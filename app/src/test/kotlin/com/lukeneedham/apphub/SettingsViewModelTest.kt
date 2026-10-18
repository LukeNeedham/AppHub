package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.CommentDto
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.IssueDto
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
        override suspend fun comments(repo: String, number: Int): List<CommentDto> = emptyList()
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
        val viewModel = SettingsViewModel(FakeApi(setOf("github_pat_abcd")), tokens)

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
        val viewModel = SettingsViewModel(FakeApi(emptySet()), tokens)

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
        val viewModel = SettingsViewModel(FakeApi(setOf("github_pat_abcd")), tokens)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.signedIn)

        viewModel.remove()

        assertNull(tokens.token.value)
        assertEquals(false, viewModel.uiState.value.signedIn)
    }
}
