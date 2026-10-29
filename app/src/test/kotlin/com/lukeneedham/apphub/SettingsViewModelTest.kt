package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.CommentDto
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.IssueDto
import com.lukeneedham.apphub.data.ReleaseDto
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeApi(private val valid: Set<String>) : GithubApi {
        override suspend fun openPullRequests(): List<IssueDto> = emptyList()
        override suspend fun repositories(): List<String> = emptyList()
        override suspend fun commonApps(): Set<String> = emptySet()
        override suspend fun comments(repo: String, number: Int): List<CommentDto> = emptyList()
        override suspend fun mainRelease(repo: String): ReleaseDto? = null
        override suspend fun releasePublishedAt(repo: String, tag: String): String? = null
        override suspend fun checkToken(token: String): RateLimitInfo =
            if (token in valid) RateLimitInfo(5000, 4990) else throw InvalidTokenException()
    }

    private class MemoryTokens(initial: String? = null) : TokenStore {
        private val state = MutableStateFlow(initial)
        override val token: StateFlow<String?> = state
        override fun set(token: String?) { state.value = token }
    }

    @Test
    fun aValidTokenIsKeptAndItsAllowanceShown() = runTest(dispatcher) {
        val tokens = MemoryTokens()
        val viewModel = SettingsViewModel(FakeApi(setOf("github_pat_abcd")), tokens, MemoryTokens())

        viewModel.onInputChange("  github_pat_abcd ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("github_pat_abcd", tokens.token.value)
        val state = viewModel.uiState.value
        assertEquals(true, state.signedIn)
        assertEquals("", state.input)
        assertEquals(RateLimitInfo(5000, 4990), state.rateLimit)
    }

    @Test
    fun aRejectedTokenIsNotKept() = runTest(dispatcher) {
        val tokens = MemoryTokens()
        val viewModel = SettingsViewModel(FakeApi(emptySet()), tokens, MemoryTokens())

        viewModel.onInputChange("wrong")
        viewModel.save()
        advanceUntilIdle()

        assertNull(tokens.token.value)
        assertNotNull(viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.signedIn)
    }

    @Test
    fun removingTheTokenSignsOut() = runTest(dispatcher) {
        val tokens = MemoryTokens("github_pat_abcd")
        val viewModel = SettingsViewModel(FakeApi(setOf("github_pat_abcd")), tokens, MemoryTokens())
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.signedIn)

        viewModel.remove()

        assertNull(tokens.token.value)
        assertEquals(false, viewModel.uiState.value.signedIn)
    }

    @Test
    fun aSavedRepoCreationTokenIsShownAsSavedFromTheStart() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeApi(emptySet()), MemoryTokens(), MemoryTokens("github_pat_create"))
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasRepoCreationToken)
    }

    @Test
    fun aRepoCreationTokenThatIsTypedAndSavedIsKeptTrimmedAndForgottenFromTheField() = runTest(dispatcher) {
        val creationTokens = MemoryTokens()
        val viewModel = SettingsViewModel(FakeApi(emptySet()), MemoryTokens(), creationTokens)

        viewModel.onRepoCreationTokenInputChange("  github_pat_new ")
        viewModel.saveRepoCreationToken()

        assertEquals("github_pat_new", creationTokens.token.value)
        val state = viewModel.uiState.value
        assertEquals(true, state.hasRepoCreationToken)
        assertEquals("", state.repoCreationTokenInput)
    }

    @Test
    fun anEmptyRepoCreationTokenIsNotSaved() = runTest(dispatcher) {
        val creationTokens = MemoryTokens()
        val viewModel = SettingsViewModel(FakeApi(emptySet()), MemoryTokens(), creationTokens)

        viewModel.onRepoCreationTokenInputChange("   ")
        viewModel.saveRepoCreationToken()

        assertNull(creationTokens.token.value)
        assertEquals(false, viewModel.uiState.value.hasRepoCreationToken)
    }

    @Test
    fun removingTheRepoCreationTokenForgetsItAndLeavesTheGithubTokenAlone() = runTest(dispatcher) {
        val tokens = MemoryTokens("github_pat_abcd")
        val creationTokens = MemoryTokens("github_pat_create")
        val viewModel = SettingsViewModel(FakeApi(setOf("github_pat_abcd")), tokens, creationTokens)
        advanceUntilIdle()

        viewModel.removeRepoCreationToken()

        assertNull(creationTokens.token.value)
        assertEquals(false, viewModel.uiState.value.hasRepoCreationToken)
        assertEquals("github_pat_abcd", tokens.token.value)
        assertEquals(true, viewModel.uiState.value.signedIn)
    }

    @Test
    fun theGithubTokenAndTheRepoCreationTokenDoNotTouchEachOther() = runTest(dispatcher) {
        val tokens = MemoryTokens()
        val creationTokens = MemoryTokens("github_pat_create")
        val viewModel = SettingsViewModel(FakeApi(setOf("github_pat_abcd")), tokens, creationTokens)
        viewModel.onRepoCreationTokenInputChange("half typed")

        viewModel.onInputChange("github_pat_abcd")
        viewModel.save()
        advanceUntilIdle()
        viewModel.remove()

        assertEquals("github_pat_create", creationTokens.token.value)
        assertEquals(true, viewModel.uiState.value.hasRepoCreationToken)
        assertEquals("half typed", viewModel.uiState.value.repoCreationTokenInput)
    }
}
