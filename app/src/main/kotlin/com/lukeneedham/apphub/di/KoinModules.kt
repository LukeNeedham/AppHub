package com.lukeneedham.apphub.di

import com.lukeneedham.apphub.data.AndroidApkInstaller
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.KeystoreTokenStore
import com.lukeneedham.apphub.data.KtorApkDownloader
import com.lukeneedham.apphub.data.KtorGithubApi
import com.lukeneedham.apphub.data.KtorNewAppApi
import com.lukeneedham.apphub.data.MainBuildRepository
import com.lukeneedham.apphub.data.NewAppApi
import com.lukeneedham.apphub.data.NewAppRepository
import com.lukeneedham.apphub.data.PrefsDisplaySettings
import com.lukeneedham.apphub.data.PrefsInstallTracker
import com.lukeneedham.apphub.data.PrefsPullRequestCache
import com.lukeneedham.apphub.data.PullRequestCache
import com.lukeneedham.apphub.data.PullRequestRepository
import com.lukeneedham.apphub.data.githubHttpClient
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.DisplaySettings
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
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

object KoinModules {
    private val app = module {
        single { OkHttpClientFactory.create(androidContext()) }
        single<TokenStore> { KeystoreTokenStore(androidContext()) }
        single<DisplaySettings> { PrefsDisplaySettings(androidContext()) }
        single { githubHttpClient(get()) }
        single<GithubApi> { KtorGithubApi(get(), GITHUB_OWNER, get()) }
        single<TokenStore>(named(KoinQualifier.REPO_CREATION_TOKEN)) { TokenStoreFactory.repoCreation(androidContext()) }
        single<NewAppApi> { KtorNewAppApi(get(), get(), get(named(KoinQualifier.REPO_CREATION_TOKEN))) }
        single<NewAppSource> { NewAppRepository(get()) }
        single<PullRequestCache> { PrefsPullRequestCache(androidContext()) }
        single { PullRequestRepository(get(), get()) } bind PullRequestSource::class
        single { MainBuildRepository(get(), get()) } bind MainBuildSource::class
        single(named(KoinQualifier.APK_DIRECTORY)) { AppDirectories.apks(androidContext()) }
        single<ApkDownloader> { KtorApkDownloader(get(), get(named(KoinQualifier.APK_DIRECTORY))) }
        single<ApkInstaller> { AndroidApkInstaller(androidContext()) }
        single<InstallTracker> { PrefsInstallTracker(androidContext()) }
        single(named(KoinQualifier.APPLICATION_SCOPE)) { ApplicationScope.create() }
        single { ApkDownloads(get(), get(), get(), get(named(KoinQualifier.APPLICATION_SCOPE))) }
        viewModelOf(::HomeViewModel)
        viewModel { (repo: String, number: Int) -> PullRequestViewModel(repo, number, get(), get(), get()) }
        viewModel { SettingsViewModel(get(), get(), get(named(KoinQualifier.REPO_CREATION_TOKEN)), get()) }
        viewModel { NewAppViewModel(get(), get(), get(named(KoinQualifier.REPO_CREATION_TOKEN))) }
    }

    val modules: List<Module> = listOf(app)
}
