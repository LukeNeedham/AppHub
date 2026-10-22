package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.KtorApkDownloader
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipOutputStream

class KtorApkDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun writeValidZip(file: java.io.File) {
        ZipOutputStream(file.outputStream()).use { it.putNextEntry(java.util.zip.ZipEntry("x")); it.closeEntry() }
    }

    @Test
    fun `prune deletes files that are not kept`() = runTest {
        val dir = folder.newFolder("apks")
        listOf("keep.apk", "old.apk").forEach { dir.resolve(it).writeText("x") }

        KtorApkDownloader(OkHttpClient(), dir).prune(setOf("keep.apk"))

        assertEquals(listOf("keep.apk"), dir.list()!!.toList())
    }

    @Test
    fun `prune keeps the partial file of a kept download and deletes other partials`() = runTest {
        val dir = folder.newFolder("apks")
        listOf("keep.apk.part", "old.apk.part").forEach { dir.resolve(it).writeText("x") }

        KtorApkDownloader(OkHttpClient(), dir).prune(setOf("keep.apk"))

        assertEquals(listOf("keep.apk.part"), dir.list()!!.toList())
    }

    @Test
    fun `existing rejects a corrupt cached file instead of offering it for install`() = runTest {
        val dir = folder.newFolder("apks")
        // Looks like a finished download (non-empty, right name) but is not actually a valid zip/APK -
        // e.g. left over from an interrupted download under an older app version.
        dir.resolve("broken.apk").writeText("not an apk")

        val existing = KtorApkDownloader(OkHttpClient(), dir).existing("broken.apk")

        assertNull(existing)
    }

    @Test
    fun `existing accepts a well-formed cached file`() = runTest {
        val dir = folder.newFolder("apks")
        writeValidZip(dir.resolve("ok.apk"))

        val existing = KtorApkDownloader(OkHttpClient(), dir).existing("ok.apk")

        assertNotNull(existing)
    }

    @Test
    fun `prune keeps the temporary files of an extraction under a kept name`() = runTest {
        val dir = folder.newFolder("apks")
        listOf("keep.apk.extract.part", "old.apk.extract.part").forEach { dir.resolve(it).writeText("x") }

        KtorApkDownloader(OkHttpClient(), dir).prune(setOf("keep.apk"))

        assertEquals(listOf("keep.apk.extract.part"), dir.list()!!.toList())
    }
}
