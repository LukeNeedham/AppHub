package com.lukeneedham.apphub

import android.app.Application
import com.lukeneedham.apphub.data.FeatureKeepAlive
import com.lukeneedham.apphub.di.KoinModules
import com.lukeneedham.apphub.domain.FeatureManager
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.DebugKitConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))
        val koin = startKoin {
            androidContext(this@App)
            modules(KoinModules.modules)
        }.koin
        // A session that was running when the app was closed is over; the notification keeps sessions alive from now on
        koin.get<FeatureManager>().failInterrupted()
        koin.get<FeatureKeepAlive>().start()
    }
}
