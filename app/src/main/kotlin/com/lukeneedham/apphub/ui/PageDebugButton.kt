package com.lukeneedham.apphub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import com.lukeneedham.apphub.BuildConfig
import com.lukeneedham.apphub.R

/**
 * The last action of every page's toolbar: a bug icon that opens a bottom sheet with the name of the page on screen ([page], the
 * root composable, e.g. `HomePage`) and a button to copy it. Only in debug builds. It is also left out of previews, so the
 * README screenshots do not show it.
 */
@Composable
fun PageDebugButton(page: String) {
    if (!BuildConfig.DEBUG || LocalInspectionMode.current) return
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val route = LocalCurrentRoute.current
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_bug), contentDescription = "Debug: this page")
    }
    if (open) {
        PageDebugSheet(
            info = PageDebugInfo(page = page, route = route?.toString()),
            onCopy = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(page, text))
            },
            onDismiss = { open = false },
        )
    }
}
