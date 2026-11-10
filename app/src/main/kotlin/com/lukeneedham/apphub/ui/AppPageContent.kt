package com.lukeneedham.apphub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.domain.PullRequestRef
import com.lukeneedham.apphub.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPageContent(
    repo: String,
    state: AppUiState,
    onRefresh: () -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpen: (String) -> Unit,
    mainBuild: MainBuildActions,
    onOpenPullRequest: (PullRequestRef) -> Unit,
    onStartFeature: () -> Unit,
    onOpenFeature: (String) -> Unit,
    onBack: () -> Unit,
) {
    val background = AppTheme.colors.background
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(repo.substringAfter('/'), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = { PageDebugButton("AppPage") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                if (state.error != null) {
                    ErrorCard(state.error, onRetry = onRefresh)
                }
                state.section?.let { section ->
                    RepoCard(
                        section = section,
                        expanded = true,
                        onToggle = null,
                        onOpenApp = null,
                        onDownload = onDownload,
                        onCancelDownload = onCancelDownload,
                        onInstall = onInstall,
                        onOpen = onOpen,
                        mainBuild = mainBuild,
                        onOpenPullRequest = onOpenPullRequest,
                        onStartFeature = onStartFeature,
                        onOpenFeature = onOpenFeature,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    AppTheme {
        AppPageContent(
            repo = "LukeNeedham/FlagTutor",
            state = AppUiState(isLoading = false, section = RepoSection("LukeNeedham/FlagTutor", emptyList())),
            onRefresh = {},
            onDownload = {},
            onCancelDownload = {},
            onInstall = {},
            onOpen = {},
            mainBuild = MainBuildActions(download = {}, cancelDownload = {}, install = {}, open = {}),
            onOpenPullRequest = {},
            onStartFeature = {},
            onOpenFeature = {},
            onBack = {},
        )
    }
}
