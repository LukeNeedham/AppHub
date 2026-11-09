package com.lukeneedham.apphub.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable

/** The bottom sheet around [PageDebugSheetContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageDebugSheet(info: PageDebugInfo, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PageDebugSheetContent(info = info, onCopy = onCopy)
    }
}
