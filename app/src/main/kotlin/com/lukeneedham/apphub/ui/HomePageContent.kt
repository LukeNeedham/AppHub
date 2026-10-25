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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lukeneedham.apphub.R
import coil3.compose.AsyncImage
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainPhase
import com.lukeneedham.apphub.domain.PullRequestRef
import com.lukeneedham.apphub.domain.ageText
import com.lukeneedham.apphub.domain.formatTimestamp
import com.lukeneedham.apphub.domain.repoIconUrl
import com.lukeneedham.apphub.ui.theme.StatusColors
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lukeneedham.apphub.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

/** What the main build row of a repo can do; each takes the repo `owner/name`. */
data class MainBuildActions(
    val build: (String) -> Unit,
    val download: (String) -> Unit,
    val cancelDownload: (String) -> Unit,
    val install: (String) -> Unit,
    val open: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomePageContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onToggleRepo: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpen: (String) -> Unit,
    mainBuild: MainBuildActions,
    onOpenPullRequest: (PullRequestRef) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumping by remember { mutableStateOf(false) }
    var jumpId by remember { mutableIntStateOf(0) }
    // Items before the repo cards: the summary, and the error card if there is one
    val headerCount = if (state.error != null) 2 else 1
    val selectedIndex by remember(headerCount, state.repos.size) {
        derivedStateOf { (listState.firstVisibleItemIndex - headerCount).coerceIn(0, (state.repos.size - 1).coerceAtLeast(0)) }
    }
    // Room after the last project, so that it too can be scrolled to the top and selected
    val density = LocalDensity.current
    var lastRepoHeight by remember { mutableIntStateOf(0) }
    val lastRepo = state.repos.lastOrNull()?.repo
    LaunchedEffect(lastRepo) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == lastRepo }?.size }
            .collect { if (it != null) lastRepoHeight = it }
    }
    val trailingSpace by remember {
        derivedStateOf {
            // What the viewport holds beyond the last project, less the padding and spacing around it
            (with(density) { (listState.layoutInfo.viewportSize.height - lastRepoHeight).toDp() } -
                ListTopPadding - ListItemSpacing - ListBottomPadding).coerceAtLeast(0.dp)
        }
    }
    Scaffold(
        containerColor = background,
        topBar = {
            Column {
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
                if (state.repos.isNotEmpty()) {
                    RepoNavigation(
                        repos = state.repos.map { it.repo },
                        prCounts = state.repos.map { it.pullRequests.size },
                        selectedIndex = selectedIndex,
                        // The row has already settled on the project it jumps to, so it ignores the list passing the ones between
                        followList = !jumping,
                        onSelect = { index ->
                            val id = ++jumpId
                            scope.launch {
                                jumping = true
                                try {
                                    listState.animateScrollToItem(headerCount + index)
                                } finally {
                                    // A later jump has taken over if the id moved on
                                    if (id == jumpId) jumping = false
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = ListTopPadding, bottom = ListBottomPadding),
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
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
                        onCancelDownload = onCancelDownload,
                        onInstall = onInstall,
                        onOpen = onOpen,
                        mainBuild = mainBuild,
                        onOpenPullRequest = onOpenPullRequest,
                    )
                }
                item(key = "trailing-space") { Spacer(Modifier.height(trailingSpace)) }
            }
        }
    }
}

private val ListTopPadding = 4.dp

private val ListBottomPadding = 32.dp

private val ListItemSpacing = 14.dp

/** The index of the item whose middle is nearest to the middle of the row, or null while nothing is shown. */
private fun LazyListState.centredIndex(): Int? {
    val info = layoutInfo
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }?.index
}

