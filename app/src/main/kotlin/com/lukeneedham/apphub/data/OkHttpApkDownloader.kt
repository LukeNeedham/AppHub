package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.DownloadEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Downloads into [directory]. A file is written under a temporary name, so a finished name is always whole. */
class OkHttpApkDownloader(
    client: OkHttpClient,
    private val directory: File,
) : ApkDownloader {

    // No HTTP cache: an APK is large and is stored on disk by us anyway
    private val client = client.newBuilder().cache(null).build()

    override fun existing(fileName: String): File? =
        File(directory, fileName).takeIf { it.isFile && it.length() > 0 }

    override fun download(url: String, fileName: String): Flow<DownloadEvent> = flow {
        directory.mkdirs()
        val target = File(directory, fileName)
        val partial = File(directory, "$fileName.part")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Download failed: empty response")
                val total = body.contentLength().takeIf { it > 0 }
                var written = 0L
                var lastEmit = 0L
                emit(DownloadEvent.Progress(0, total))
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            val now = System.nanoTime()
                            if (now - lastEmit > 100_000_000) {
                                lastEmit = now
                                emit(DownloadEvent.Progress(written, total))
                            }
                        }
                    }
                }
                if (total != null && written != total) throw IOException("Download was cut short")
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Could not save the APK")
            emit(DownloadEvent.Done(target))
        } finally {
            partial.delete()
        }
    }.flowOn(Dispatchers.IO)

    override fun prune(keep: Set<String>) {
        directory.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }
}
