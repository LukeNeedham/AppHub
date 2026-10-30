package com.lukeneedham.apphub.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The scope of work that outlives every screen, so a download carries on when its page is closed. */
object ApplicationScope {
    fun create(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
