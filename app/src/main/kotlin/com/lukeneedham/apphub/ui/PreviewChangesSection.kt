package com.lukeneedham.apphub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lukeneedham.apphub.domain.PreviewChange
import com.lukeneedham.apphub.domain.PreviewChangeKind
import com.lukeneedham.apphub.ui.theme.AppTheme

/**
 * The previews a PR changes compared with main, taken from the CI's "Preview changes" comment: for each, the image
 * on main next to the image of the PR. A press opens both large. Draws nothing when nothing changed.
 */
@Composable
internal fun PreviewChangesSection(changes: List<PreviewChange>, modifier: Modifier = Modifier) {
    if (changes.isEmpty()) return
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Preview changes", style = MaterialTheme.typography.titleMedium)
        changes.forEachIndexed { index, change ->
            PreviewChangeCard(change, onClick = { open = index })
        }
    }
    changes.getOrNull(open ?: -1)?.let { change ->
        PreviewChangeDialog(change, onDismiss = { open = null })
    }
}

@Composable
private fun PreviewChangeCard(change: PreviewChange, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "View ${change.title} large", onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(change.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    change.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary, maxLines = 1)
                    }
                }
                KindChip(change.kind)
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewImage("Main", change.beforeUrl, Modifier.weight(1f))
                PreviewImage("This PR", change.afterUrl, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewChangeDialog(change: PreviewChange, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(onClickLabel = "Close", onClick = onDismiss),
            color = AppTheme.colors.background,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(change.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    KindChip(change.kind)
                }
                // Side by side, as the page does: large enough to compare, and each can still be read
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewImage("Main", change.beforeUrl, Modifier.weight(1f))
                    PreviewImage("This PR", change.afterUrl, Modifier.weight(1f))
                }
            }
        }
    }
}

/** One side of a change: a caption over the image, or over a placeholder when that side has none (a new or removed preview). */
@Composable
private fun PreviewImage(caption: String, url: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
        val shape = RoundedCornerShape(8.dp)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "$caption preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .clip(shape)
                    .background(AppTheme.colors.background),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            // Takes the height left by the row, which the other side's image sets, so both sides are as tall
            Box(
                Modifier.fillMaxWidth().weight(1f).heightIn(min = 80.dp).clip(shape).background(AppTheme.colors.background),
                contentAlignment = Alignment.Center,
            ) {
                Text("None", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun KindChip(kind: PreviewChangeKind) {
    val (text, color) = when (kind) {
        PreviewChangeKind.New -> "New" to AppTheme.colors.installed
        PreviewChangeKind.Modified -> "Modified" to AppTheme.colors.stale
        PreviewChangeKind.Removed -> "Removed" to AppTheme.colors.failed
    }
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}
