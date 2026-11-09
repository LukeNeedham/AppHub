package com.lukeneedham.apphub.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What the model hands in when its work is done. */
data class Submission(val commitMessage: String, val pullRequestTitle: String, val pullRequestBody: String)

/** What a tool answers to the model. */
data class ToolOutcome(val content: String, val isError: Boolean = false)

/**
 * The tools of one working phase of a session: the model reads the repo at [ref] and stages changes, which stay here
 * until the runner commits them. Reads see the staged changes, so the model sees its own edits. A fresh instance is made for
 * each phase, so every phase reads the branch as it is then (the CI adds commits of its own to it).
 */
class AgentTools(
    private val host: CodeHost,
    private val repo: String,
    private val ref: String,
) {
    private val staged = linkedMapOf<String, FileChange>()

    var submission: Submission? = null
        private set

    val changes: List<FileChange> get() = staged.values.toList()

    val hasChanges: Boolean get() = staged.isNotEmpty()

    /** Runs [call], which is not [askUserTool]: that one needs the user and is the runner's. Never throws on a bad call: the model reads the error. */
    suspend fun execute(call: LlmToolCall): ToolOutcome =
        try {
            when (call.name) {
                listFilesTool -> listFiles(call.arguments.text("path").orEmpty())
                readFileTool -> readFile(call.arguments.required("path"))
                writeFileTool -> writeFile(call.arguments.required("path"), call.arguments.required("content"))
                replaceInFileTool -> replaceInFile(
                    call.arguments.required("path"),
                    call.arguments.required("old_text"),
                    call.arguments.required("new_text"),
                )
                deleteFileTool -> deleteFile(call.arguments.required("path"))
                submitTool -> submit(call.arguments)
                else -> ToolOutcome("There is no tool called ${call.name}.", isError = true)
            }
        } catch (e: ToolArgumentException) {
            ToolOutcome(e.message.orEmpty(), isError = true)
        }

    private suspend fun listFiles(rawPath: String): ToolOutcome {
        val path = cleanPath(rawPath, allowRoot = true) ?: return badPath(rawPath)
        val stagedPaths = staged.keys.filter { path.isEmpty() || it.startsWith("$path/") }
        val entries = host.listDirectory(repo, ref, path)
        if (entries.isEmpty() && stagedPaths.isEmpty()) return ToolOutcome("No such directory, or it is empty: '$path'.", isError = true)
        val lines = entries.sortedWith(compareBy({ !it.isDirectory }, { it.name })).map { if (it.isDirectory) "${it.name}/" else it.name }
        val newFiles = stagedPaths.map { "(new or changed, not yet pushed) $it" }
        return ToolOutcome((lines + newFiles).joinToString("\n"))
    }

    private suspend fun readFile(rawPath: String): ToolOutcome {
        val path = cleanPath(rawPath) ?: return badPath(rawPath)
        val text = currentText(path) ?: return ToolOutcome("No text file at '$path'.", isError = true)
        if (text.length <= maxFileChars) return ToolOutcome(text)
        return ToolOutcome(text.take(maxFileChars) + "\n[... cut: the file has ${text.length} characters, this is the first $maxFileChars]")
    }

    private suspend fun writeFile(rawPath: String, content: String): ToolOutcome {
        val path = writablePath(rawPath) ?: return badPath(rawPath)
        staged[path] = FileChange.Write(path, content)
        return ToolOutcome("Wrote $path (${content.length} characters). It is pushed when you call submit.")
    }

    private suspend fun replaceInFile(rawPath: String, oldText: String, newText: String): ToolOutcome {
        val path = writablePath(rawPath) ?: return badPath(rawPath)
        val text = currentText(path) ?: return ToolOutcome("No text file at '$path'.", isError = true)
        if (oldText.isEmpty()) return ToolOutcome("old_text is empty.", isError = true)
        val first = text.indexOf(oldText)
        if (first < 0) return ToolOutcome("old_text is not in $path. Read the file again: it must match exactly, whitespace included.", isError = true)
        if (text.indexOf(oldText, first + 1) >= 0) {
            return ToolOutcome("old_text is in $path more than once. Include more of the lines around it.", isError = true)
        }
        staged[path] = FileChange.Write(path, text.replaceRange(first, first + oldText.length, newText))
        return ToolOutcome("Changed $path. It is pushed when you call submit.")
    }

    private fun deleteFile(rawPath: String): ToolOutcome {
        val path = writablePath(rawPath) ?: return badPath(rawPath)
        staged[path] = FileChange.Delete(path)
        return ToolOutcome("Deleted $path. It is pushed when you call submit.")
    }

    private fun submit(arguments: JsonObject): ToolOutcome {
        if (staged.isEmpty()) return ToolOutcome("Nothing is changed yet. Write the changes first, then submit.", isError = true)
        val message = arguments.required("commit_message").trim()
        val title = arguments.text("pull_request_title")?.trim().orEmpty()
        val body = arguments.text("pull_request_body")?.trim().orEmpty()
        if (message.isEmpty()) return ToolOutcome("commit_message is empty.", isError = true)
        submission = Submission(message, title.ifEmpty { message.lineSequence().first() }, body)
        return ToolOutcome("Submitted ${staged.size} changed file(s). They are being pushed and checked by the CI. If it fails you will be told.")
    }

    /** The text of [path] with the staged changes applied, or null when there is none. */
    private suspend fun currentText(path: String): String? =
        when (val change = staged[path]) {
            is FileChange.Write -> change.content
            is FileChange.Delete -> null
            null -> host.readFile(repo, ref, path)
        }

    private fun badPath(path: String) = ToolOutcome(
        "'$path' is not a path you may use. Paths are relative to the repo root, with no '..', and nothing under .git or " +
            ".github/workflows (the workflows are kept in sync from ci-workflows).",
        isError = true,
    )

    private fun writablePath(raw: String): String? =
        cleanPath(raw)?.takeIf { path -> protectedPrefixes.none { path == it || path.startsWith("$it/") } }

    private fun cleanPath(raw: String, allowRoot: Boolean = false): String? {
        val path = raw.trim().trim('/')
        if (path.isEmpty()) return if (allowRoot) "" else null
        if (path.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
        if (path == ".git" || path.startsWith(".git/")) return null
        return path
    }

    companion object {
        const val listFilesTool = "list_files"
        const val readFileTool = "read_file"
        const val writeFileTool = "write_file"
        const val replaceInFileTool = "replace_in_file"
        const val deleteFileTool = "delete_file"
        const val askUserTool = "ask_user"
        const val submitTool = "submit"

        private const val maxFileChars = 40_000
        private val protectedPrefixes = listOf(".github/workflows")

        val specs: List<LlmTool> = listOf(
            LlmTool(
                listFilesTool,
                "Lists the files and directories in a directory of the repo. Directories end with '/'. Use '' for the repo root.",
                schema("path" to "Directory, relative to the repo root"),
            ),
            LlmTool(readFileTool, "Reads a text file of the repo.", schema("path" to "File, relative to the repo root")),
            LlmTool(
                writeFileTool,
                "Creates a file or replaces all of it. For a small change to an existing file use replace_in_file instead.",
                schema("path" to "File, relative to the repo root", "content" to "The whole new content of the file"),
            ),
            LlmTool(
                replaceInFileTool,
                "Replaces one exact piece of text of a file by another. old_text must occur exactly once.",
                schema(
                    "path" to "File, relative to the repo root",
                    "old_text" to "The text to replace, exactly as it is in the file",
                    "new_text" to "What to put there",
                ),
            ),
            LlmTool(deleteFileTool, "Deletes a file.", schema("path" to "File, relative to the repo root")),
            LlmTool(
                askUserTool,
                "Asks the user a question and waits for the answer. The user has no other way to talk to you, and does not expect to: " +
                    "use it only when you are blocked and cannot choose a sensible default yourself.",
                schema("question" to "A short, specific question"),
            ),
            LlmTool(
                submitTool,
                "Call this once, when the work is complete and every change is written. It pushes the changes, opens or updates the " +
                    "pull request and watches the CI. If the CI fails you are given the failure and call submit again after fixing it.",
                schema(
                    "commit_message" to "One-line summary of the change, imperative mood",
                    "pull_request_title" to "Title of the pull request (used the first time only)",
                    "pull_request_body" to "Description of the pull request: what changed and why, in Markdown (used the first time only)",
                    required = listOf("commit_message"),
                ),
            ),
        )

        private fun schema(vararg properties: Pair<String, String>, required: List<String> = properties.map { it.first }) = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                properties.forEach { (name, description) ->
                    putJsonObject(name) {
                        put("type", "string")
                        put("description", description)
                    }
                }
            }
            putJsonArray("required") { required.forEach { add(it) } }
        }
    }
}

/** A tool call that lacks an argument. */
private class ToolArgumentException(message: String) : Exception(message)

internal fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.required(name: String): String =
    text(name) ?: throw ToolArgumentException("The argument '$name' is missing.")
