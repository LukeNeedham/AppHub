package com.lukeneedham.apphub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lukeneedham.apphub.domain.DeepLink
import com.lukeneedham.apphub.domain.PullRequestRef
import com.lukeneedham.apphub.ui.Root

class MainActivity : ComponentActivity() {

    /** A pull request link that has been opened and not yet shown. */
    private var pendingLink by mutableStateOf<PullRequestRef?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation or process restart the saved back stack already has the page
        if (savedInstanceState == null) pendingLink = linkOf(intent)
        setContent { Root(pendingLink = pendingLink, onLinkShown = { pendingLink = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkOf(intent)?.let { pendingLink = it }
    }

    private fun linkOf(intent: Intent): PullRequestRef? =
        if (intent.action == Intent.ACTION_VIEW) DeepLink.parse(intent.dataString) else null
}
