package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainPhase
import com.lukeneedham.apphub.domain.MainRun
import com.lukeneedham.apphub.domain.RunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBuildItemTest {

    private fun item(
        run: RunState? = RunState.Succeeded,
        apk: String? = "abcdef1234567",
        behind: Int = 0,
        install: InstallStatus = InstallStatus.NotInstalled,
        requested: Boolean = false,
    ) = MainBuildItem(
        repo = "LukeNeedham/FlagTutor",
        info = MainBuildInfo(
            run = run?.let { MainRun(1, "runcommit1234", it) },
            apk = apk?.let { MainApk(5, it) },
            tip = "tip",
            commitsBehind = behind,
        ),
        requested = requested,
        download = DownloadState.Idle,
        install = install,
    )

    @Test
    fun `no run and no apk is no build`() {
        val item = item(run = null, apk = null)
        assertEquals(MainPhase.NoBuild, item.phase)
        assertEquals("Build main", item.buildLabel)
        assertTrue(item.offersBuild)
        assertNull(item.offer)
        assertNull(item.behindText)
    }

    @Test
    fun `the run is linked on GitHub`() {
        assertEquals("https://github.com/LukeNeedham/FlagTutor/actions/runs/1", item().runUrl)
        assertNull(item(run = null).runUrl)
    }

    @Test
    fun `a press is requested until the run shows up, and cannot be repeated`() {
        val item = item(requested = true)
        assertEquals(MainPhase.Requested, item.phase)
        assertFalse(item.canBuild)
    }

    @Test
    fun `an active run is building even when an older apk is there`() {
        val item = item(run = RunState.Active)
        assertEquals(MainPhase.Building, item.phase)
        assertFalse(item.canBuild)
    }

    @Test
    fun `a failed build without an apk is failed`() {
        val item = item(run = RunState.Failed, apk = null)
        assertEquals(MainPhase.Failed, item.phase)
        assertTrue(item.canBuild)
    }

    @Test
    fun `a failed newer build leaves the older apk on offer`() {
        val item = item(run = RunState.Failed)
        assertEquals(MainPhase.Ready, item.phase)
    }

    @Test
    fun `a ready apk of the latest main is up to date`() {
        val item = item()
        assertEquals(MainPhase.Ready, item.phase)
        assertEquals("Up to date", item.behindText)
        assertFalse(item.offersBuild)
    }

    @Test
    fun `a ready apk that lacks commits says how far and offers the latest`() {
        val item = item(behind = 3)
        assertEquals("3 commits behind", item.behindText)
        assertEquals("Build latest main", item.buildLabel)
        assertTrue(item.offersBuild)
    }

    @Test
    fun `the installed build is the latest main`() {
        val item = item(install = InstallStatus.Installed)
        assertEquals(MainPhase.Installed, item.phase)
        assertEquals("Up to date", item.behindText)
    }

    @Test
    fun `the installed build behind main says how far`() {
        val item = item(install = InstallStatus.Installed, behind = 1)
        assertEquals("1 commit behind", item.behindText)
        assertEquals("Build latest main", item.buildLabel)
        assertTrue(item.canBuild)
    }

    @Test
    fun `the offer is named after the repo and the commit`() {
        val offer = item().offer!!
        assertEquals("LukeNeedham/FlagTutor@main", offer.key)
        assertEquals("abcdef1234567", offer.buildId)
        assertEquals("LukeNeedham_FlagTutor-main-abcdef1234567.apk", offer.fileName)
    }
}
