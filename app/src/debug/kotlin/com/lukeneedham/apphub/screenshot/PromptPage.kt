package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.apphub.ui.PromptPageContent
import com.lukeneedham.apphub.ui.PromptUiState
import com.lukeneedham.apphub.ui.theme.AppTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-PromptPage.png. */
@Preview
@Composable
private fun PromptPageScreenshot() {
    val state = PromptUiState(
        repo = "LukeNeedham/FlagTutor",
        prompt = "Add a hint button to the quiz that removes two wrong answers",
        hasGithubToken = true,
        hasLlmKey = true,
        llmName = "Anthropic (Claude), claude-sonnet-5-5",
    )
    AppTheme {
        PromptPageContent(
            state = state,
            onPromptChange = {},
            onSend = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}
