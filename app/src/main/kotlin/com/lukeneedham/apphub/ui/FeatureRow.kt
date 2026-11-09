package com.lukeneedham.apphub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureKind
import com.lukeneedham.apphub.domain.FeatureStatus
import com.lukeneedham.apphub.ui.theme.AppTheme

/** The words for where a session is. A tweak that is done is "Completed"; a feature is "Ready for review". */
internal fun Feature.statusLabel(): String =
    when (status) {
        FeatureStatus.Working -> "In progress"
        FeatureStatus.NeedsAnswer -> "Needs your answer"
        FeatureStatus.WaitingForCi -> "Waiting for CI"
        FeatureStatus.FixingCi -> "Fixing CI"
        FeatureStatus.ReadyForReview -> if (kind == FeatureKind.Tweak) "Completed" else "Ready for review"
        FeatureStatus.Failed -> "Failed"
    }

/** The colour of a status, by what it asks of the user (docs/status-colors.md): blue wait, purple answer, green done, red broken. */
@Composable
internal fun FeatureStatus.color(): Color =
    when (this) {
        FeatureStatus.Working, FeatureStatus.WaitingForCi, FeatureStatus.FixingCi -> AppTheme.colors.building
        FeatureStatus.NeedsAnswer -> AppTheme.colors.action
        FeatureStatus.ReadyForReview -> AppTheme.colors.installed
        FeatureStatus.Failed -> AppTheme.colors.failed
    }

/** The icon of a status: a spinner while a session runs, a question mark when it waits for the user, a tick when done, a cross when it failed. */
@Composable
internal fun FeatureStatusIcon(feature: Feature, size: Dp = 24.dp) {
    val color = feature.status.color()
    Box(
        modifier = Modifier.size(size).semantics { contentDescription = feature.statusLabel() },
        contentAlignment = Alignment.Center,
    ) {
        when (feature.status) {
            FeatureStatus.Working, FeatureStatus.WaitingForCi, FeatureStatus.FixingCi ->
                CircularProgressIndicator(modifier = Modifier.size(size - 4.dp), color = color, strokeWidth = 2.dp)

            FeatureStatus.NeedsAnswer -> Box(
                modifier = Modifier.size(size).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            FeatureStatus.ReadyForReview -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(size), tint = color)

            FeatureStatus.Failed -> Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(size), tint = color)
        }
    }
}

/** One session as a row: its status icon, a summary of the work, and where it is. */
@Composable
internal fun FeatureRow(feature: Feature, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureStatusIcon(feature)
        Column(Modifier.weight(1f)) {
            Text(
                feature.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                feature.statusLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = feature.status.color(),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    AppTheme {
        Column {
            FeatureStatus.entries.forEach { status ->
                FeatureRow(
                    Feature(
                        id = status.name,
                        kind = FeatureKind.Tweak,
                        repo = "LukeNeedham/FlagTutor",
                        prompt = "Make the buttons rounder",
                        summary = "Make the buttons rounder",
                        status = status,
                        prNumber = 12,
                        createdAtMillis = 0,
                    ),
                    onClick = {},
                )
            }
        }
    }
}
