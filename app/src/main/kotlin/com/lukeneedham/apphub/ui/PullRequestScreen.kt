package com.lukeneedham.apphub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.ui.theme.StatusColors

@Composable
fun PullRequestScreen(
    repo: String,
    number: Int,
    state: PullRequestUiState,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }
    PullRequestContent(repo, number, state, onRefresh, onDownload, onCancelDownload, onInstall, onOpen, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullRequestContent(
    repo: String,
    number: Int,
    state: PullRequestUiState,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val uriHandler = LocalUriHandler.current
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("${repo.substringAfter('/')} #$number", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
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
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.error != null) ErrorCard(state.error, onRetry = onRefresh)
                val item = state.item
                if (item == null) {
                    Text(
                        when {
                            state.notFound -> "This pull request is not open any more. It may have been merged or closed."
                            state.isLoading -> "Loading…"
                            else -> "Could not find this pull request."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { uriHandler.openUri("https://github.com/$repo/pull/$number") }) { Text("Open on GitHub") }
                    return@Column
                }

                val pr = item.pr
                val target = pr.downloadableApk

                Text(pr.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "#${pr.number} · ${pr.author}" + if (pr.isDraft) " · draft" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PullRequestStatus(item, onDownload = onDownload, onCancel = onCancelDownload, onInstall = onInstall, onOpen = onOpen)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pr.webUrl != null) {
                            CircleIconButton(
                                icon = R.drawable.ic_globe,
                                description = "Open web deployment",
                                onClick = { uriHandler.openUri(pr.webUrl) },
                                size = 36.dp,
                            )
                        }
                        if (pr.sessionUrl != null) {
                            CircleIconButton(
                                icon = R.drawable.ic_claude,
                                description = "Open Claude session",
                                onClick = { uriHandler.openUri(pr.sessionUrl) },
                                size = 36.dp,
                            )
                        }
                        CircleIconButton(
                            icon = R.drawable.ic_pull_request,
                            description = "Open pull request on GitHub",
                            onClick = { uriHandler.openUri(pr.url) },
                            size = 36.dp,
                        )
                    }
                }


                if (target?.isStale == true) {
                    Text(
                        "Out of date: there are later changes, rebuilding now",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusColors.warning,
                    )
                }

                (item.download as? DownloadState.Failed)?.let { failed ->
                    Text(
                        "Download failed: ${failed.message}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Description", style = MaterialTheme.typography.titleMedium)
                if (pr.description != null) {
                    SelectionContainer(Modifier.fillMaxWidth()) {
                        Text(pr.description, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        "No description",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
