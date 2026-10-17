package com.lukeneedham.apphub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.PullRequest

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }
    HomeContent(state, onRefresh, onDownload, onInstall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("AppHub") }) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.error != null) {
                    item {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRefresh) { Text("Retry") }
                    }
                }
                if (state.repos.isEmpty() && !state.isLoading && state.error == null) {
                    item { Text("No open pull requests.") }
                }
                state.repos.forEach { section ->
                    item(key = section.repo) {
                        Text(section.repo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(section.pullRequests, key = { it.pr.key }) { item ->
                        PullRequestCard(item, onDownload, onInstall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRequestCard(
    item: PullRequestItem,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    val pr = item.pr
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pr.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "#${pr.number} by ${pr.author}" + if (pr.isDraft) " · draft" else "",
                style = MaterialTheme.typography.bodySmall,
            )

            ApkSection(item, onDownload, onInstall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pr.webUrl != null) {
                    OutlinedButton(onClick = { uriHandler.openUri(pr.webUrl) }) { Text("Open web deploy") }
                }
                TextButton(onClick = { uriHandler.openUri(pr.url) }) { Text("View PR") }
            }
        }
    }
}

@Composable
private fun ApkSection(
    item: PullRequestItem,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    val pr = item.pr
    when (val apk = pr.apk) {
        null -> Text("No APK build yet", style = MaterialTheme.typography.bodyMedium)
        is ApkBuild.InProgress -> Text("Build in progress…", style = MaterialTheme.typography.bodyMedium)
        is ApkBuild.Failed -> Text("Build failed", color = MaterialTheme.colorScheme.error)
        is ApkBuild.Cancelled -> Text("Build cancelled", style = MaterialTheme.typography.bodyMedium)
        is ApkBuild.Succeeded -> {
            Text(
                installStatusText(item.install) + apk.commit?.let { " · commit ${it.take(7)}" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            DownloadControls(pr, item, onDownload, onInstall)
        }
    }
}

@Composable
private fun DownloadControls(
    pr: PullRequest,
    item: PullRequestItem,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    when (val download = item.download) {
        is DownloadState.Downloading -> {
            val total = download.total
            if (total != null) {
                LinearProgressIndicator(progress = { download.bytes.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Text("Downloading ${megabytes(download.bytes)} of ${megabytes(total)} MB")
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Downloading ${megabytes(download.bytes)} MB")
            }
        }
        is DownloadState.Downloaded -> {
            Text("Downloaded")
            Button(onClick = { onInstall(pr.key) }) {
                Text(if (item.install == InstallStatus.Installed) "Reinstall" else "Install")
            }
        }
        is DownloadState.Failed -> {
            Text("Download failed: ${download.message}", color = MaterialTheme.colorScheme.error)
            Button(onClick = { onDownload(pr.key) }) { Text("Retry download") }
        }
        DownloadState.Idle -> Button(onClick = { onDownload(pr.key) }) {
            Text(if (item.install == InstallStatus.DifferentBuild) "Download update" else "Download & install")
        }
    }
}

private fun installStatusText(status: InstallStatus): String =
    when (status) {
        InstallStatus.Installed -> "This version is installed"
        InstallStatus.DifferentBuild -> "A different build is installed"
        InstallStatus.NotInstalled -> "Not installed"
        InstallStatus.Unknown -> "Not downloaded yet"
    }

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)
