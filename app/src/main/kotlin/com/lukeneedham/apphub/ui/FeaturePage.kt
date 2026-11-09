package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The page of one session. [onOpenPullRequest] opens the pull request in AppHub; [onBack] also follows a removal. */
@Composable
fun FeaturePage(
    id: String,
    onBack: () -> Unit,
    onOpenPullRequest: (repo: String, number: Int) -> Unit,
) {
    val viewModel = koinViewModel<FeatureViewModel>(parameters = { parametersOf(id) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeaturePageContent(
        state = state,
        onAnswerChange = viewModel::onAnswerChange,
        onSendAnswer = viewModel::sendAnswer,
        onStop = viewModel::stop,
        onDismiss = {
            viewModel.dismiss()
            onBack()
        },
        onOpenPullRequest = onOpenPullRequest,
        onBack = onBack,
    )
}
