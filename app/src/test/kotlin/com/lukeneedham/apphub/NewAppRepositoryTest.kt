package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.NewAppApi
import com.lukeneedham.apphub.data.NewAppRepository
import com.lukeneedham.apphub.data.WorkflowRunDto
import com.lukeneedham.apphub.domain.NewAppProgress
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class NewAppRepositoryTest {

    /** Answers [runs] with what the next call of the script says; the last answer repeats. */
    private class FakeApi(private val answers: List<List<WorkflowRunDto>>, private val dispatchError: Exception? = null) : NewAppApi {
        var runsCalls = 0
        val dispatched = mutableListOf<Pair<String, Boolean>>()

        override suspend fun dispatch(name: String, isPrivate: Boolean) {
            dispatchError?.let { throw it }
            dispatched += name to isPrivate
        }

        override suspend fun runs(): List<WorkflowRunDto> = answers[minOf(runsCalls++, answers.lastIndex)]
    }

    private fun workflowRun(id: Long, status: String, conclusion: String? = null, title: String = "New App LyricTutor") =
        WorkflowRunDto(id, status, conclusion, "https://github.com/LukeNeedham/ci-workflows/actions/runs/$id", title)

    private fun repository(api: NewAppApi) = NewAppRepository(api, pollMillis = 1, findAttempts = 3, runAttempts = 5)

    @Test
    fun `it starts the workflow, finds its run and follows it to the end`() = runTest {
        val api = FakeApi(
            listOf(
                emptyList(), // before the start
                emptyList(), // not listed yet
                listOf(workflowRun(7, "queued")),
                listOf(workflowRun(7, "in_progress")),
                listOf(workflowRun(7, "completed", "success")),
            ),
        )

        val progress = repository(api).create("LyricTutor", isPrivate = true).toList()

        assertEquals(listOf("LyricTutor" to true), api.dispatched)
        val url = "https://github.com/LukeNeedham/ci-workflows/actions/runs/7"
        assertEquals(
            listOf(
                NewAppProgress.Starting,
                NewAppProgress.Waiting,
                NewAppProgress.Running(url),
                NewAppProgress.Done("LukeNeedham/LyricTutor", url),
            ),
            progress,
        )
    }

    @Test
    fun `a run that did not succeed is reported as failed with its link`() = runTest {
        val api = FakeApi(listOf(emptyList(), listOf(workflowRun(8, "completed", "failure"))))

        val progress = repository(api).create("LyricTutor", isPrivate = false).toList()

        assertEquals(NewAppProgress.Failed("https://github.com/LukeNeedham/ci-workflows/actions/runs/8"), progress.last())
    }

    @Test
    fun `a cancelled run is a failure too`() = runTest {
        val api = FakeApi(listOf(emptyList(), listOf(workflowRun(8, "completed", "cancelled"))))

        assertTrue(repository(api).create("LyricTutor", false).toList().last() is NewAppProgress.Failed)
    }

    @Test
    fun `an earlier run of the same app is not taken for the new one`() = runTest {
        val old = workflowRun(3, "completed", "failure")
        val api = FakeApi(
            listOf(
                listOf(old), // before the start: the first try
                listOf(old), // the new run is not listed yet
                listOf(workflowRun(9, "in_progress"), old),
                listOf(workflowRun(9, "completed", "success"), old),
            ),
        )

        val progress = repository(api).create("LyricTutor", false).toList()

        assertEquals(NewAppProgress.Running("https://github.com/LukeNeedham/ci-workflows/actions/runs/9"), progress[2])
        assertTrue(progress.last() is NewAppProgress.Done)
    }

    @Test
    fun `a run of another app is not taken either`() = runTest {
        val other = workflowRun(5, "in_progress", title = "New App Other")
        val api = FakeApi(listOf(listOf(other), listOf(other), listOf(workflowRun(6, "completed", "success"), other)))

        val progress = repository(api).create("LyricTutor", false).toList()

        assertEquals(NewAppProgress.Running("https://github.com/LukeNeedham/ci-workflows/actions/runs/6"), progress[2])
    }

    @Test
    fun `it gives up when the run never appears`() = runTest {
        val api = FakeApi(listOf(emptyList()))

        try {
            repository(api).create("LyricTutor", false).toList()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("did not start"))
        }
    }

    @Test
    fun `it gives up on a run that never ends`() = runTest {
        val api = FakeApi(listOf(emptyList(), listOf(workflowRun(7, "in_progress"))))

        try {
            repository(api).create("LyricTutor", false).toList()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("taking too long"))
        }
    }

    @Test
    fun `a refused start is thrown to the caller and nothing is followed`() = runTest {
        val api = FakeApi(listOf(emptyList()), dispatchError = IOException("GitHub returned 422"))

        try {
            repository(api).create("LyricTutor", false).toList()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertEquals("GitHub returned 422", e.message)
        }
        assertEquals(1, api.runsCalls) // only the look before the start
    }
}
