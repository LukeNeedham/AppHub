package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainNote
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
        assertNull(item.offer)
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
        assertEquals(MainNote("Building main at runcomm…"), item.note)
    }

    @Test
    fun `a failed build without an apk is failed`() {
        val item = item(run = RunState.Failed, apk = null)
        assertEquals(MainPhase.Failed, item.phase)
        assertEquals(MainNote("The build of runcomm failed", warn = true), item.note)
        assertTrue(item.canBuild)
    }

    @Test
    fun `a failed newer build leaves the older apk on offer and says so`() {
        val item = item(run = RunState.Failed)
        assertEquals(MainPhase.Ready, item.phase)
        assertEquals(MainNote("The newest build failed; this is an older one (abcdef1)", warn = true), item.note)
    }

    @Test
    fun `a ready apk of the latest main says so`() {
        val item = item()
        assertEquals(MainPhase.Ready, item.phase)
        assertEquals(MainNote("Built from the latest main (abcdef1)"), item.note)
        assertEquals("Rebuild", item.buildLabel)
    }

    @Test
    fun `a ready apk that lacks commits is noted and offers the latest`() {
        val item = item(behind = 3)
        assertEquals(MainNote("3 new commits on main since this build (abcdef1)"), item.note)
        assertEquals("Build latest main", item.buildLabel)
    }

    @Test
    fun `the installed build is the latest main`() {
        val item = item(install = InstallStatus.Installed)
        assertEquals(MainPhase.Installed, item.phase)
        assertEquals(MainNote("Installed: this is the latest main (abcdef1)"), item.note)
    }

    @Test
    fun `the installed build behind main says how far`() {
        val item = item(install = InstallStatus.Installed, behind = 1)
        assertEquals(MainNote("1 new commit on main since the installed build (abcdef1)"), item.note)
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
