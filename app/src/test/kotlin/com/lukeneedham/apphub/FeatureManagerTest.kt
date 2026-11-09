package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.CiStatus
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureKind
import com.lukeneedham.apphub.domain.FeatureManager
import com.lukeneedham.apphub.domain.FeatureRunner
import com.lukeneedham.apphub.domain.FeatureStatus
import com.lukeneedham.apphub.domain.PullRequestInfo
import com.lukeneedham.apphub.domain.featureBranchName
import com.lukeneedham.apphub.domain.find
import com.lukeneedham.apphub.domain.summarize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureManagerTest {

    private val repo = "LukeNeedham/FlagTutor"
    private val pr = PullRequestInfo(12, "Quiz", "https://github.com/$repo/pull/12", "feature/quiz", isOpen = true)

    private fun TestScope.manager(store: InMemoryFeatureStore, llm: ScriptedLlm, host: FakeCodeHost) =
        FeatureManager(
            store,
            FeatureRunner(FixedLlmSource(llm), host, store, ciPollMillis = 10),
            this,
            newId = { "id${store.features.value.size}" },
            nowMillis = { 1_000 },
        )

    @Test
    fun aStartedFeatureIsListedAsInProgressWithASummary() = runTest {
        val store = InMemoryFeatureStore()
        val manager = manager(store, ScriptedLlm(listOf(reply(text = "?"))), FakeCodeHost())

        val id = manager.startFeature(repo, "  Add a hint button\nthat removes two answers ")

        val feature = store.find(id!!)!!
        assertEquals(FeatureKind.Feature, feature.kind)
        assertEquals(FeatureStatus.Working, feature.status)
        assertEquals("Add a hint button", feature.summary)
        assertEquals("Add a hint button\nthat removes two answers", feature.prompt)
        assertEquals("feature/add-a-hint-button-that-removes-two-answe-id0", feature.branch)
        manager.stop(id)
    }

    @Test
    fun aBlankPromptStartsNothing() = runTest {
        val store = InMemoryFeatureStore()
        val manager = manager(store, ScriptedLlm(emptyList()), FakeCodeHost())

        assertNull(manager.startFeature(repo, "   "))
        assertNull(manager.startTweak(repo, 12, ""))
        assertTrue(store.features.value.isEmpty())
    }

    @Test
    fun aTweakWaitsWhileAnotherSessionIsOnTheSamePullRequest() = runTest {
        val store = InMemoryFeatureStore()
        // The first tweak asks a question and so stays active
        val llm = ScriptedLlm(listOf(reply(text = "Which colour?")))
        val manager = manager(store, llm, FakeCodeHost(tweakedPullRequest = pr))

        val first = manager.startTweak(repo, 12, "Make it blue")
        advanceUntilIdle()

        assertEquals(FeatureStatus.NeedsAnswer, store.find(first!!)!!.status)
        assertTrue(manager.isBusy(repo, 12))
        assertNull(manager.startTweak(repo, 12, "Make it red"))
        assertFalse(manager.isBusy(repo, 13))
        manager.stop(first)
        assertFalse(manager.isBusy(repo, 12))
    }

    @Test
    fun theAnswerReachesTheSessionThatAsked() = runTest {
        val store = InMemoryFeatureStore()
        val llm = ScriptedLlm(
            listOf(
                reply(text = "Which colour?"),
                reply(toolCall("1", "write_file", "path" to "A.kt", "content" to "blue")),
                reply(toolCall("2", "submit", "commit_message" to "Blue")),
            ),
        )
        val host = FakeCodeHost(tweakedPullRequest = pr, ci = listOf(CiStatus.Passed))
        val manager = manager(store, llm, host)

        val id = manager.startTweak(repo, 12, "Change the colour")!!
        advanceUntilIdle()
        manager.answer(id, "Blue")
        advanceUntilIdle()

        assertEquals(FeatureStatus.ReadyForReview, store.find(id)!!.status)
        assertEquals(1, host.commits.size)
        assertFalse(manager.isBusy(repo, 12))
    }

    @Test
    fun stoppingASessionEndsItAsFailed() = runTest {
        val store = InMemoryFeatureStore()
        val manager = manager(store, ScriptedLlm(listOf(reply(text = "Which colour?"))), FakeCodeHost(tweakedPullRequest = pr))
        val id = manager.startTweak(repo, 12, "Change the colour")!!
        advanceUntilIdle()

        manager.stop(id)
        advanceUntilIdle()

        assertEquals(FeatureStatus.Failed, store.find(id)!!.status)
    }

    @Test
    fun sessionsLeftRunningByAClosedAppAreFailedOnStart() = runTest {
        val store = InMemoryFeatureStore()
        store.add(Feature("a", FeatureKind.Feature, repo, "p", "p", FeatureStatus.WaitingForCi, createdAtMillis = 0))
        store.add(Feature("b", FeatureKind.Feature, repo, "p", "p", FeatureStatus.ReadyForReview, createdAtMillis = 0))
        val manager = manager(store, ScriptedLlm(emptyList()), FakeCodeHost())

        manager.failInterrupted()

        assertEquals(FeatureStatus.Failed, store.find("a")!!.status)
        assertEquals(FeatureStatus.ReadyForReview, store.find("b")!!.status)
    }

    @Test
    fun dismissingKeepsARunningSessionAndRemovesAFinishedOne() = runTest {
        val store = InMemoryFeatureStore()
        store.add(Feature("a", FeatureKind.Feature, repo, "p", "p", FeatureStatus.Working, createdAtMillis = 0))
        store.add(Feature("b", FeatureKind.Feature, repo, "p", "p", FeatureStatus.Failed, createdAtMillis = 0))
        val manager = manager(store, ScriptedLlm(emptyList()), FakeCodeHost())

        manager.dismiss("a")
        manager.dismiss("b")

        assertNotNull(store.find("a"))
        assertNull(store.find("b"))
    }

    @Test
    fun summariesAndBranchNamesAreShortAndSafe() {
        assertEquals("Add a button", summarize("\n\n Add a button \nmore"))
        assertEquals(80, summarize("x".repeat(200)).length)
        assertEquals("feature/add-a-button-abc123", featureBranchName("Add a button!!", "abc123xyz"))
        assertEquals("feature/abc123", featureBranchName("???", "abc123xyz"))
    }
}
