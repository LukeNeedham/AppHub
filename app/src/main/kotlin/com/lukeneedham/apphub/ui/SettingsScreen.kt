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

private const val NEW_TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

/** [onOpenDebug] is null when debugging is off (release builds), which hides the entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    onOpenDebug: (() -> Unit)?,
) {
    val background = MaterialTheme.colorScheme.background
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
                "Signed in with a token ending …${state.tokenHint}",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.rateLimit?.let {
                Text(
                    "${it.remaining} of ${it.limit} requests left this hour",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.error != null) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = onRemove, shape = CircleShape) { Text("Remove token") }
        } else {
            Text(
                "Without a token GitHub allows 60 requests an hour. With one it allows 5,000. " +
                    "Create a fine-grained token with \"Public repositories (read-only)\" access and no other permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.rotate(-90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
