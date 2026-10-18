package com.lukeneedham.apphub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation3.runtime.rememberNavBackStack
import com.lukeneedham.apphub.ui.AppNavigation
import com.lukeneedham.apphub.ui.Home
import com.lukeneedham.apphub.ui.theme.AppTheme
import com.lukeneedham.common.ui.ProvideDebugSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProvideDebugSettings {
                AppTheme {
                    // The debug page is reached from Settings, so there is no floating DebugButton
                    AppNavigation(rememberNavBackStack(Home))
                }
            }
        }
    }
}
