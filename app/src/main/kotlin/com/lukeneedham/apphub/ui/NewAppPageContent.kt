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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.domain.NewAppStack
import com.lukeneedham.apphub.domain.newAppPackage
import com.lukeneedham.apphub.domain.newClaudeSessionUrl
import com.lukeneedham.apphub.domain.repoUrl
import com.lukeneedham.apphub.ui.theme.AppTheme

private const val CLAUDE_APP_ACCESS_URL = "https://github.com/apps/claude/installations/select_target"
private const val VERCEL_NEW_PROJECT_URL = "https://vercel.com/new"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewAppPageContent(
    state: NewAppUiState,
    onNameChange: (String) -> Unit,
    onStackChange: (NewAppStack) -> Unit,
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
                title = { Text("New project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = { PageDebugButton("NewAppPage") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.signedIn) NeedsToken(onOpenSettings)
            if (!state.hasCreationToken) NeedsCreationToken(onOpenSettings)
            FormCard(state, onNameChange, onStackChange, onPrivateChange, onCreate)
            state.progress?.let { ProgressCard(it, state.stack, onDismissResult) }
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
            "Creating a project starts a workflow of the ci-workflows repo, which needs a token with the Actions permission " +
                "(read and write). Add one in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        OutlinedButton(onClick = onOpenSettings, shape = CircleShape) { Text("Open Settings") }
    }
}

@Composable
private fun NeedsCreationToken(onOpenSettings: () -> Unit) {
    NewAppCard {
        Text(
            "You need to set up the create new app token first.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onOpenSettings, shape = CircleShape) { Text("Open Settings") }
    }
}

@Composable
private fun FormCard(
    state: NewAppUiState,
    onNameChange: (String) -> Unit,
    onStackChange: (NewAppStack) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    NewAppCard {
        StackPicker(state.stack, enabled = !state.inProgress, onStackChange = onStackChange)
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project name") },
            singleLine = true,
            enabled = !state.inProgress,
            isError = state.nameError != null,
            supportingText = {
                // A website has no Android package, so there is only something to say for a wrong name or an app
                val hint = state.nameError ?: state.stack?.takeIf { it.hasAndroidPackage }?.let { "Package: ${newAppPackage(state.name)}" }
                if (state.name.isNotEmpty() && hint != null) Text(hint)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            shape = MaterialTheme.shapes.medium,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Private repository", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "AppHub lists public repos, so a private project does not show here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Switch(checked = state.isPrivate, onCheckedChange = onPrivateChange, enabled = !state.inProgress)
        }
        Button(onClick = onCreate, enabled = state.canCreate, shape = CircleShape) { Text("Create project") }
    }
}

/** What the project is built with. Nothing is chosen at first: the user picks, so the workflow never guesses (there is no default stack). */
@Composable
private fun StackPicker(selected: NewAppStack?, enabled: Boolean, onStackChange: (NewAppStack) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Built with", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary)
        NewAppStack.entries.forEach { stack ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = stack == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onStackChange(stack) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = stack == selected, onClick = null, enabled = enabled)
                Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(stack.title, style = MaterialTheme.typography.bodyMedium)
                    Text(stack.description, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(progress: NewAppProgress, stack: NewAppStack?, onDismissResult: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    NewAppCard {
        when (progress) {
            NewAppProgress.CreatingRepo -> Working("Creating the repo…")
            NewAppProgress.Starting -> Working("Repo made. Starting its setup…")
            NewAppProgress.Waiting -> Working("Started. Waiting for the run to appear…")
            is NewAppProgress.Running -> {
                Working("Creating the project…")
                TextButton(onClick = { uriHandler.openUri(progress.runUrl) }) { Text("Watch the run") }
            }
            is NewAppProgress.Done -> {
                Text("Created ${progress.repo}", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.installed)
                Text(
                    doneText(progress.repo, stack),
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
                if (stack == NewAppStack.NextJs) {
                    TextButton(onClick = { uriHandler.openUri(VERCEL_NEW_PROJECT_URL) }) { Text("Vercel") }
                }
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

/** What is left to do once [repo] is created, which depends on its [stack]. */
private fun doneText(repo: String, stack: NewAppStack?): String {
    val claudeAccess = "Give the Claude GitHub App access to the repo, unless its installation covers all repositories, then start a session on it."
    return when (stack) {
        NewAppStack.NextJs ->
            "The run's summary says whether the site was set up on Vercel. If it says that was skipped, connect the repo on Vercel " +
                "and turn Deployment Protection off for previews. $claudeAccess"
        NewAppStack.Kmp, null -> "The package is ${newAppPackage(repo.substringAfter('/'))}. $claudeAccess"
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
            state = NewAppUiState(signedIn = true, hasCreationToken = true),
            onNameChange = {},
            onStackChange = {},
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
            state = NewAppUiState(signedIn = false, hasCreationToken = false, name = "Lyric Tutor"),
            onNameChange = {},
            onStackChange = {},
            onPrivateChange = {},
            onCreate = {},
            onDismissResult = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}

/** For Android Studio: a website is chosen. */
@Preview
@Composable
private fun PreviewWebsite() {
    AppTheme {
        NewAppPageContent(
            state = NewAppUiState(signedIn = true, hasCreationToken = true, name = "MySite", stack = NewAppStack.NextJs),
            onNameChange = {},
            onStackChange = {},
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
                hasCreationToken = true,
                name = "LyricTutor",
                stack = NewAppStack.Kmp,
                progress = NewAppProgress.Done("LukeNeedham/LyricTutor", "https://github.com/LukeNeedham/ci-workflows/actions/runs/1"),
            ),
            onNameChange = {},
            onStackChange = {},
            onPrivateChange = {},
            onCreate = {},
            onDismissResult = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}
