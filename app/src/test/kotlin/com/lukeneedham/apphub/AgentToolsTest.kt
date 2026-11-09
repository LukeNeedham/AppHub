package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.AgentTools
import com.lukeneedham.apphub.domain.FileChange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {

    private val host = FakeCodeHost(files = mutableMapOf("README.md" to "Hello\nHello", "app/Main.kt" to "fun main() {}"))
    private val tools = AgentTools(host, "LukeNeedham/FlagTutor", "main")

    @Test
    fun theModelReadsItsOwnStagedEdits() = runTest {
        tools.execute(toolCall("1", "write_file", "path" to "app/New.kt", "content" to "class New"))
        tools.execute(toolCall("2", "replace_in_file", "path" to "app/Main.kt", "old_text" to "main()", "new_text" to "start()"))

        assertEquals("class New", tools.execute(toolCall("3", "read_file", "path" to "app/New.kt")).content)
        assertEquals("fun start() {}", tools.execute(toolCall("4", "read_file", "path" to "app/Main.kt")).content)
        assertEquals(2, tools.changes.size)
        // Nothing reached the repo
        assertEquals("fun main() {}", host.files["app/Main.kt"])
    }

    @Test
    fun replacingTextThatIsNotThereOrIsThereTwiceIsAnError() = runTest {
        val missing = tools.execute(toolCall("1", "replace_in_file", "path" to "README.md", "old_text" to "Bye", "new_text" to "x"))
        val twice = tools.execute(toolCall("2", "replace_in_file", "path" to "README.md", "old_text" to "Hello", "new_text" to "x"))

        assertTrue(missing.isError)
        assertTrue(twice.isError)
        assertFalse(tools.hasChanges)
    }

    @Test
    fun workflowsAndGitAndPathsOutsideTheRepoAreOffLimits() = runTest {
        listOf(".github/workflows/ci.yml", ".git/config", "../etc/passwd", "a/../b").forEach { path ->
            assertTrue(path, tools.execute(toolCall("w", "write_file", "path" to path, "content" to "x")).isError)
        }
        assertFalse(tools.hasChanges)
    }

    @Test
    fun deletingAFileIsStagedAsADelete() = runTest {
        tools.execute(toolCall("1", "delete_file", "path" to "README.md"))

        assertEquals(listOf<FileChange>(FileChange.Delete("README.md")), tools.changes)
        assertTrue(tools.execute(toolCall("2", "read_file", "path" to "README.md")).isError)
    }

    @Test
    fun submitNeedsChangesAndKeepsTheMessages() = runTest {
        assertTrue(tools.execute(toolCall("1", "submit", "commit_message" to "Do it")).isError)
        assertNull(tools.submission)

        tools.execute(toolCall("2", "write_file", "path" to "A.kt", "content" to "x"))
        val outcome = tools.execute(toolCall("3", "submit", "commit_message" to "Do it", "pull_request_title" to "Title", "pull_request_body" to "Body"))

        assertFalse(outcome.isError)
        assertEquals("Do it", tools.submission?.commitMessage)
        assertEquals("Title", tools.submission?.pullRequestTitle)
        assertEquals("Body", tools.submission?.pullRequestBody)
    }

    @Test
    fun aMissingArgumentIsAnErrorForTheModelNotACrash() = runTest {
        val outcome = tools.execute(toolCall("1", "read_file"))

        assertTrue(outcome.isError)
        assertNotNull(outcome.content)
    }

    @Test
    fun listingShowsDirectoriesFirst() = runTest {
        val outcome = tools.execute(toolCall("1", "list_files", "path" to ""))

        assertEquals("app/\nREADME.md", outcome.content)
    }
}
