package com.lukeneedham.apphub

import android.app.Application
import com.lukeneedham.apphub.di.appModule
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.DebugKitConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }
}
