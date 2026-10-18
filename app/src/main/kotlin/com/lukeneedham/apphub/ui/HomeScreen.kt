package com.lukeneedham.apphub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lukeneedham.apphub.R
import coil3.compose.AsyncImage
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.PullRequestRef
import com.lukeneedham.apphub.domain.repoIconUrl
import com.lukeneedham.apphub.ui.theme.StatusColors

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onToggleRepo: (String) -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpenPullRequest: (PullRequestRef) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }
    HomeContent(state, onRefresh, onToggleRepo, onDownload, onInstall, onOpenPullRequest, onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onToggleRepo: (String) -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpenPullRequest: (PullRequestRef) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("AppHub", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    val prCount = state.repos.sumOf { it.pullRequests.size }
                    val projectCount = state.repos.count { it.pullRequests.isNotEmpty() }
                    Text(
                        when {
                            prCount == 0 -> if (state.isLoading && state.repos.isEmpty()) "Loading…" else "No open pull requests"
                            else -> "$prCount open pull request${if (prCount == 1) "" else "s"} in " +
                                "$projectCount project${if (projectCount == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (state.error != null) {
                    item { ErrorCard(state.error, onRetry = onRefresh) }
                }
                items(state.repos, key = { it.repo }) { section ->
                    RepoCard(
                        section = section,
                        expanded = section.repo !in state.collapsedRepos,
                        onToggle = { onToggleRepo(section.repo) },
                        onDownload = onDownload,
                        onInstall = onInstall,
                        onOpenPullRequest = onOpenPullRequest,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) { Text("Try again") }
        }
    }
}

/** One project: a header that collapses and expands its pull requests. */
@Composable
private fun RepoCard(
    section: RepoSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpenPullRequest: (PullRequestRef) -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Always takes its space, so names line up whether or not the repo has an icon
                AsyncImage(
                    model = repoIconUrl(section.repo),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        section.repo.substringAfter('/'),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Pill(
                    text = section.pullRequests.size.toString(),
                    color = if (section.pullRequests.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    dot = false,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // One animation for card and content together: the size follows the content, which fades with it
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    val uriHandler = LocalUriHandler.current
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircleIconButton(
                            icon = R.drawable.ic_github,
                            description = "Open ${section.repo} on GitHub",
                            onClick = { uriHandler.openUri(section.url) },
                        )
                        CircleIconButton(
                            icon = R.drawable.ic_claude,
                            description = "Start a Claude session on ${section.repo}",
                            onClick = { uriHandler.openUri(section.claudeUrl) },
                        )
                    }
                    if (section.pullRequests.isEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "No open pull requests",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                    section.pullRequests.forEach { item ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PullRequestRow(item, onDownload, onInstall, onOpenPullRequest)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PullRequestRow(
    item: PullRequestItem,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpenPullRequest: (PullRequestRef) -> Unit,
) {
    val pr = item.pr
    val uriHandler = LocalUriHandler.current
    val status = statusOf(item)
    val target = pr.downloadableApk

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pr.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "#${pr.number} · ${pr.author}" + if (pr.isDraft) " · draft" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(status.text, status.color)
        }

        if (target?.isStale == true) {
            Text(
                "Out of date: there are later changes, rebuilding now",
                style = MaterialTheme.typography.labelMedium,
                color = StatusColors.warning,
            )
        }

        when (val download = item.download) {
            is DownloadState.Downloading -> DownloadProgress(download)
            is DownloadState.Failed -> Text(
                "Download failed: ${download.message}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            primaryAction(item)?.let { (label, isDownload) ->
                Button(
                    onClick = { if (isDownload) onDownload(pr.key) else onInstall(pr.key) },
                    modifier = Modifier.height(36.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                ) { Text(label) }
            }
            LinkButton("Details", onClick = { onOpenPullRequest(PullRequestRef(pr.repo, pr.number)) })
            if (pr.webUrl != null) LinkButton("Web", onClick = { uriHandler.openUri(pr.webUrl) })
            if (pr.sessionUrl != null) LinkButton("Claude session", onClick = { uriHandler.openUri(pr.sessionUrl) })
            LinkButton("Open PR", onClick = { uriHandler.openUri(pr.url) })
        }
    }
}

@Composable
internal fun DownloadProgress(download: DownloadState.Downloading) {
    val total = download.total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val shape = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        if (total != null) {
            LinearProgressIndicator(progress = { download.bytes.toFloat() / total }, modifier = shape)
        } else {
            LinearProgressIndicator(modifier = shape)
        }
        Text(
            if (total != null) "Downloading · ${megabytes(download.bytes)} of ${megabytes(total)} MB"
            else "Downloading · ${megabytes(download.bytes)} MB",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A round outlined button holding just an icon. */
@Composable
private fun CircleIconButton(icon: Int, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun LinkButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** A small rounded tag: [color] on a faint tint of itself, with an optional dot. */
@Composable
internal fun Pill(text: String, color: Color, dot: Boolean = true) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

internal data class Status(val text: String, val color: Color)

@Composable
internal fun statusOf(item: PullRequestItem): Status {
    val scheme = MaterialTheme.colorScheme
    return when (val apk = item.pr.apk) {
        null -> Status("No build", scheme.onSurfaceVariant)
        is ApkBuild.InProgress -> Status("Building", StatusColors.info)
        is ApkBuild.Failed -> Status("Build failed", scheme.error)
        is ApkBuild.Cancelled -> Status("Cancelled", scheme.onSurfaceVariant)
        is ApkBuild.Succeeded -> when (item.install) {
            InstallStatus.Installed -> Status("Installed", StatusColors.success)
            InstallStatus.DifferentBuild -> Status("Update ready", StatusColors.warning)
            InstallStatus.NotInstalled, InstallStatus.Unknown -> Status("Ready", scheme.primary)
        }
    }
}

/** The main button of a PR, and whether pressing it downloads (true) or installs (false). Null for none. */
internal fun primaryAction(item: PullRequestItem): Pair<String, Boolean>? {
    val target = item.pr.downloadableApk ?: return null
    return when (item.download) {
        is DownloadState.Downloading -> null
        is DownloadState.Downloaded ->
            (if (item.install == InstallStatus.Installed) "Reinstall" else "Install") to false
        is DownloadState.Failed -> "Retry" to true
        DownloadState.Idle -> {
            val label = when {
                target.isStale -> "Download old build"
                item.install == InstallStatus.DifferentBuild -> "Download update"
                else -> "Download & install"
            }
            label to true
        }
    }
}

internal fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)
