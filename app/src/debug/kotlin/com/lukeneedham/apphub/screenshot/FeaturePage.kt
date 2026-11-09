package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureKind
import com.lukeneedham.apphub.domain.FeatureStatus
import com.lukeneedham.apphub.ui.FeaturePageContent
import com.lukeneedham.apphub.ui.FeatureUiState
import com.lukeneedham.apphub.ui.theme.AppTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-FeaturePage.png. */
@Preview
@Composable
private fun FeaturePageScreenshot() {
    val state = FeatureUiState(
        feature = Feature(
            id = "f1",
            kind = FeatureKind.Feature,
            repo = "LukeNeedham/FlagTutor",
            prompt = "Add a hint button to the quiz that removes two wrong answers",
            summary = "Add a hint button to the quiz that removes two wrong answers",
            status = FeatureStatus.NeedsAnswer,
            question = "Should using a hint lower the score?",
            log = listOf(
                "Started with Anthropic (Claude) (claude-sonnet-5-5).",
                "Read CLAUDE.md",
                "Listed the repo root",
                "Read app/src/main/kotlin/com/lukeneedham/flagtutor/ui/QuizPageContent.kt",
            ),
            createdAtMillis = 0,
        ),
        answer = "Yes, by one point",
    )
    AppTheme {
        FeaturePageContent(
            state = state,
            onAnswerChange = {},
            onSendAnswer = {},
            onStop = {},
            onDismiss = {},
            onOpenPullRequest = { _, _ -> },
            onBack = {},
        )
    }
}
