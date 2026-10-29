package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.NewAppApi
import com.lukeneedham.apphub.data.NewAppRepository
import com.lukeneedham.apphub.data.WorkflowRunDto
import com.lukeneedham.apphub.domain.MissingPermissionException
import com.lukeneedham.apphub.domain.NewAppException
import com.lukeneedham.apphub.domain.NewAppProgress
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class NewAppRepositoryTest {

    /**
     * Answers [runs] with what the next call of the script says (the last answer repeats), and notes in [events] what it was
     * asked, in order.
     */
    private class FakeApi(
        private val answers: List<List<WorkflowRunDto>>,
        private val defaultBranch: String = "main",
        private val createError: Exception? = null,
        private val dispatchError: Exception? = null,
    ) : NewAppApi {
        var runsCalls = 0
        val events = mutableListOf<String>()

        override suspend fun createRepo(name: String, isPrivate: Boolean): String {
            events += "createRepo $name private=$isPrivate"
            createError?.let { throw it }
            return defaultBranch
        }

        override suspend fun renameBranch(name: String, from: String, to: String) {
            events += "renameBranch $name $from->$to"
        }

        override suspend fun dispatch(name: String) {
            events += "dispatch $name"
            dispatchError?.let { throw it }
        }

        override suspend fun runs(): List<WorkflowRunDto> {
            events += "runs"
            return answers[minOf(runsCalls++, answers.lastIndex)]
        }
    }

    private fun workflowRun(id: Long, status: String, conclusion: String? = null, title: String = "New App LyricTutor") =
        WorkflowRunDto(id, status, conclusion, "https://github.com/LukeNeedham/ci-workflows/actions/runs/$id", title)

    private fun repository(api: NewAppApi) = NewAppRepository(api, pollMillis = 1, findAttempts = 3, runAttempts = 5)

    private val happyAnswers = listOf(
        emptyList(), // before anything is made
        emptyList(), // the run is not listed yet
        listOf(workflowRun(7, "queued")),
        listOf(workflowRun(7, "in_progress")),
        listOf(workflowRun(7, "completed", "success")),
    )

    @Test
    fun `it makes the repo, starts the setup, finds its run and follows it to the end`() = runTest {
        val api = FakeApi(happyAnswers)

        val progress = repository(api).create("LyricTutor", isPrivate = true).toList()

        val url = "https://github.com/LukeNeedham/ci-workflows/actions/runs/7"
        assertEquals(
            listOf(
                NewAppProgress.CreatingRepo,
                NewAppProgress.Starting,
                NewAppProgress.Waiting,
                NewAppProgress.Running(url),
                NewAppProgress.Done("LukeNeedham/LyricTutor", url),
            ),
            progress,
        )
    }

    @Test
    fun `the workflow is looked at before the repo is made, and the repo is made before the workflow starts`() = runTest {
        val api = FakeApi(happyAnswers)

        repository(api).create("LyricTutor", isPrivate = true).toList()

        assertEquals(listOf("runs", "createRepo LyricTutor private=true", "dispatch LyricTutor"), api.events.take(3))
    }

    @Test
    fun `a token that cannot see the workflow makes no repo`() = runTest {
        val api = object : NewAppApi {
            val events = mutableListOf<String>()
            override suspend fun createRepo(name: String, isPrivate: Boolean): String = "main".also { events += "createRepo" }
            override suspend fun renameBranch(name: String, from: String, to: String) { events += "renameBranch" }
            override suspend fun dispatch(name: String) { events += "dispatch" }
            override suspend fun runs(): List<WorkflowRunDto> = throw MissingPermissionException()
        }

        try {
            repository(api).create("LyricTutor", false).toList()
            fail("expected a MissingPermissionException")
        } catch (e: MissingPermissionException) {
            // expected
        }
        assertTrue(api.events.isEmpty())
    }

    @Test
    fun `a default branch that is not main is renamed to it before the setup starts`() = runTest {
        val api = FakeApi(happyAnswers, defaultBranch = "master")

        repository(api).create("LyricTutor", false).toList()

        val create = api.events.indexOf("createRepo LyricTutor private=false")
        val rename = api.events.indexOf("renameBranch LyricTutor master->main")
        val dispatch = api.events.indexOf("dispatch LyricTutor")
        assertTrue(create in 0 until rename && rename < dispatch)
    }

    @Test
    fun `main is not renamed`() = runTest {
        val api = FakeApi(happyAnswers)

        repository(api).create("LyricTutor", false).toList()

        assertTrue(api.events.none { it.startsWith("renameBranch") })
    }

    @Test
    fun `a refused repo is thrown to the caller and nothing is started`() = runTest {
        val api = FakeApi(happyAnswers, createError = NewAppException("A repository named LyricTutor exists already"))

        try {
            repository(api).create("LyricTutor", false).toList()
            fail("expected a NewAppException")
        } catch (e: NewAppException) {
            assertTrue(e.message!!.contains("exists already"))
        }
        assertTrue(api.events.none { it.startsWith("dispatch") })
    }

    @Test
    fun `a setup that cannot be started says the repo was made and what to do`() = runTest {
        val api = FakeApi(happyAnswers, dispatchError = IOException("GitHub returned 422"))

        try {
            repository(api).create("LyricTutor", false).toList()
            fail("expected a NewAppException")
        } catch (e: NewAppException) {
            val message = e.message!!
            assertTrue(message, message.contains("LukeNeedham/LyricTutor was created"))
            assertTrue(message, message.contains("422"))
            assertTrue(message, message.contains("resume"))
        }
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
                listOf(old), // before anything is made: the first try
                listOf(old), // the new run is not listed yet
                listOf(workflowRun(9, "in_progress"), old),
                listOf(workflowRun(9, "completed", "success"), old),
            ),
        )

        val progress = repository(api).create("LyricTutor", false).toList()

        assertEquals(NewAppProgress.Running("https://github.com/LukeNeedham/ci-workflows/actions/runs/9"), progress[3])
        assertTrue(progress.last() is NewAppProgress.Done)
    }

    @Test
    fun `a run of another app is not taken either`() = runTest {
        val other = workflowRun(5, "in_progress", title = "New App Other")
        val api = FakeApi(listOf(listOf(other), listOf(other), listOf(workflowRun(6, "completed", "success"), other)))

        val progress = repository(api).create("LyricTutor", false).toList()

        assertEquals(NewAppProgress.Running("https://github.com/LukeNeedham/ci-workflows/actions/runs/6"), progress[3])
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
}
