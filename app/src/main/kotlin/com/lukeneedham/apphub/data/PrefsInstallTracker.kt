package com.lukeneedham.apphub.data

import android.content.Context
import android.content.pm.PackageManager
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.PullRequest
import java.io.File

/**
 * Debug builds usually keep the same `versionCode` and `versionName`, so the installed version cannot
 * say which PR commit it is. Instead this remembers which commit AppHub installed for each package, and
 * the package's `lastUpdateTime` at that moment: if the time has since changed, something else (another
 * build) replaced it.
 *
 * The package name of a repo's app is learned from the first APK downloaded for it.
 */
class PrefsInstallTracker(context: Context) : InstallTracker {

    private val packageManager = context.packageManager
    private val prefs = context.getSharedPreferences("install_tracker", Context.MODE_PRIVATE)

    override fun statusFor(pr: PullRequest): InstallStatus {
        val commit = pr.downloadableApk?.buildId ?: return InstallStatus.Unknown
        val packageName = prefs.getString(packageKey(pr.repo), null) ?: return InstallStatus.Unknown
        val updatedAt = lastUpdateTime(packageName) ?: return InstallStatus.NotInstalled
        val installedCommit = prefs.getString("commit:$packageName", null)
        val installedAt = prefs.getLong("updated:$packageName", -1)
        val isThisBuild = installedCommit != null && installedAt == updatedAt &&
            (installedCommit.startsWith(commit) || commit.startsWith(installedCommit))
        return if (isThisBuild) InstallStatus.Installed else InstallStatus.DifferentBuild
    }

    override fun packageNameFor(pr: PullRequest): String? = prefs.getString(packageKey(pr.repo), null)

    override fun onDownloaded(pr: PullRequest, file: File) {
        val info = packageManager.getPackageArchiveInfo(file.path, 0) ?: return
        prefs.edit().putString(packageKey(pr.repo), info.packageName).apply()
    }

    override fun onInstallStarted(pr: PullRequest) {
        val commit = pr.downloadableApk?.buildId ?: return
        val packageName = prefs.getString(packageKey(pr.repo), null) ?: return
        prefs.edit()
            .putString("pending:package", packageName)
            .putString("pending:commit", commit)
            .putLong("pending:at", System.currentTimeMillis())
            .apply()
    }

    override fun confirmPending() {
        val packageName = prefs.getString("pending:package", null) ?: return
        val commit = prefs.getString("pending:commit", null) ?: return
        val startedAt = prefs.getLong("pending:at", 0)
        val edit = prefs.edit()
        val updatedAt = lastUpdateTime(packageName)
        if (updatedAt != null && updatedAt >= startedAt) {
            edit.putString("commit:$packageName", commit).putLong("updated:$packageName", updatedAt)
            edit.remove("pending:package").remove("pending:commit").remove("pending:at")
        } else if (System.currentTimeMillis() - startedAt > PENDING_TIMEOUT_MS) {
            // The install was cancelled or never finished
            edit.remove("pending:package").remove("pending:commit").remove("pending:at")
        }
        edit.apply()
    }

    private fun lastUpdateTime(packageName: String): Long? =
        try {
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    private fun packageKey(repo: String) = "package:$repo"

    private companion object {
        const val PENDING_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
