package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.DownloadEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/** Downloads into [directory]. A file is written under a temporary name, so a finished name is always whole. */
class KtorApkDownloader(
    okHttp: OkHttpClient,
    private val directory: File,
) : ApkDownloader {

    // No HTTP cache: an APK is large and is stored on disk by us anyway
    private val client = HttpClient(OkHttp) {
        engine { preconfigured = okHttp.newBuilder().cache(null).build() }
    }

    override fun existing(fileName: String): File? =
        File(directory, fileName).takeIf { it.isFile && it.length() > 0 }

    override fun download(url: String, fileName: String): Flow<DownloadEvent> = flow {
        directory.mkdirs()
        val target = File(directory, fileName)
        val partial = File(directory, fileName + PARTIAL_SUFFIX)
        try {
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) throw IOException("Download failed: HTTP ${response.status.value}")
                val total = response.contentLength()?.takeIf { it > 0 }
                var written = 0L
                var lastEmit = 0L
                emit(DownloadEvent.Progress(0, total))
                val input = response.bodyAsChannel()
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.readAvailable(buffer, 0, buffer.size)
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
                if (total != null && written != total) throw IOException("Download was cut short")
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Could not save the APK")
            emit(DownloadEvent.Done(target))
        } finally {
            partial.delete()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun prune(keep: Set<String>) {
        withContext(Dispatchers.IO) {
            directory.listFiles()?.forEach { file ->
                if (file.name.removeSuffix(PARTIAL_SUFFIX) !in keep) file.delete()
            }
        }
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}
