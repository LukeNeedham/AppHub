package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lukeneedham.apphub.domain.PullRequestRef
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AppPage(
    repo: String,
    onBack: () -> Unit,
    onOpenPullRequest: (PullRequestRef) -> Unit,
    onStartFeature: (String) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val viewModel = koinViewModel<AppViewModel>(parameters = { parametersOf(repo) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    AppPageContent(
        repo = repo,
        state = state,
        onRefresh = viewModel::refresh,
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onInstall = viewModel::install,
        onOpen = viewModel::open,
        mainBuild = MainBuildActions(
            download = { viewModel.downloadMain() },
            cancelDownload = { viewModel.cancelMain() },
            install = { viewModel.installMain() },
            open = { viewModel.openMain() },
        ),
        onOpenPullRequest = onOpenPullRequest,
        onStartFeature = { onStartFeature(repo) },
        onOpenFeature = onOpenFeature,
        onBack = onBack,
    )
}
