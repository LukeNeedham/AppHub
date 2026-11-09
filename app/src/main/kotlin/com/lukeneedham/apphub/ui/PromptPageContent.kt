package com.lukeneedham.apphub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptPageContent(
    state: PromptUiState,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val background = AppTheme.colors.background
    val appName = state.repo.substringAfter('/')
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isTweak) "Tweak #${state.pullRequestNumber}" else "New feature") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.hasGithubToken) NeedsSetup("A GitHub token is needed", "The session pushes to $appName with it. Add one in Settings.", onOpenSettings)
            if (!state.hasLlmKey) NeedsSetup("An LLM needs setting up", "Choose a provider and add its key in Settings. A custom provider also needs its address and a model name.", onOpenSettings)
            Card {
                Text(
                    if (state.isTweak) {
                        "Describe the changes you want to this pull request of $appName."
                    } else {
                        "Describe the feature you want in $appName."
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (state.isTweak) {
                        "A new session works on the branch of the pull request, pushes to it, and ends by itself when the CI is green."
                    } else {
                        "A new session builds it, opens a pull request and fixes the CI until it is green, then ends by itself."
                    } + " You get one prompt: after you send it you cannot type again, unless the session asks you a question.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt") },
                    minLines = 6,
                    // Closed for good once sent
                    enabled = !state.sent,
                    shape = MaterialTheme.shapes.medium,
                    supportingText = if (state.llmName.isNotEmpty()) ({ Text("Runs on ${state.llmName}") }) else null,
                )
                Button(onClick = onSend, enabled = state.canSend, shape = CircleShape) {
                    Text(if (state.sent) "Sent" else if (state.isTweak) "Send tweak" else "Start feature")
                }
            }
            if (state.error != null) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.failed)
            }
        }
    }
}

@Composable
private fun NeedsSetup(title: String, text: String, onOpenSettings: () -> Unit) {
    Card {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        OutlinedButton(onClick = onOpenSettings, shape = CircleShape) { Text("Open Settings") }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** For Android Studio while building the page: a new feature, ready to be typed. */
@Preview
@Composable
private fun Preview() {
    AppTheme {
        PromptPageContent(
            state = PromptUiState(
                repo = "LukeNeedham/FlagTutor",
                prompt = "Add a quiz that shows a flag and asks for the country",
                hasGithubToken = true,
                hasLlmKey = true,
                llmName = "Anthropic (Claude), claude-sonnet-5-5",
            ),
            onPromptChange = {},
            onSend = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}

/** For Android Studio while building the page: the prompt is sent and the input is closed. */
@Preview
@Composable
private fun PreviewSent() {
    AppTheme {
        PromptPageContent(
            state = PromptUiState(
                repo = "LukeNeedham/FlagTutor",
                pullRequestNumber = 12,
                prompt = "Make the buttons rounder",
                hasGithubToken = true,
                hasLlmKey = true,
                sent = true,
            ),
            onPromptChange = {},
            onSend = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}