/**
 * A pinned row of round app icons, one per project. The selected one, which is the project at the top of the list,
 * is centred and a little bigger. Scrolling the row snaps the nearest icon to the centre and selects it; a tap
 * centres that icon and selects it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoNavigation(repos: List<String>, prCounts: List<Int>, selectedIndex: Int, followList: Boolean, onSelect: (Int) -> Unit) {
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemSize = 44.dp
    val gap = 14.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    var touched by remember { mutableStateOf(false) }
    var userScroll by remember { mutableStateOf(false) }

    LaunchedEffect(rowState) {
        rowState.interactionSource.interactions.collect {
            when (it) {
                is DragInteraction.Start -> touched = true
                // A touch that never moved the row leaves nothing to settle
                is DragInteraction.Stop, is DragInteraction.Cancel -> if (!rowState.isScrollInProgress) touched = false
                else -> Unit
            }
        }
    }
    // The list only follows once the user's scroll has settled with the finger up: the centred icon is then the one selected
    LaunchedEffect(rowState) {
        snapshotFlow { rowState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                userScroll = touched
                touched = false
            } else if (userScroll) {
                userScroll = false
                val centred = rowState.centredIndex()
                // Even when it is the selected one already: at the top of the list the first project counts as selected
                // while the summary above it is still showing, and selecting it scrolls the project itself to the top
                if (centred != null) onSelect(centred)
            }
        }
    }
    // The list scrolling to another project moves the row to its icon
    LaunchedEffect(selectedIndex, repos.size) {
        if (followList && !userScroll && !rowState.isScrollInProgress) rowState.animateScrollToItem(selectedIndex)
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Padding of half the free width lets the first and the last icon reach the centre
        val edge = (maxWidth - itemSize) / 2
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(repos, key = { _, repo -> repo }) { index, repo ->
                val name = repo.substringAfter('/')
                val count = prCounts.getOrElse(index) { 0 }
                Box(
                    modifier = Modifier
                        .size(itemSize)
                        .graphicsLayer {
                            // Grows as the icon nears the centre, so the one under selection is clear while the row is still moving
                            val info = rowState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val distance = if (item == null) 1f else kotlin.math.abs(item.offset + item.size / 2f - middle) / (item.size + gapPx)
                            val scale = 1.3f - 0.45f * distance.coerceIn(0f, 1f)
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Surface(
                        onClick = {
                            scope.launch { rowState.animateScrollToItem(index) }
                            onSelect(index)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Jump to $name" },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // The initial shows for repos without an icon, and under the icon while it loads
                            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AsyncImage(
                                model = repoIconUrl(repo),
                                contentDescription = null,
                                modifier = Modifier.padding(4.dp).fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 5.dp, y = (-5).dp)
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
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
    onCancelDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpen: (String) -> Unit,
    mainBuild: MainBuildActions,
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
                        verticalAlignment = Alignment.CenterVertically,
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
                        section.mainBuild?.let { item ->
                            StatusControl(
                                controlOf(item),
                                onDownload = { mainBuild.download(item.repo) },
                                onCancel = { mainBuild.cancelDownload(item.repo) },
                                onInstall = { mainBuild.install(item.repo) },
                                onOpen = { mainBuild.open(item.repo) },
                                onOpenRun = { item.runUrl?.let(uriHandler::openUri) },
                                onBuild = { mainBuild.build(item.repo) },
                            )
                        }
                    }
                    section.mainBuild?.let { item -> MainBuildErrors(item) }
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
                        PullRequestRow(item, onDownload, onCancelDownload, onInstall, onOpen, onOpenPullRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRequestRow(
    item: PullRequestItem,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenPullRequest: (PullRequestRef) -> Unit,
) {
    val pr = item.pr
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The whole upper section opens the page of the PR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open the page of this pull request") { onOpenPullRequest(PullRequestRef(pr.repo, pr.number)) }
                .padding(top = 14.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(pr.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "#${pr.number} · ${pr.author}" + if (pr.isDraft) " · draft" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            PullRequestStatus(item, onDownload = { onDownload(pr.key) }, onCancel = { onCancelDownload(pr.key) }, onInstall = { onInstall(pr.key) }, onOpen = { onOpen(pr.key) })
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

        (item.download as? DownloadState.Failed)?.let { failed ->
            Text(
                "Download failed: ${failed.message}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The errors of the main build, under the row of buttons. */
