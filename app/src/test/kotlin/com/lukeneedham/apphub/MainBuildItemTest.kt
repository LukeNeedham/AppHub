package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class MainBuildItemTest {

    private fun item(install: InstallStatus = InstallStatus.NotInstalled) = MainBuildItem(
        repo = "LukeNeedham/FlagTutor",
        apk = MainApk("abcdef1234567", "https://example.com/app-debug.apk"),
        download = DownloadState.Idle,
        install = install,
    )

    @Test
    fun `a build that is not installed is ready`() {
        assertEquals(MainPhase.Ready, item().phase)
        assertEquals(MainPhase.Ready, item(InstallStatus.DifferentBuild).phase)
    }

    @Test
    fun `the installed build is installed`() {
        assertEquals(MainPhase.Installed, item(InstallStatus.Installed).phase)
    }

    @Test
    fun `the offer is named after the repo and the commit`() {
        val offer = item().offer
        assertEquals("LukeNeedham/FlagTutor@main", offer.key)
        assertEquals("abcdef1234567", offer.buildId)
        assertEquals("LukeNeedham_FlagTutor-main-abcdef1234567.apk", offer.fileName)
    }
}
