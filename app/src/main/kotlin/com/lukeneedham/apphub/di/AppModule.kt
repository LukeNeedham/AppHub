package com.lukeneedham.apphub.di

import com.lukeneedham.apphub.data.AndroidApkInstaller
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.KeystoreTokenStore
import com.lukeneedham.apphub.data.KtorGithubApi
import com.lukeneedham.apphub.data.githubHttpClient
import com.lukeneedham.apphub.data.PrefsPullRequestCache
import com.lukeneedham.apphub.data.PullRequestCache
import com.lukeneedham.apphub.data.KtorApkDownloader
import com.lukeneedham.apphub.data.PrefsInstallTracker
import com.lukeneedham.apphub.data.PullRequestRepository
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.ui.HomeViewModel
import com.lukeneedham.apphub.ui.SettingsViewModel
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
    single<TokenStore> { KeystoreTokenStore(androidContext()) }
    single<GithubApi> { KtorGithubApi(githubHttpClient(get()), GITHUB_OWNER, get()) }
    single<PullRequestCache> { PrefsPullRequestCache(androidContext()) }
    single { PullRequestRepository(get(), get()) } bind PullRequestSource::class
    single<ApkDownloader> { KtorApkDownloader(get(), File(androidContext().cacheDir, "apks")) }
    single<ApkInstaller> { AndroidApkInstaller(androidContext()) }
    single<InstallTracker> { PrefsInstallTracker(androidContext()) }
    viewModelOf(::HomeViewModel)
    viewModelOf(::SettingsViewModel)
}
