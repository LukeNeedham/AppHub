package com.lukeneedham.apphub.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * One session: takes a prompt to a pull request whose CI is green, on its own.
 *
 * The model works in phases. In a phase it reads the repo and stages changes with [AgentTools] until it calls `submit`. The
 * runner then commits and pushes (and opens the pull request, the first time), and waits for the CI **without the model**:
 * the model is called again only if the CI fails, with the failure report. When the CI is green the runner clears the conversation
 * and returns: the session is over, nothing polls and nothing calls the model any more.
 *
 * The user is asked only through [run]'s `askUser`, when the model asks a question.
 */
class FeatureRunner(
    private val llm: LlmSource,
    private val host: CodeHost,
    private val store: FeatureStore,
    private val ciPollMillis: Long = 20_000,
    /** How long a commit may have no check at all before it counts as having no CI. */
    private val noCiGraceMillis: Long = 180_000,
    private val ciTimeoutMillis: Long = 45 * 60_000L,
    private val maxTurnsPerPhase: Int = 80,
    private val maxCiFixes: Int = 5,
    /** A guard against a conversation that grows without end. About 150,000 tokens. */
    private val maxContextChars: Int = 600_000,
) {

    /** Runs the session of the stored feature [id] to its end, and records every step in the store. */
    suspend fun run(id: String, askUser: suspend (question: String) -> String) {
        val feature = store.find(id) ?: return
        val spent = TokenCount()
        try {
            execute(feature, spent, askUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FeatureFailure) {
            fail(id, e.message.orEmpty())
        } catch (e: LlmException) {
            fail(id, e.message.orEmpty())
        } catch (e: Exception) {
            fail(id, e.message ?: "Something went wrong (${e::class.simpleName}).")
        } finally {
            // However it ended, say what the model cost: the user pays per token
            spent.total?.let { note(id, "Tokens used: ${it.inputTokens} in (${it.cachedInputTokens} from the cache), ${it.outputTokens} out.") }
        }
    }

    private suspend fun execute(start: Feature, spent: TokenCount, askUser: suspend (String) -> String) {
        val id = start.id
        val repo = start.repo
        val configured = llm.current() ?: throw FeatureFailure("Set up an LLM in Settings first: its API key, and for a custom provider its address and model.")
        val appName = repo.substringAfter('/')

        val branch: String
        val system: String
        var readRef: String
        if (start.isTweak) {
            val number = start.prNumber ?: throw FeatureFailure("This tweak has no pull request.")
            val pr = host.pullRequest(repo, number) ?: throw FeatureFailure("Pull request #$number was not found.")
            if (!pr.isOpen) throw FeatureFailure("Pull request #$number is not open any more.")
            branch = pr.branch
            readRef = branch
            system = AgentPrompts.tweak(appName, pr.url)
            update(id) { it.copy(branch = branch) }
        } else {
            branch = start.branch ?: throw FeatureFailure("This feature has no branch.")
            readRef = host.defaultBranch(repo)
            system = AgentPrompts.feature(appName)
            // Before the model is called: a token that cannot write must fail here, not after the work
            host.prepareBranch(repo, branch)
        }
        note(id, "Started with ${configured.providerLabel} (${configured.model}).")

        val messages = mutableListOf<LlmMessage>(LlmMessage.User(start.prompt))
        var prNumber = start.prNumber
        var fixes = 0
        var working = FeatureStatus.Working

        while (true) {
            update(id) { it.copy(status = working) }
            val tools = AgentTools(host, repo, readRef)
            val submission = work(id, configured, system, messages, tools, working, spent, askUser)

            update(id) { it.copy(status = FeatureStatus.WaitingForCi) }
            val sha = host.commit(repo, branch, submission.commitMessage, tools.changes)
            note(id, "Pushed ${tools.changes.size} changed file(s) to $branch: ${submission.commitMessage}")
            readRef = branch

            if (prNumber == null) {
                val opened = host.openPullRequest(repo, branch, submission.pullRequestTitle, pullRequestBody(start.prompt, submission))
                prNumber = opened.number
                update(id) { it.copy(prNumber = opened.number, prUrl = opened.url) }
                note(id, "Opened pull request #${opened.number}.")
            }

            when (val ci = waitForCi(id, repo, sha)) {
                is CiStatus.Failed -> {
                    if (++fixes > maxCiFixes) throw FeatureFailure("The CI still fails after $maxCiFixes fixes. The last failure:\n${ci.report.take(1_500)}")
                    note(id, "The CI failed. Fixing it (attempt $fixes of $maxCiFixes).")
                    messages += LlmMessage.User(AgentPrompts.ciFailed(fixes, maxCiFixes, ci.report))
                    working = FeatureStatus.FixingCi
                }
                else -> {
                    // Green: the session ends here. Nothing is kept of the conversation, and the model is not called again
                    messages.clear()
                    note(id, "The CI is green. Session closed.")
                    update(id) { it.copy(status = FeatureStatus.ReadyForReview, question = null, error = null) }
                    return
                }
            }
        }
    }

    /** Lets the model work until it submits. Throws [FeatureFailure] when it cannot. */
    private suspend fun work(
        id: String,
        configured: ConfiguredLlm,
        system: String,
        messages: MutableList<LlmMessage>,
        tools: AgentTools,
        working: FeatureStatus,
        spent: TokenCount,
        askUser: suspend (String) -> String,
    ): Submission {
        var nudges = 0
        val specs = AgentTools.specs
        for (turn in 1..maxTurnsPerPhase) {
            if (contextSize(messages) > maxContextChars) throw FeatureFailure("The conversation grew too long for one session.")
            val reply = configured.client.complete(LlmRequest(configured.model, system, messages.toList(), specs))
            reply.usage?.let(spent::add)
            val text = reply.text?.takeIf { it.isNotBlank() }
            messages += LlmMessage.Assistant(text, reply.toolCalls)

            if (reply.toolCalls.isEmpty()) {
                when {
                    // No change yet and no tool: the model is speaking to the user, so it is a question
                    !tools.hasChanges -> messages += LlmMessage.User(ask(id, text ?: "The model needs more information.", working, askUser))
                    nudges < maxNudges -> {
                        nudges++
                        messages += LlmMessage.User(AgentPrompts.nudgeToSubmit)
                    }
                    else -> throw FeatureFailure("The model stopped without submitting its changes.")
                }
                continue
            }

            for (call in reply.toolCalls) {
                val outcome = if (call.name == AgentTools.askUserTool) {
                    val question = call.arguments.text("question") ?: "The model needs more information."
                    ToolOutcome(ask(id, question, working, askUser))
                } else if (call.name == AgentTools.setTitleTool) {
                    setTitle(id, call.arguments.text("title"))
                } else {
                    note(id, describe(call))
                    tools.execute(call)
                }
                messages += LlmMessage.ToolResult(call.id, outcome.content, outcome.isError)
            }
            tools.submission?.let { return it }
        }
        throw FeatureFailure("The model did not finish within $maxTurnsPerPhase steps.")
    }

    /** The title the model gave to the work replaces the line made from the prompt, and shows in the app as soon as it is here. */
    private fun setTitle(id: String, title: String?): ToolOutcome {
        val clean = title?.let(::summarize).orEmpty()
        if (clean.isEmpty()) return ToolOutcome("The title is empty. Call set_title with a short summary of the request.", isError = true)
        update(id) { it.copy(summary = clean) }
        note(id, "Title: $clean")
        return ToolOutcome("The title is set. Now carry on with the work.")
    }

    private suspend fun ask(id: String, question: String, working: FeatureStatus, askUser: suspend (String) -> String): String {
        update(id) { it.copy(status = FeatureStatus.NeedsAnswer, question = question) }
        val answer = askUser(question)
        note(id, "You answered.")
        update(id) { it.copy(status = working, question = null) }
        return answer
    }

    /** Waits for the checks of [sha] to end. Returns [CiStatus.Passed] or [CiStatus.Failed]. */
    private suspend fun waitForCi(id: String, repo: String, sha: String): CiStatus {
        var waited = 0L
        var withoutChecks = 0L
        var failedReads = 0
        var passedReads = 0
        while (true) {
            val status = try {
                host.ciStatus(repo, sha).also { failedReads = 0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // A dropped connection must not end a session that is nearly done
                if (++failedReads > maxFailedReads) throw e
                CiStatus.Running
            }
            passedReads = if (status is CiStatus.Passed) passedReads + 1 else 0
            when (status) {
                is CiStatus.Failed -> return status
                // Twice in a row: a workflow that starts a moment after the others must not be missed
                is CiStatus.Passed -> if (passedReads >= passesNeeded) return status
                CiStatus.NotStarted -> {
                    withoutChecks += ciPollMillis
                    if (withoutChecks >= noCiGraceMillis) {
                        note(id, "No check started on the commit: counting it as green.")
                        return CiStatus.Passed
                    }
                }
                CiStatus.Running -> withoutChecks = 0
            }
            waited += ciPollMillis
            if (waited >= ciTimeoutMillis) throw FeatureFailure("The CI did not finish in time. Look at it on GitHub.")
            delay(ciPollMillis)
        }
    }

    private fun pullRequestBody(prompt: String, submission: Submission): String {
        val quoted = prompt.trim().lines().joinToString("\n") { "> $it" }
        return (submission.pullRequestBody.ifBlank { "Made by AppHub." }) + "\n\n---\nStarted from AppHub with this prompt:\n\n$quoted"
    }

    private fun describe(call: LlmToolCall): String =
        when (call.name) {
            AgentTools.listFilesTool -> "Listed ${call.arguments.text("path").orEmpty().ifEmpty { "the repo root" }}"
            AgentTools.readFileTool -> "Read ${call.arguments.text("path")}"
            AgentTools.writeFileTool -> "Wrote ${call.arguments.text("path")}"
            AgentTools.replaceInFileTool -> "Changed ${call.arguments.text("path")}"
            AgentTools.deleteFileTool -> "Deleted ${call.arguments.text("path")}"
            AgentTools.setTitleTool -> "Set the title"
            AgentTools.submitTool -> "Submitted the changes"
            else -> call.name
        }

    private fun contextSize(messages: List<LlmMessage>): Int =
        messages.sumOf { message ->
            when (message) {
                is LlmMessage.User -> message.text.length
                is LlmMessage.ToolResult -> message.content.length
                is LlmMessage.Assistant -> (message.text?.length ?: 0) + message.toolCalls.sumOf { it.arguments.toString().length }
            }
        }

    private fun update(id: String, change: (Feature) -> Feature) = store.update(id, change)

    private fun note(id: String, line: String) = store.update(id) { it.copy(log = (it.log + line).takeLast(maxLogLines)) }

    private fun fail(id: String, message: String) =
        store.update(id) { it.copy(status = FeatureStatus.Failed, error = message, question = null) }

    private companion object {
        const val maxNudges = 2
        const val passesNeeded = 2
        const val maxFailedReads = 5
        const val maxLogLines = 60
    }
}

/** The tokens a session has used so far. */
private class TokenCount {
    var total: LlmUsage? = null
        private set

    fun add(usage: LlmUsage) {
        total = total?.let { it + usage } ?: usage
    }
}

/** The session cannot go on; [message] says why and is shown to the user. */
private class FeatureFailure(message: String) : Exception(message)
