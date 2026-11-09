package com.lukeneedham.apphub.ui

import androidx.compose.runtime.compositionLocalOf

/** The route of the page being shown, provided by `Router`; null where no route is provided (a preview). The debug sheet reads it. */
val LocalCurrentRoute = compositionLocalOf<Route?> { null }