@Composable
private fun MainBuildErrors(item: MainBuildItem) {
    val downloadFailed = item.download as? DownloadState.Failed
    if (item.error == null && downloadFailed == null) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item.error?.let { error ->
            Text(error, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        downloadFailed?.let { failed ->
            Text(
                "Download failed: ${failed.message}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * What a status pill says of the APK it is about, on the two lines under the status: how old it is ([builtAtMillis],
 * the moment it was built, if known) and a [detail] in much smaller text, which is the timestamp of a PR's build
 * and how far the main build lags main.
 */
internal data class ApkDetail(val builtAtMillis: Long?, val detail: String?)

/** What a status says is only about an APK when there is one: not while it is only building, or after it failed. */
private fun Control.aboutApk(built: ApkDetail?): Control =
    if (built == null || text == "Building" || text == "Starting") this else copy(built = built)

/** The status pill of a PR, with a press on "Building" opening the run. */
@Composable
internal fun PullRequestStatus(
    item: PullRequestItem,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val pr = item.pr
    StatusControl(
        controlOf(item),
        onDownload = onDownload,
        onCancel = onCancel,
        onInstall = onInstall,
        onOpen = onOpen,
        onOpenRun = { pr.buildingRunUrl?.let(uriHandler::openUri) },
    )
}

/**
 * The three lines of a status pill, each always laid out and exactly one line tall, ellipsized if it does not fit:
 *
 * 1. **status**: what state it is in ("Ready", "Stale build", "No build").
 * 2. **age**: how old the APK is ("2 minutes old"); while it downloads, how far it is ("42%").
 * 3. **detail**: a fact about that APK in much smaller text: the timestamp of a PR's build, or how far the main
 *    build lags main ("3 commits behind", "Up to date with main").
 *
 * With no APK there is no age and no detail, and the status is centred in the height of the three lines.
 */
@Composable
private fun PillLines(
    status: String,
    statusColor: Color,
    apk: ApkDetail?,
    progressText: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val ageSize = 10.sp
    val detailSize = 8.sp
    val age = progressText ?: apk?.builtAtMillis?.let { ageText(it, rememberNowMillis(it)) }
    val detail = apk?.detail
    val statusSize = 13.sp
    val statusStyle = MaterialTheme.typography.labelMedium.copy(fontSize = statusSize, lineHeight = statusSize * 1.25f)
    val statusText = @Composable { text: String ->
        Text(text, style = statusStyle, color = statusColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val alone = age == null && detail == null
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            // Alone, the status is drawn over the lines below, which then only give the height
            statusText(if (alone) Blank else status)
            Text(
                age ?: Blank,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = ageSize, lineHeight = ageSize * 1.25f),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail ?: Blank,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = detailSize, lineHeight = detailSize * 1.25f),
                color = color.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (alone) statusText(status)
    }
}

/** A no-break space: a line of text with nothing to show still takes the height of a line. */
private const val Blank = "\u00A0"

/** The current time, brought up to date every second while [sinceMillis] is under a minute ago (it counts seconds), else every half minute. */
@Composable
private fun rememberNowMillis(sinceMillis: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (sinceMillis != null && now - sinceMillis < 60_000) 1_000 else 30_000)
        }
    }
    return now
}

/** A round outlined button holding just an icon. */
@Composable
internal fun CircleIconButton(icon: Int, description: String, onClick: () -> Unit, size: Dp = 40.dp) {
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

/** A small rounded tag: [color] on a faint tint of itself. */
@Composable
private fun Pill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** The status tag of a PR, with what presses on from here ([trailing]) at its end. */
internal data class Control(
    val text: String,
    val color: Color,
    val trailing: Trailing? = null,
    val built: ApkDetail? = null,
    /** A second action, next to [trailing]: with two actions only a press on an action's own icon runs it. */
    val secondary: Trailing.Action? = null,
    /** Shown on the second line in place of the age of the APK: how far a download is. */
    val progressText: String? = null,
)

internal enum class ActionKind { Download, Install, Open, OpenRun, Build }

/** What a press on a "Building" pill does: opens the run that builds the APK. */
private const val STALE_BUILD = "Stale build"

private val BuildAction = Trailing.Action(R.drawable.ic_build, "Build main", ActionKind.Build)

private val OpenRunAction = Trailing.Action(R.drawable.ic_open, "Open the build run", ActionKind.OpenRun)

internal sealed interface Trailing {
    /** A press on the tag downloads the APK, installs the one already downloaded, or opens the installed app ([kind]). */
    data class Action(val icon: Int, val description: String, val kind: ActionKind) : Trailing

    /** [fraction] is null while the size is not known. */
    data class Progress(val fraction: Float?) : Trailing
}

@Composable
internal fun controlOf(item: PullRequestItem): Control {
    val scheme = MaterialTheme.colorScheme
    val installed = item.install == InstallStatus.Installed
    val build = when (item.pr.apk) {
        null -> Control("No build", scheme.onSurfaceVariant)
        is ApkBuild.InProgress -> Control("Building", StatusColors.info, if (item.pr.buildingRunUrl != null) OpenRunAction else null)
        is ApkBuild.Failed -> Control("Build failed", scheme.error)
        is ApkBuild.Cancelled -> Control("Cancelled", scheme.onSurfaceVariant)
        is ApkBuild.Succeeded -> when (item.install) {
            InstallStatus.Installed -> Control("Installed", StatusColors.success)
            InstallStatus.DifferentBuild -> Control("Update ready", scheme.primary)
            InstallStatus.NotInstalled, InstallStatus.Unknown -> Control("Ready", scheme.primary)
        }
    }
    val target = item.pr.downloadableApk
    // The APK on offer lacks the latest commits, so what is on offer is the stale build, whatever the build says
    val isStale = target?.isStale == true
    val offered = if (isStale) Control(STALE_BUILD, StatusColors.warning) else build
    val pr = item.pr
    val millis = pr.apkBuiltAtMillis
    val text = (pr.apk as? ApkBuild.Succeeded)?.builtAt
    val built = if (millis != null || text != null) ApkDetail(millis, millis?.let(::formatTimestamp) ?: text) else null
    val control = transferControl(item.download, build, offered, installed, hasApk = target != null, isStale = isStale, error = scheme.error)
        .aboutApk(built)
    // A stale build is installed by the first action; the second opens the run that is replacing it
    return if (control.text == STALE_BUILD && control.trailing is Trailing.Action && pr.buildingRunUrl != null) {
        control.copy(secondary = OpenRunAction)
    } else {
        control
    }
}

/** The status of the main build; see docs/status-colors.md. */
@Composable
internal fun controlOf(item: MainBuildItem): Control {
    val scheme = MaterialTheme.colorScheme
    val installed = item.install == InstallStatus.Installed
    val behind = item.info.commitsBehind > 0
    val build = when (item.phase) {
        MainPhase.NoBuild -> Control("No build", scheme.onSurfaceVariant)
        MainPhase.Requested -> Control("Starting", StatusColors.info)
        MainPhase.Building -> Control("Building", StatusColors.info, if (item.runUrl != null) OpenRunAction else null)
        MainPhase.Failed -> Control("Build failed", scheme.error)
        MainPhase.Ready -> Control(if (item.install == InstallStatus.DifferentBuild) "Update ready" else "Ready", scheme.primary)
        MainPhase.Installed -> if (behind) Control("Behind main", scheme.onSurfaceVariant) else Control("Installed", StatusColors.success)
    }
    val building = item.phase == MainPhase.Requested || item.phase == MainPhase.Building
    // An APK that lacks the latest commits, or that a newer build is about to replace, is the stale build
    val isStale = item.info.apk != null && !installed && (building || behind)
    // Main is not built for every push, so a stale build of it is routine: quiet grey, not the amber of a PR's
    val offered = if (isStale) Control(STALE_BUILD, scheme.onSurfaceVariant) else build
    val built = item.info.apk?.let { ApkDetail(it.builtAtMillis, item.behindText) }
    val control = transferControl(item.download, build, offered, installed, hasApk = item.info.apk != null, isStale = isStale, error = scheme.error)
        .aboutApk(built)
    // A stale build is installed by the first action; the second opens the run that is replacing it
    if (control.text == STALE_BUILD && control.trailing is Trailing.Action && building && item.runUrl != null) {
        return control.copy(secondary = OpenRunAction)
    }
    // Building main is the primary action when there is no other, else the second one
    if (!item.offersBuild || !item.canBuild) return control
    return when (control.trailing) {
        null -> control.copy(trailing = BuildAction)
        is Trailing.Action -> control.copy(secondary = BuildAction)
        is Trailing.Progress -> control
    }
}

/** [build] with what a press does next, given the state of the download of the APK on offer ([offered]). */
private fun transferControl(
    download: DownloadState,
    build: Control,
    offered: Control,
    installed: Boolean,
    hasApk: Boolean,
    isStale: Boolean,
    error: Color,
): Control =
    when (download) {
        is DownloadState.Downloading -> {
            val total = download.total
            val done = if (total != null) "${(download.bytes * 100 / total).toInt()}%" else "${megabytes(download.bytes)} MB"
            Control("Downloading…", StatusColors.info, Trailing.Progress(total?.let { download.bytes.toFloat() / it }), progressText = done)
        }
        is DownloadState.Downloaded -> when {
            installed -> build.copy(trailing = Trailing.Action(R.drawable.ic_open, "Open app", ActionKind.Open))
            else -> offered.copy(
                text = if (isStale) offered.text else "Ready to install",
                trailing = Trailing.Action(R.drawable.ic_install, "Install", ActionKind.Install),
            )
        }
        is DownloadState.Failed -> Control(
            "Download failed",
            error,
            if (hasApk) Trailing.Action(R.drawable.ic_refresh, "Retry download", ActionKind.Download) else null,
        )
        DownloadState.Idle -> when {
            !hasApk -> build
            installed -> build.copy(trailing = Trailing.Action(R.drawable.ic_open, "Open app", ActionKind.Open))
            else -> offered.copy(trailing = Trailing.Action(R.drawable.ic_download, "Download", ActionKind.Download))
        }
    }

/**
 * The status of a PR as a coloured pill: the status, the age of the APK and a detail, then after a divider the icon of what a
 * press does next, or its progress. A press anywhere on the pill does that.
 */
@Composable
internal fun StatusControl(
    control: Control,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onOpenRun: () -> Unit = {},
    onBuild: () -> Unit = {},
) {
    val color = control.color
    // The pale status colours do not read as text on their own tint in light mode, so the text takes a darker shade
    val textColor = if (isSystemInDarkTheme()) color else lerp(color, Color.Black, 0.3f)
    val progress = control.trailing as? Trailing.Progress
    val actions = listOfNotNull(control.trailing as? Trailing.Action, control.secondary)
    // One action, or a download to cancel: a press anywhere on the pill does it. With two, only a press on an action's own icon does
    val wholePill = progress != null || actions.size == 1
    val run = { kind: ActionKind ->
        when (kind) {
            ActionKind.Download -> onDownload()
            ActionKind.Open -> onOpen()
            ActionKind.OpenRun -> onOpenRun()
            ActionKind.Build -> onBuild()
            ActionKind.Install -> onInstall()
        }
    }
    Surface(
        modifier = Modifier.semantics(mergeDescendants = wholePill) {
            if (wholePill) {
                contentDescription = if (progress != null) "${control.text} ${control.progressText.orEmpty()}. Cancel the download" else "${control.text}. ${actions.single().description}"
            }
        },
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 32.dp)
                .height(IntrinsicSize.Min)
                .then(
                    if (wholePill) Modifier.clickable { if (progress != null) onCancel() else run(actions.single().kind) } else Modifier,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Measured after the actions, so that a long line is ellipsized instead of pushing them out
            PillLines(
                status = control.text,
                statusColor = textColor,
                apk = control.built,
                progressText = control.progressText,
                color = textColor,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 12.dp, end = if (control.trailing == null) 12.dp else 10.dp, top = 4.dp, bottom = 4.dp),
            )
            if (progress != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(color.copy(alpha = 0.3f)))
                Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                    val indicator = Modifier.size(16.dp)
                    if (progress.fraction != null) {
                        CircularProgressIndicator(progress = { progress.fraction }, modifier = indicator, strokeWidth = 2.5.dp, color = textColor)
                    } else {
                        CircularProgressIndicator(modifier = indicator, strokeWidth = 2.5.dp, color = textColor)
                    }
                }
            }
            actions.forEach { action ->
                Box(Modifier.width(1.dp).fillMaxHeight().background(color.copy(alpha = 0.3f)))
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .fillMaxHeight()
                        .then(
                            if (wholePill) {
                                Modifier
                            } else {
                                Modifier
                                    .clickable(onClickLabel = action.description) { run(action.kind) }
                                    .semantics { contentDescription = action.description }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(action.icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = textColor)
                }
            }
        }
    }
}

internal fun megabytes(bytes: Long): String = "%.1f".format(bytes / 1_000_000.0)

/** For Android Studio while building the page: the empty page. */
@Preview
@Composable
private fun Preview() {
    AppTheme {
        HomePageContent(
            state = HomeUiState(isLoading = false),
            onRefresh = {},
            onToggleRepo = {},
            onDownload = {},
            onCancelDownload = {},
            onInstall = {},
            onOpen = {},
            mainBuild = MainBuildActions(build = {}, download = {}, cancelDownload = {}, install = {}, open = {}),
            onOpenPullRequest = {},
            onOpenSettings = {},
        )
    }
}
