package com.lukeneedham.apphub.data

import android.content.Context
import com.lukeneedham.apphub.domain.DisplaySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrefsDisplaySettings(context: Context) : DisplaySettings {

    private val prefs = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    private val _showAutoMergePrs = MutableStateFlow(prefs.getBoolean(KEY_SHOW_AUTO_MERGE, false))

    override val showAutoMergePrs: StateFlow<Boolean> = _showAutoMergePrs.asStateFlow()

    override fun setShowAutoMergePrs(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_AUTO_MERGE, show).apply()
        _showAutoMergePrs.value = show
    }

    private companion object {
        const val KEY_SHOW_AUTO_MERGE = "show_auto_merge_prs"
    }
}
