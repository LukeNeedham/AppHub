package com.lukeneedham.apphub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.R
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lukeneedham.apphub.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

private const val NEW_TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPageContent(
    state: SettingsUiState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onRepoCreationTokenInputChange: (String) -> Unit,
    onSaveRepoCreationToken: () -> Unit,
    onRemoveRepoCreationToken: () -> Unit,
    onShowAutoMergePrsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenDebug: (() -> Unit)?,
) {
    val background = AppTheme.colors.background
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            GithubCard(state, onInputChange, onSave, onRemove)
            RepoCreationTokenCard(state, onRepoCreationTokenInputChange, onSaveRepoCreationToken, onRemoveRepoCreationToken)
            AutoMergeRow(state.showAutoMergePrs, onShowAutoMergePrsChange)
            if (onOpenDebug != null) {
                SettingsRow(title = "Debug", subtitle = "Logs, crashes and animation speed", onClick = onOpenDebug)
            }
        }
    }
}

@Composable
private fun GithubCard(
    state: SettingsUiState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Card {
        Text("GitHub", style = MaterialTheme.typography.titleMedium)
        if (state.signedIn) {
            Text(
                "Signed in with a GitHub token",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.rateLimit?.let {
                Text(
                    "${it.remaining} of ${it.limit} requests left this hour",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            if (state.error != null) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.failed)
            }
            OutlinedButton(onClick = onRemove, shape = CircleShape) { Text("Remove token") }
        } else {
            Text(
                "Without a token GitHub allows 60 requests an hour. With one it allows 5,000. " +
                    "A token is also what lets AppHub build main on demand: a project's \"Latest main\" row, " +
                    "with its Build button, only shows when there is one, and what lets it create a new app (the plus on the " +
                    "home page). For those the token needs the Actions permission (read and write); " +
                    "for the rate limit alone it needs no permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
            TokenSteps()
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Personal access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = state.error != null,
                supportingText = if (state.error != null) {
                    { Text(state.error) }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSave,
                    enabled = state.input.isNotBlank() && !state.isChecking,
                    shape = CircleShape,
                ) { Text(if (state.isChecking) "Checking…" else "Save token") }
                TextButton(onClick = { uriHandler.openUri(NEW_TOKEN_URL) }) { Text("Create a token") }
            }
        }
    }
}

/** The token that creates the repos of the New app page: its own, so that nothing else can. Entered once, kept like the GitHub token. */
@Composable
private fun RepoCreationTokenCard(
    state: SettingsUiState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Card {
        Text("Create new app token", style = MaterialTheme.typography.titleMedium)
        if (state.hasRepoCreationToken) {
            Text(
                "Saved on this device, and only used to create the repos of new apps.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onRemove, shape = CircleShape) { Text("Remove token") }
        } else {
            Text(
                "The New app page makes a GitHub repo with a token of its own, apart from the one above, so that no secret of " +
                    "ci-workflows can create repos. Make a fine-grained token for all repositories with the permission " +
                    "Repository creation (write), or Administration (write) if that is not offered. A classic token with the " +
                    "scope repo works too. Set the longest expiration: after it, create a new token and replace this one.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
            OutlinedTextField(
                value = state.repoCreationTokenInput,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token to create repos") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave, enabled = state.repoCreationTokenInput.isNotBlank(), shape = CircleShape) { Text("Save token") }
                TextButton(onClick = { uriHandler.openUri(NEW_TOKEN_URL) }) { Text("Create a token") }
            }
        }
    }
}

/** How to make a token on GitHub's fine-grained token page, which "Create a token" opens. */
@Composable
private fun TokenSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("How to create a token", style = MaterialTheme.typography.labelLarge)
        listOf(
            "Tap \"Create a token\" below. It opens GitHub: sign in if it asks.",
            "Token name: anything, for example AppHub.",
            "Expiration: pick a date (the longest is 1 year). You will need a new token after it.",
            "Repository access: choose \"All repositories\" to build main (\"Public repositories\" is enough for the rate limit alone).",
            "To build main: open \"Repository permissions\", find \"Actions\" and set it to \"Read and write\". Leave the rest unset.",
            "Scroll to the bottom and tap \"Generate token\".",
            "Copy the token (it starts with github_pat_). GitHub shows it only once.",
            "Come back here, paste it in the field and tap \"Save token\".",
        ).forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.accent)
                Text(step, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
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

@Composable
private fun AutoMergeRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show auto merge PRs", style = MaterialTheme.typography.titleMedium)
                Text(
                    "PRs with [auto-merge] in their title. When off, they are hidden from the home page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.rotate(-90f),
                tint = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** For Android Studio while building the page: signed out. */
@Preview
@Composable
private fun Preview() {
    AppTheme {
        SettingsPageContent(
            state = SettingsUiState(),
            onInputChange = {},
            onSave = {},
            onRemove = {},
            onRepoCreationTokenInputChange = {},
            onSaveRepoCreationToken = {},
            onRemoveRepoCreationToken = {},
            onShowAutoMergePrsChange = {},
            onBack = {},
            onOpenDebug = {},
        )
    }
}
