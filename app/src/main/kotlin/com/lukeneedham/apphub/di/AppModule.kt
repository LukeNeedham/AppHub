package com.lukeneedham.apphub.di

import com.lukeneedham.apphub.data.AndroidApkInstaller
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.OkHttpApkDownloader
import com.lukeneedham.apphub.data.PrefsInstallTracker
import com.lukeneedham.apphub.data.PullRequestRepository
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.ui.HomeViewModel
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/** Whose public repositories are listed. */
private const val GITHUB_OWNER = "LukeNeedham"

val appModule = module {
    single {
        // The HTTP cache lets GitHub answer repeat requests with a 304, which is free of the rate limit
        OkHttpClient.Builder()
            .cache(Cache(File(androidContext().cacheDir, "http"), 10L * 1024 * 1024))
            .build()
    }
    single { GithubApi(get(), GITHUB_OWNER) }
    single { PullRequestRepository(get()) } bind PullRequestSource::class
    single<ApkDownloader> { OkHttpApkDownloader(get(), File(androidContext().cacheDir, "apks")) }
    single<ApkInstaller> { AndroidApkInstaller(androidContext()) }
    single<InstallTracker> { PrefsInstallTracker(androidContext()) }
    viewModelOf(::HomeViewModel)
}
