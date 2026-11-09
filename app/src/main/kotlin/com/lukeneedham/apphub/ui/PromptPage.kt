package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The page of the one prompt that starts a session: a new feature in [repo], or, with [pullRequestNumber] above 0, a tweak of that
 * pull request. [onStarted] gets the id of the session that the prompt started.
 */
@Composable
fun PromptPage(
    repo: String,
    pullRequestNumber: Int,
    onBack: () -> Unit,
    onStarted: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = koinViewModel<PromptViewModel>(parameters = { parametersOf(repo, pullRequestNumber) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.started.collect { onStarted(it) } }
    PromptPageContent(
        state = state,
        onPromptChange = viewModel::onPromptChange,
        onSend = viewModel::send,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
    )
}
