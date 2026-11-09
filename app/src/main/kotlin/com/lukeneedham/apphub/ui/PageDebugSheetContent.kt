package com.lukeneedham.apphub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.apphub.R
import com.lukeneedham.apphub.ui.theme.AppTheme

/** What the debug sheet shows about the page on screen. [page] is the name of its root composable. */
data class PageDebugInfo(
    val page: String,
    /** `Route.X(...)` of the page, or null when the page was not opened through a route. */
    val route: String?,
) {
    /** The composables and classes of the page, all named after it: `HomePage` has `HomePageContent` and `HomeViewModel`. */
    val content: String get() = "${page}Content"
    val viewModel: String get() = page.removeSuffix("Page") + "ViewModel"
}

/** The rows of the debug sheet, each with a button that copies its value. Stateless: [onCopy] gets the text to copy. */
@Composable
internal fun PageDebugSheetContent(info: PageDebugInfo, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Debug: this page", style = MaterialTheme.typography.titleMedium)
        Text(
            "Copy the page name into a feature prompt to say which page to change.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        DebugRow("Page", info.page, onCopy)
        DebugRow("Content", info.content, onCopy)
        DebugRow("ViewModel", info.viewModel, onCopy)
        info.route?.let { DebugRow("Route", it, onCopy) }
    }
}

@Composable
private fun DebugRow(label: String, value: String, onCopy: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = { onCopy(value) }) {
            Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy $label", modifier = Modifier.size(20.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    AppTheme {
        PageDebugSheetContent(info = PageDebugInfo(page = "PullRequestPage", route = "PullRequest(repo=o/r, number=12, openedAt=0)"), onCopy = {})
    }
}
