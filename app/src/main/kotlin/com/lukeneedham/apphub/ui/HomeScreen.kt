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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.ui.theme.StatusColors

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onResume: () -> Unit,
    onToggleRepo: (String) -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }
    HomeContent(state, onRefresh, onToggleRepo, onDownload, onInstall, onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onToggleRepo: (String) -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
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
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
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
                        PullRequestRow(item, onDownload, onInstall)
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
) {
    val pr = item.pr
    val uriHandler = LocalUriHandler.current
    val control = controlOf(item)
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
            StatusControl(control, onDownload = { onDownload(pr.key) }, onInstall = { onInstall(pr.key) })
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

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pr.webUrl != null) LinkButton("Web", onClick = { uriHandler.openUri(pr.webUrl) })
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
}

/** A round outlined button holding just an icon. */
@Composable
private fun CircleIconButton(icon: Int, description: String, onClick: () -> Unit, size: Dp = 40.dp) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
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
private fun LinkButton(text: String, onClick: () -> Unit) {
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
private fun Pill(text: String, color: Color, dot: Boolean = true) {
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

/** What the merged build status and action control on a PR shows. */
private sealed interface Control {
    /** Only says something about the build, so it cannot be pressed. */
    data class Tag(val text: String, val color: Color) : Control

    /** A press downloads the APK ([isDownload]) or installs the one already downloaded. */
    data class Action(val label: String, val icon: Int, val isDownload: Boolean, val isError: Boolean = false) : Control

    /** [fraction] is null while the size is not known. */
    data class Progress(val fraction: Float?, val text: String) : Control
}

@Composable
private fun controlOf(item: PullRequestItem): Control {
    val scheme = MaterialTheme.colorScheme
    val target = item.pr.downloadableApk
    val download = item.download
    if (download is DownloadState.Downloading) {
        val total = download.total
        return if (total != null) {
            Control.Progress(download.bytes.toFloat() / total, "${(download.bytes * 100 / total).toInt()}%")
        } else {
            Control.Progress(null, "${megabytes(download.bytes)} MB")
        }
    }
    if (target != null) {
        when (download) {
            is DownloadState.Downloaded -> {
                val label = if (item.install == InstallStatus.Installed) "Reinstall" else "Install"
                return Control.Action(label, R.drawable.ic_install, isDownload = false)
            }
            is DownloadState.Failed -> return Control.Action("Retry", R.drawable.ic_download, isDownload = true, isError = true)
            else -> when {
                target.isStale -> return Control.Action("Old build", R.drawable.ic_download, isDownload = true)
                item.install == InstallStatus.DifferentBuild ->
                    return Control.Action("Update", R.drawable.ic_download, isDownload = true)
                item.install != InstallStatus.Installed ->
                    return Control.Action("Download", R.drawable.ic_download, isDownload = true)
            }
        }
    }
    return when (val apk = item.pr.apk) {
        null -> Control.Tag("No build", scheme.onSurfaceVariant)
        is ApkBuild.InProgress -> Control.Tag("Building", StatusColors.info)
        is ApkBuild.Failed -> Control.Tag("Build failed", scheme.error)
        is ApkBuild.Cancelled -> Control.Tag("Cancelled", scheme.onSurfaceVariant)
        is ApkBuild.Succeeded -> Control.Tag("Installed", StatusColors.success)
    }
}

/** The one pill on the right of a PR: a tag for the build, or the filled button or progress that moves it on. */
@Composable
private fun StatusControl(control: Control, onDownload: () -> Unit, onInstall: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (control) {
        is Control.Tag -> Pill(control.text, control.color)
        is Control.Action -> {
            val container = if (control.isError) scheme.error else scheme.primary
            val content = if (control.isError) scheme.onError else scheme.onPrimary
            Surface(
                onClick = if (control.isDownload) onDownload else onInstall,
                shape = CircleShape,
                color = container,
                contentColor = content,
            ) {
                Row(
                    modifier = Modifier.height(32.dp).padding(start = 10.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(control.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(control.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        is Control.Progress -> {
            val color = scheme.primary
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val indicator = Modifier.size(16.dp)
                if (control.fraction != null) {
                    CircularProgressIndicator(progress = { control.fraction }, modifier = indicator, strokeWidth = 2.5.dp)
                } else {
                    CircularProgressIndicator(modifier = indicator, strokeWidth = 2.5.dp)
                }
                Text(control.text, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)
