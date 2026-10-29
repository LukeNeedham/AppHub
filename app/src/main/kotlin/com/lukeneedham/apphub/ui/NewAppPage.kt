package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NewAppPage(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = koinViewModel<NewAppViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NewAppPageContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onPrivateChange = viewModel::onPrivateChange,
        onCreate = viewModel::create,
        onDismissResult = viewModel::dismissResult,
        onCreationTokenInputChange = viewModel::onCreationTokenInputChange,
        onSaveCreationToken = viewModel::saveCreationToken,
        onRemoveCreationToken = viewModel::removeCreationToken,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
    )
}
