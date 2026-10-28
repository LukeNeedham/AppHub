package com.lukeneedham.apphub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.domain.newAppPackage
import com.lukeneedham.apphub.domain.newClaudeSessionUrl
import com.lukeneedham.apphub.domain.repoUrl
import com.lukeneedham.apphub.ui.theme.AppTheme

private const val CLAUDE_APP_ACCESS_URL = "https://github.com/apps/claude/installations/select_target"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewAppPageContent(
    state: NewAppUiState,
    onNameChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onDismissResult: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val background = AppTheme.colors.background
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("New app") },
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
            if (!state.signedIn) NeedsToken(onOpenSettings)
            FormCard(state, onNameChange, onPrivateChange, onCreate)
            state.progress?.let { ProgressCard(it, onDismissResult) }
            if (state.error != null) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.failed)
            }
        }
    }
}

@Composable
private fun NeedsToken(onOpenSettings: () -> Unit) {
    NewAppCard {
        Text("A GitHub token is needed", style = MaterialTheme.typography.titleMedium)
        Text(
            "Creating an app starts a workflow of the ci-workflows repo, which needs a token with the Actions permission " +
                "(read and write). Add one in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        OutlinedButton(onClick = onOpenSettings, shape = CircleShape) { Text("Open Settings") }
    }
}

@Composable
private fun FormCard(
    state: NewAppUiState,
    onNameChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    NewAppCard {
        Text(
            "Creates the GitHub repo with its first commit, adds the app to the list of ci-workflows and sets it up. " +
                "It takes a minute or two.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("App name") },
            singleLine = true,
            enabled = !state.inProgress,
            isError = state.nameError != null,
            supportingText = {
                Text(
                    state.nameError ?: if (state.name.isEmpty()) "For example LyricTutor" else "Package: ${newAppPackage(state.name)}",
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            shape = MaterialTheme.shapes.medium,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Private repository", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "AppHub lists public repos, so a private app does not show here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Switch(checked = state.isPrivate, onCheckedChange = onPrivateChange, enabled = !state.inProgress)
        }
        Button(onClick = onCreate, enabled = state.canCreate, shape = CircleShape) { Text("Create app") }
    }
}

@Composable
private fun ProgressCard(progress: NewAppProgress, onDismissResult: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    NewAppCard {
        when (progress) {
            NewAppProgress.Starting -> Working("Asking GitHub to start…")
            NewAppProgress.Waiting -> Working("Started. Waiting for the run to appear…")
            is NewAppProgress.Running -> {
                Working("Creating the app…")
                TextButton(onClick = { uriHandler.openUri(progress.runUrl) }) { Text("Watch the run") }
            }
            is NewAppProgress.Done -> {
                Text("Created ${progress.repo}", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.installed)
                Text(
                    "The package is ${newAppPackage(progress.repo.substringAfter('/'))}. Give the Claude GitHub App access to the repo, " +
                        "unless its installation covers all repositories, then start a session on it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { uriHandler.openUri(repoUrl(progress.repo)) }, shape = CircleShape) { Text("Open repo") }
                    OutlinedButton(onClick = { uriHandler.openUri(newClaudeSessionUrl(progress.repo)) }, shape = CircleShape) {
                        Text("Claude")
                    }
                }
                TextButton(onClick = { uriHandler.openUri(CLAUDE_APP_ACCESS_URL) }) { Text("Claude app access") }
                TextButton(onClick = onDismissResult) { Text("Create another") }
            }
            is NewAppProgress.Failed -> {
                Text("The run failed", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.failed)
                Text(
                    "Its log says what stopped. The steps can be repeated: a repo that exists is carried on with by a new run " +
                        "of the workflow with \"resume\" on, from the Actions tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { uriHandler.openUri(progress.runUrl) }, shape = CircleShape) { Text("Open the run") }
                    TextButton(onClick = onDismissResult) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun Working(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppTheme.colors.building)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NewAppCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** For Android Studio while building the page: signed in, nothing typed. */
@Preview
@Composable
private fun Preview() {
    AppTheme {
        NewAppPageContent(
            state = NewAppUiState(signedIn = true),
            onNameChange = {},
            onPrivateChange = {},
            onCreate = {},
            onDismissResult = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}

/** For Android Studio: signed out, so the token card shows. */
@Preview
@Composable
private fun PreviewSignedOut() {
    AppTheme {
        NewAppPageContent(
            state = NewAppUiState(signedIn = false, name = "Lyric Tutor"),
            onNameChange = {},
            onPrivateChange = {},
            onCreate = {},
            onDismissResult = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}

/** For Android Studio: the app is created. */
@Preview
@Composable
private fun PreviewDone() {
    AppTheme {
        NewAppPageContent(
            state = NewAppUiState(
                signedIn = true,
                name = "LyricTutor",
                progress = NewAppProgress.Done("LukeNeedham/LyricTutor", "https://github.com/LukeNeedham/ci-workflows/actions/runs/1"),
            ),
            onNameChange = {},
            onPrivateChange = {},
            onCreate = {},
            onDismissResult = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}
