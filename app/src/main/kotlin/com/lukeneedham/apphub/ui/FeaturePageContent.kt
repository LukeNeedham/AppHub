package com.lukeneedham.apphub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureKind
import com.lukeneedham.apphub.domain.FeatureStatus
import com.lukeneedham.apphub.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeaturePageContent(
    state: FeatureUiState,
    onAnswerChange: (String) -> Unit,
    onSendAnswer: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPullRequest: (repo: String, number: Int) -> Unit,
    onBack: () -> Unit,
) {
    val background = AppTheme.colors.background
    val feature = state.feature
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(if (feature?.isTweak == true) "Tweak" else "Feature") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = { PageDebugButton("FeaturePage") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (feature == null) {
                Text("This session is not here any more.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                return@Column
            }
            Header(feature)
            if (feature.status == FeatureStatus.NeedsAnswer) {
                QuestionCard(feature.question.orEmpty(), state.answer, onAnswerChange, onSendAnswer)
            }
            if (feature.status == FeatureStatus.Failed && feature.error != null) {
                Surface(shape = MaterialTheme.shapes.large, color = AppTheme.colors.failed.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(feature.error, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.failed)
                    }
                }
            }
            Actions(feature, onStop, onDismiss, onOpenPullRequest)
            Card {
                Text("Prompt", style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(feature.prompt, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "The prompt cannot be changed: a session takes one, and only asks you something if it is blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            if (feature.log.isNotEmpty()) {
                Card {
                    Text("Activity", style = MaterialTheme.typography.titleMedium)
                    // One container for all the lines, so a selection can run across them and be copied whole
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            feature.log.asReversed().forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(feature: Feature) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(feature.summary, style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureStatusIcon(feature)
            Text(
                feature.statusLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = feature.status.color(),
            )
        }
        Text(
            feature.repo.substringAfter('/') + (feature.prNumber?.let { " · #$it" } ?: ""),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** The one place a session takes typing after its prompt. */
@Composable
private fun QuestionCard(question: String, answer: String, onAnswerChange: (String) -> Unit, onSend: () -> Unit) {
    Card {
        Text("The session is blocked and asks:", style = MaterialTheme.typography.titleMedium)
        SelectionContainer { Text(question, style = MaterialTheme.typography.bodyMedium) }
        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your answer") },
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
        )
        Button(onClick = onSend, enabled = answer.isNotBlank(), shape = CircleShape) { Text("Send answer") }
    }
}

@Composable
private fun Actions(
    feature: Feature,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPullRequest: (String, Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val number = feature.prNumber
        if (number != null) {
            Button(onClick = { onOpenPullRequest(feature.repo, number) }, shape = CircleShape) { Text("Open pull request") }
        }
        if (feature.status.isActive) {
            OutlinedButton(onClick = onStop, shape = CircleShape) { Text("Stop") }
        } else {
            OutlinedButton(onClick = onDismiss, shape = CircleShape) { Text("Remove") }
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

private fun previewFeature(status: FeatureStatus, kind: FeatureKind = FeatureKind.Feature) = Feature(
    id = "1",
    kind = kind,
    repo = "LukeNeedham/FlagTutor",
    prompt = "Add a quiz that shows a flag and asks for the country",
    summary = "Add a flag quiz",
    status = status,
    prNumber = 12,
    log = listOf("Started with Anthropic (Claude) (claude-sonnet-5-5).", "Read CLAUDE.md", "Opened pull request #12."),
    createdAtMillis = 0,
)

/** For Android Studio while building the page: waiting for the CI. */
@Preview
@Composable
private fun Preview() {
    AppTheme {
        FeaturePageContent(FeatureUiState(previewFeature(FeatureStatus.WaitingForCi)), {}, {}, {}, {}, { _, _ -> }, {})
    }
}

/** For Android Studio while building the page: the session asks something. */
@Preview
@Composable
private fun PreviewQuestion() {
    AppTheme {
        FeaturePageContent(
            FeatureUiState(previewFeature(FeatureStatus.NeedsAnswer).copy(question = "Should the quiz have a timer?"), answer = "No"),
            {}, {}, {}, {}, { _, _ -> }, {},
        )
    }
}
