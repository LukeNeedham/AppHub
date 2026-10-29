package com.lukeneedham.apphub.domain

import kotlinx.coroutines.flow.StateFlow

private val autoMergeTag = Regex("""\[\s*auto[\s_-]?merge\s*]""", RegexOption.IGNORE_CASE)

/** True when the title carries the auto merge tag, for example `[auto-merge] Fix the crash`. */
fun PullRequest.isAutoMerge(): Boolean = autoMergeTag.containsMatchIn(title)

/** The user's choices in Settings that change what Home lists. */
interface DisplaySettings {
    /** Whether PRs tagged auto merge are listed; off by default. */
    val showAutoMergePrs: StateFlow<Boolean>

    fun setShowAutoMergePrs(show: Boolean)
}
