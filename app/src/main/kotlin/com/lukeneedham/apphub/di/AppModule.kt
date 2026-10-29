package com.lukeneedham.apphub.di

import com.lukeneedham.apphub.data.AndroidApkInstaller
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.KeystoreTokenStore
import com.lukeneedham.apphub.data.PrefsDisplaySettings
import com.lukeneedham.apphub.domain.DisplaySettings
import com.lukeneedham.apphub.data.KtorGithubApi
import com.lukeneedham.apphub.data.KtorNewAppApi
import com.lukeneedham.apphub.data.NewAppApi
import com.lukeneedham.apphub.data.NewAppRepository
import com.lukeneedham.apphub.data.githubHttpClient
import com.lukeneedham.apphub.data.PrefsPullRequestCache
import com.lukeneedham.apphub.data.PullRequestCache
import com.lukeneedham.apphub.data.KtorApkDownloader
import com.lukeneedham.apphub.data.PrefsInstallTracker
import com.lukeneedham.apphub.data.MainBuildRepository
import com.lukeneedham.apphub.data.PullRequestRepository
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.GITHUB_OWNER
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.NewAppSource
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.ui.ApkDownloads
import com.lukeneedham.apphub.ui.HomeViewModel
import com.lukeneedham.apphub.ui.NewAppViewModel
import com.lukeneedham.apphub.ui.PullRequestViewModel
import com.lukeneedham.apphub.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/** The name of the [TokenStore] that holds the token that creates repos. */
const val REPO_CREATION_TOKEN = "repoCreation"

val appModule = module {
    single {
        // The HTTP cache lets GitHub answer repeat requests with a 304, which is free of the rate limit
        OkHttpClient.Builder()
            .cache(Cache(File(androidContext().cacheDir, "http"), 10L * 1024 * 1024))
            .build()
    }
    single<TokenStore> { KeystoreTokenStore(androidContext()) }
    single<DisplaySettings> { PrefsDisplaySettings(androidContext()) }
    single { githubHttpClient(get()) }
    single<GithubApi> { KtorGithubApi(get(), GITHUB_OWNER, get()) }
    // A second token, kept apart from the one above, whose only power is to create repos (docs/create-app.md in ci-workflows)
    single<TokenStore>(named(REPO_CREATION_TOKEN)) {
        KeystoreTokenStore(androidContext(), prefsName = "repo_creation_token", alias = "apphub_repo_creation_token")
    }
    single<NewAppApi> { KtorNewAppApi(get(), get(), get(named(REPO_CREATION_TOKEN))) }
    single<NewAppSource> { NewAppRepository(get()) }
    single<PullRequestCache> { PrefsPullRequestCache(androidContext()) }
    single { PullRequestRepository(get(), get()) } bind PullRequestSource::class
    single { MainBuildRepository(get(), get()) } bind MainBuildSource::class
    single<ApkDownloader> { KtorApkDownloader(get(), File(androidContext().cacheDir, "apks")) }
    single<ApkInstaller> { AndroidApkInstaller(androidContext()) }
    single<InstallTracker> { PrefsInstallTracker(androidContext()) }
    // Outlives every screen, so a download carries on when its page is closed
    single { ApkDownloads(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    viewModelOf(::HomeViewModel)
    viewModel { (repo: String, number: Int) -> PullRequestViewModel(repo, number, get(), get(), get()) }
    viewModelOf(::SettingsViewModel)
    viewModel { NewAppViewModel(get(), get(), get(named(REPO_CREATION_TOKEN))) }
}
