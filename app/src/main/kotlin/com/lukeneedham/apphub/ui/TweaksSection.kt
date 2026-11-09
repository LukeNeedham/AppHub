package com.lukeneedham.apphub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureKind
import com.lukeneedham.apphub.domain.FeatureStatus
import com.lukeneedham.apphub.ui.theme.AppTheme

/**
 * The tweaks of a pull request that a feature made: the Tweak button, and a row per tweak with a summary of what was asked and a status
 * icon (a spinner while it runs, a tick when it is completed). A new tweak waits while [blocked].
 */
@Composable
internal fun TweaksSection(
    tweaks: List<Feature>,
    blocked: Boolean,
    onTweak: () -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tweaks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onTweak, enabled = !blocked, shape = CircleShape) { Text("Tweak") }
        }
        if (blocked) {
            Text(
                "A session is working on this pull request. You can tweak it again when that session ends.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        if (tweaks.isEmpty()) {
            Text(
                "No tweaks yet. Describe a change and a new session pushes it to this pull request.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        } else {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = AppTheme.colors.card,
                border = BorderStroke(1.dp, AppTheme.colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    tweaks.forEachIndexed { index, tweak ->
                        if (index > 0) HorizontalDivider(color = AppTheme.colors.border)
                        FeatureRow(tweak, onClick = { onOpenFeature(tweak.id) })
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    fun tweak(id: String, summary: String, status: FeatureStatus) = Feature(
        id = id,
        kind = FeatureKind.Tweak,
        repo = "LukeNeedham/FlagTutor",
        prompt = summary,
        summary = summary,
        status = status,
        prNumber = 12,
        createdAtMillis = 0,
    )
    AppTheme {
        TweaksSection(
            tweaks = listOf(
                tweak("2", "Make the answer buttons rounder", FeatureStatus.Working),
                tweak("1", "Show the score at the end", FeatureStatus.ReadyForReview),
            ),
            blocked = true,
            onTweak = {},
            onOpenFeature = {},
        )
    }
}
