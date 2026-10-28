package com.lukeneedham.apphub.ui

import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.domain.newAppNameError

data class NewAppUiState(
    /** Whether there is a GitHub token: without one the workflow cannot be started. */
    val signedIn: Boolean = false,
    val name: String = "",
    val isPrivate: Boolean = false,
    /** Null before anything was started, and after a start that was refused (see [error]). */
    val progress: NewAppProgress? = null,
    val error: String? = null,
) {
    /** What is wrong with [name]; nothing is said before anything was typed. */
    val nameError: String? get() = if (name.isEmpty()) null else newAppNameError(name)

    /** Started, and not ended yet. */
    val inProgress: Boolean
        get() = progress != null && progress !is NewAppProgress.Done && progress !is NewAppProgress.Failed

    val canCreate: Boolean get() = signedIn && name.isNotEmpty() && nameError == null && !inProgress
}
