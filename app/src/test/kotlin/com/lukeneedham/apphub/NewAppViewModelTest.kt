package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.MissingPermissionException
import com.lukeneedham.apphub.domain.NewAppException
import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.domain.NewAppSource
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.ui.NewAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NewAppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSource(private val behaviour: (String, Boolean) -> Flow<NewAppProgress>) : NewAppSource {
        val started = mutableListOf<Pair<String, Boolean>>()

        override fun create(name: String, isPrivate: Boolean): Flow<NewAppProgress> {
            started += name to isPrivate
            return behaviour(name, isPrivate)
        }
    }

    private class MemoryTokens(initial: String? = "github_pat_x") : TokenStore {
        private val state = MutableStateFlow(initial)
        override val token: StateFlow<String?> = state
        override fun set(token: String?) { state.value = token }
    }

    private val done = NewAppProgress.Done("LukeNeedham/LyricTutor", "run-url")

    private fun viewModel(
        source: FakeSource,
        tokens: TokenStore = MemoryTokens(),
        creationTokens: TokenStore = MemoryTokens("github_pat_create"),
    ) = NewAppViewModel(source, tokens, creationTokens)

    @Test
    fun `without a token nothing can be created`() = runTest(dispatcher) {
        val source = FakeSource { _, _ -> flowOf(done) }
        val viewModel = viewModel(source, MemoryTokens(null))
        viewModel.onNameChange("LyricTutor")

        viewModel.create()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.signedIn)
        assertTrue(source.started.isEmpty())
    }

    @Test
    fun `without a repo creation token nothing can be created`() = runTest(dispatcher) {
        val source = FakeSource { _, _ -> flowOf(done) }
        val viewModel = viewModel(source, creationTokens = MemoryTokens(null))
        viewModel.onNameChange("LyricTutor")

        viewModel.create()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasCreationToken)
        assertFalse(viewModel.uiState.value.canCreate)
        assertTrue(source.started.isEmpty())
    }

    @Test
    fun `a repo creation token saved in Settings while the page is open enables it`() = runTest(dispatcher) {
        val creationTokens = MemoryTokens(null)
        val viewModel = viewModel(FakeSource { _, _ -> flowOf(done) }, creationTokens = creationTokens)
        viewModel.onNameChange("LyricTutor")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canCreate)

        creationTokens.set("github_pat_new")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasCreationToken)
        assertTrue(viewModel.uiState.value.canCreate)
    }

    @Test
    fun `a repo creation token removed in Settings stops creating`() = runTest(dispatcher) {
        val creationTokens = MemoryTokens("github_pat_create")
        val viewModel = viewModel(FakeSource { _, _ -> flowOf(done) }, creationTokens = creationTokens)
        viewModel.onNameChange("LyricTutor")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canCreate)

        creationTokens.set(null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasCreationToken)
        assertFalse(viewModel.uiState.value.canCreate)
    }

    @Test
    fun `a token that arrives later enables it`() = runTest(dispatcher) {
        val tokens = MemoryTokens(null)
        val viewModel = viewModel(FakeSource { _, _ -> flowOf(done) }, tokens)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.signedIn)

        tokens.set("github_pat_x")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.signedIn)
    }

    @Test
    fun `a wrong name says what is wrong and cannot be created`() = runTest(dispatcher) {
        val source = FakeSource { _, _ -> flowOf(done) }
        val viewModel = viewModel(source)

        viewModel.onNameChange("my app")
        viewModel.create()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.nameError)
        assertFalse(viewModel.uiState.value.canCreate)
        assertTrue(source.started.isEmpty())
    }

    @Test
    fun `nothing is said about the name before anything is typed`() = runTest(dispatcher) {
        assertNull(viewModel(FakeSource { _, _ -> flowOf(done) }).uiState.value.nameError)
    }

    @Test
    fun `the app is created with the name and the visibility asked for, and the progress shown`() = runTest(dispatcher) {
        val source = FakeSource { _, _ ->
            flowOf(NewAppProgress.CreatingRepo, NewAppProgress.Starting, NewAppProgress.Waiting, NewAppProgress.Running("run-url"), done)
        }
        val viewModel = viewModel(source)
        viewModel.onNameChange("LyricTutor")
        viewModel.onPrivateChange(true)

        viewModel.create()
        advanceUntilIdle()

        assertEquals(listOf("LyricTutor" to true), source.started)
        assertEquals(done, viewModel.uiState.value.progress)
        assertFalse(viewModel.uiState.value.inProgress)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `while one is being created another cannot be started and the form is locked`() = runTest(dispatcher) {
        val source = FakeSource { _, _ -> flow { emit(NewAppProgress.Waiting); awaitCancellation() } }
        val viewModel = viewModel(source)
        viewModel.onNameChange("LyricTutor")
        viewModel.create()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.inProgress)

        viewModel.create()
        viewModel.onNameChange("Other")
        viewModel.onPrivateChange(true)
        advanceUntilIdle()

        assertEquals(1, source.started.size)
        assertEquals("LyricTutor", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.isPrivate)
    }

    @Test
    fun `a rejected token and a missing permission each get their own message`() = runTest(dispatcher) {
        val errors = mutableListOf<String?>()
        for (failure in listOf<Exception>(InvalidTokenException(), MissingPermissionException())) {
            val viewModel = viewModel(FakeSource { _, _ -> flow { throw failure } })
            viewModel.onNameChange("LyricTutor")
            viewModel.create()
            advanceUntilIdle()
            errors += viewModel.uiState.value.error
            assertNull(viewModel.uiState.value.progress)
        }
        assertTrue(errors[0]!!.contains("rejected"))
        assertTrue(errors[1]!!.contains("Actions"))
    }

    @Test
    fun `a failure with a message of its own shows that message as it is`() = runTest(dispatcher) {
        val message = "A repository named LyricTutor exists already, or GitHub does not take the name."
        val viewModel = viewModel(FakeSource { _, _ -> flow { throw NewAppException(message) } })
        viewModel.onNameChange("LyricTutor")

        viewModel.create()
        advanceUntilIdle()

        assertEquals(message, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.progress)
        assertTrue(viewModel.uiState.value.canCreate)
    }

    @Test
    fun `any other failure shows its message and lets the user try again`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSource { _, _ -> flow { throw IOException("GitHub returned 422") } })
        viewModel.onNameChange("LyricTutor")

        viewModel.create()
        advanceUntilIdle()

        assertEquals("GitHub returned 422", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canCreate)
    }

    @Test
    fun `closing a created app starts a blank form`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSource { _, _ -> flowOf(done) })
        viewModel.onNameChange("LyricTutor")
        viewModel.create()
        advanceUntilIdle()

        viewModel.dismissResult()

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertNull(state.progress)
        assertTrue(state.signedIn)
        assertTrue("the saved token stays", state.hasCreationToken)
    }

    @Test
    fun `closing a failed run keeps the name to try again`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSource { _, _ -> flowOf(NewAppProgress.Failed("run-url")) })
        viewModel.onNameChange("LyricTutor")
        viewModel.create()
        advanceUntilIdle()

        viewModel.dismissResult()

        assertEquals("LyricTutor", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.progress)
        assertTrue(viewModel.uiState.value.canCreate)
    }
}
