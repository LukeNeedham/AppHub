package com.lukeneedham.apphub

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.rememberNavBackStack
import com.lukeneedham.apphub.ui.AppNavigation
import com.lukeneedham.apphub.ui.Debug
import com.lukeneedham.apphub.ui.Home
import com.lukeneedham.common.ui.DebugButton
import com.lukeneedham.common.ui.ProvideDebugSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideDebugSettings {
                AppTheme {
                    val backStack = rememberNavBackStack(Home)
                    Box(Modifier.fillMaxSize()) {
                        AppNavigation(backStack)
                        DebugButton(
                            onClick = { if (backStack.lastOrNull() != Debug) backStack.add(Debug) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
