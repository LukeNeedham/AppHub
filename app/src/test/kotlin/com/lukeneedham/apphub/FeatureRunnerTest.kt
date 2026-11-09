package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.CiStatus
import com.lukeneedham.apphub.domain.AgentPrompts
import com.lukeneedham.apphub.domain.AgentTools
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureKind
import com.lukeneedham.apphub.domain.FeatureRunner
import com.lukeneedham.apphub.domain.FeatureStatus
import com.lukeneedham.apphub.domain.FileChange
import com.lukeneedham.apphub.domain.LlmMessage
import com.lukeneedham.apphub.domain.LlmUsage
import com.lukeneedham.apphub.domain.PullRequestInfo
import com.lukeneedham.apphub.domain.find
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRunnerTest {

    private val repo = "LukeNeedham/FlagTutor"

    private fun feature(kind: FeatureKind = FeatureKind.Feature, prNumber: Int? = null) = Feature(
        id = "abc123",
        kind = kind,
        repo = repo,
        prompt = "Add a hint button",
        summary = "Add a hint button",
        status = FeatureStatus.Working,
        branch = if (kind == FeatureKind.Feature) "feature/add-a-hint-button-abc123" else null,
        prNumber = prNumber,
        createdAtMillis = 0,
    )

    private val submit = toolCall(
        "s", "submit",
        "commit_message" to "Add a hint button",
        "pull_request_title" to "Add a hint button",
        "pull_request_body" to "Adds a hint button.",
    )

    private fun runner(store: InMemoryFeatureStore, llm: ScriptedLlm?, host: FakeCodeHost) =
        FeatureRunner(FixedLlmSource(llm), host, store, ciPollMillis = 10, noCiGraceMillis = 50, ciTimeoutMillis = 10_000)

    @Test
    fun aFeatureGoesFromPromptToGreenCiAndTheModelIsNotCalledAgain() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val host = FakeCodeHost(files = mutableMapOf("CLAUDE.md" to "rules"), ci = listOf(CiStatus.Running, CiStatus.Passed))
        val llm = ScriptedLlm(
            listOf(
                reply(toolCall("1", "read_file", "path" to "CLAUDE.md")),
                reply(toolCall("2", "write_file", "path" to "Hint.kt", "content" to "class Hint")),
                reply(submit),
            ),
        )

        runner(store, llm, host).run("abc123") { error("no question expected") }

        val done = store.find("abc123")!!
        assertEquals(FeatureStatus.ReadyForReview, done.status)
        assertEquals(7, done.prNumber)
        assertEquals(1, host.commits.size)
        assertEquals("feature/add-a-hint-button-abc123", host.commits.single().branch)
        assertEquals(listOf(FileChange.Write("Hint.kt", "class Hint")), host.commits.single().changes)
        assertEquals(listOf("Add a hint button"), host.openedPullRequests)
        // One call per step, and none while waiting for the CI or after it was green
        assertEquals(3, llm.requests.size)
        // The prompt is the first message, and the instructions are not in the conversation
        assertEquals(LlmMessage.User("Add a hint button"), llm.requests.first().messages.single())
        assertTrue(llm.requests.first().system.contains("FlagTutor"))
    }

    @Test
    fun theTokensUsedAreAddedUpAndNotedWhenTheSessionEnds() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val write = reply(toolCall("1", "write_file", "path" to "A.kt", "content" to "x"))
        val llm = ScriptedLlm(
            listOf(
                write.copy(usage = LlmUsage(inputTokens = 1_000, cachedInputTokens = 0, outputTokens = 50)),
                reply(submit).copy(usage = LlmUsage(inputTokens = 1_200, cachedInputTokens = 1_000, outputTokens = 20)),
            ),
        )

        runner(store, llm, FakeCodeHost()).run("abc123") { error("no question expected") }

        assertEquals("Tokens used: 2200 in (1000 from the cache), 70 out.", store.find("abc123")!!.log.last())
    }

    @Test
    fun theTitleFromTheModelReplacesThePromptLineAtOnceAndStaysAfterThePullRequestOpens() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val llm = ScriptedLlm(
            listOf(
                reply(toolCall("t", "set_title", "title" to "Add a hint button to the quiz")),
                reply(toolCall("q", "ask_user", "question" to "Cost a point?")),
                reply(toolCall("1", "write_file", "path" to "A.kt", "content" to "x")),
                reply(submit),
            ),
        )
        var titleWhenAsking: String? = null

        runner(store, llm, FakeCodeHost()).run("abc123") {
            titleWhenAsking = store.find("abc123")!!.summary
            "No"
        }

        // Already the title when the second call (the question) was reached, before any work was done
        assertEquals("Add a hint button to the quiz", titleWhenAsking)
        // The pull request title ("Add a hint button") does not replace it
        assertEquals("Add a hint button to the quiz", store.find("abc123")!!.summary)
    }

    @Test
    fun anEmptyTitleIsAnErrorForTheModelAndKeepsTheOldOne() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val llm = ScriptedLlm(
            listOf(
                reply(toolCall("t", "set_title", "title" to "  ")),
                reply(toolCall("1", "write_file", "path" to "A.kt", "content" to "x")),
                reply(submit),
            ),
        )

        runner(store, llm, FakeCodeHost()).run("abc123") { error("no question expected") }

        assertEquals("Add a hint button", store.find("abc123")!!.summary)
        assertTrue(llm.requests[1].messages.filterIsInstance<LlmMessage.ToolResult>().single().isError)
    }

    @Test
    fun bothPromptsMakeSettingTheTitleTheFirstStep() {
        listOf(AgentPrompts.feature("FlagTutor"), AgentPrompts.tweak("FlagTutor", "https://x/pull/1")).forEach { prompt ->
            assertTrue(prompt.contains("very first step"))
            assertTrue(prompt.contains("set_title"))
        }
        assertTrue(AgentTools.specs.any { it.name == "set_title" })
    }

    @Test
    fun aRedCiGivesTheFailureToTheModelWhichFixesItOnTheSameBranch() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val host = FakeCodeHost(ci = listOf(CiStatus.Failed("e: Hint.kt:1 Unresolved reference: Foo"), CiStatus.Passed))
        val llm = ScriptedLlm(
            listOf(
                reply(toolCall("1", "write_file", "path" to "Hint.kt", "content" to "Foo()")),
                reply(submit),
                reply(toolCall("3", "write_file", "path" to "Hint.kt", "content" to "class Hint")),
                reply(submit),
            ),
        )

        runner(store, llm, host).run("abc123") { error("no question expected") }

        assertEquals(FeatureStatus.ReadyForReview, store.find("abc123")!!.status)
        assertEquals(2, host.commits.size)
        assertEquals(setOf("feature/add-a-hint-button-abc123"), host.commits.map { it.branch }.toSet())
        // One pull request only
        assertEquals(1, host.openedPullRequests.size)
        val failureMessage = llm.requests[2].messages.filterIsInstance<LlmMessage.User>().last().text
        assertTrue(failureMessage.contains("Unresolved reference: Foo"))
        assertEquals(4, llm.requests.size)
    }

    @Test
    fun aCiThatKeepsFailingEndsTheSessionAsFailed() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val host = FakeCodeHost(ci = listOf(CiStatus.Failed("boom")))
        val rounds = (1..8).flatMap { listOf(reply(toolCall("w$it", "write_file", "path" to "A.kt", "content" to "$it")), reply(submit)) }
        val llm = ScriptedLlm(rounds)

        FeatureRunner(FixedLlmSource(llm), host, store, ciPollMillis = 10, maxCiFixes = 2).run("abc123") { error("no question") }

        val failed = store.find("abc123")!!
        assertEquals(FeatureStatus.Failed, failed.status)
        assertTrue(failed.error.orEmpty().contains("boom"))
        assertEquals(3, host.commits.size)
    }

    @Test
    fun aQuestionWaitsForTheAnswerAndTheSessionCarriesOn() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val host = FakeCodeHost()
        val llm = ScriptedLlm(
            listOf(
                reply(toolCall("1", "ask_user", "question" to "Should a hint cost a point?")),
                reply(toolCall("2", "write_file", "path" to "Hint.kt", "content" to "class Hint")),
                reply(submit),
            ),
        )
        val asked = mutableListOf<String>()
        val statusWhileAsking = mutableListOf<FeatureStatus>()

        runner(store, llm, host).run("abc123") { question ->
            asked += question
            statusWhileAsking += store.find("abc123")!!.status
            "Yes"
        }

        assertEquals(listOf("Should a hint cost a point?"), asked)
        assertEquals(listOf(FeatureStatus.NeedsAnswer), statusWhileAsking)
        assertEquals(FeatureStatus.ReadyForReview, store.find("abc123")!!.status)
        assertNull(store.find("abc123")!!.question)
        val toolResult = llm.requests[1].messages.filterIsInstance<LlmMessage.ToolResult>().single()
        assertEquals("Yes", toolResult.content)
    }

    @Test
    fun aModelThatTalksWithoutChangingAnythingIsAskingTheUser() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val llm = ScriptedLlm(
            listOf(
                reply(text = "Which screen should have the button?"),
                reply(toolCall("1", "write_file", "path" to "Hint.kt", "content" to "class Hint")),
                reply(submit),
            ),
        )
        val asked = mutableListOf<String>()

        runner(store, llm, FakeCodeHost()).run("abc123") { question ->
            asked += question
            "The quiz"
        }

        assertEquals(listOf("Which screen should have the button?"), asked)
        assertEquals(FeatureStatus.ReadyForReview, store.find("abc123")!!.status)
    }

    @Test
    fun aTweakWorksOnTheBranchOfItsPullRequestAndOpensNoNewOne() = runTest {
        val pr = PullRequestInfo(12, "Add the quiz", "https://github.com/$repo/pull/12", "feature/quiz", isOpen = true)
        val store = InMemoryFeatureStore().also { it.add(feature(FeatureKind.Tweak, prNumber = 12)) }
        val host = FakeCodeHost(tweakedPullRequest = pr)
        val llm = ScriptedLlm(
            listOf(
                reply(toolCall("1", "write_file", "path" to "Quiz.kt", "content" to "rounder")),
                reply(toolCall("2", "submit", "commit_message" to "Round the buttons")),
            ),
        )

        runner(store, llm, host).run("abc123") { error("no question expected") }

        assertEquals(FeatureStatus.ReadyForReview, store.find("abc123")!!.status)
        assertEquals(listOf("feature/quiz"), host.commits.map { it.branch })
        assertTrue(host.openedPullRequests.isEmpty())
        val system = llm.requests.first().system
        assertTrue(
            system.startsWith(
                "You are working on an existing PR for FlagTutor: https://github.com/$repo/pull/12. Review the user's requested " +
                    "adjustments below, make the necessary modifications to the codebase, push the changes to this same PR branch, " +
                    "monitor the CI until it goes green, and then immediately archive/kill this session.",
            ),
        )
    }

    @Test
    fun aTweakOfAClosedPullRequestFails() = runTest {
        val pr = PullRequestInfo(12, "Add the quiz", "https://github.com/$repo/pull/12", "feature/quiz", isOpen = false)
        val store = InMemoryFeatureStore().also { it.add(feature(FeatureKind.Tweak, prNumber = 12)) }
        val llm = ScriptedLlm(emptyList())

        runner(store, llm, FakeCodeHost(tweakedPullRequest = pr)).run("abc123") { error("no question expected") }

        assertEquals(FeatureStatus.Failed, store.find("abc123")!!.status)
        assertTrue(llm.requests.isEmpty())
    }

    @Test
    fun aTokenThatMayNotWriteFailsTheSessionBeforeTheModelIsCalled() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val llm = ScriptedLlm(emptyList())
        val host = FakeCodeHost(branchError = java.io.IOException("GitHub refused (403) at .../git/refs. That needs the permission Contents (write)"))

        runner(store, llm, host).run("abc123") { error("no question expected") }

        val failed = store.find("abc123")!!
        assertEquals(FeatureStatus.Failed, failed.status)
        assertTrue(failed.error.orEmpty().contains("Contents (write)"))
        // No tokens were spent
        assertTrue(llm.requests.isEmpty())
    }

    @Test
    fun withoutAnApiKeyTheSessionFailsAtOnce() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }

        runner(store, null, FakeCodeHost()).run("abc123") { error("no question expected") }

        val failed = store.find("abc123")!!
        assertEquals(FeatureStatus.Failed, failed.status)
        assertTrue(failed.error.orEmpty().contains("Settings"))
    }

    @Test
    fun aCommitWithNoCheckAtAllCountsAsGreenAfterTheGrace() = runTest {
        val store = InMemoryFeatureStore().also { it.add(feature()) }
        val host = FakeCodeHost(ci = listOf(CiStatus.NotStarted))
        val llm = ScriptedLlm(listOf(reply(toolCall("1", "write_file", "path" to "A.kt", "content" to "x")), reply(submit)))

        runner(store, llm, host).run("abc123") { error("no question expected") }

        assertEquals(FeatureStatus.ReadyForReview, store.find("abc123")!!.status)
        assertNotNull(store.find("abc123")!!.prNumber)
    }
}
