package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.KtorApkDownloader
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KtorApkDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

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
}
