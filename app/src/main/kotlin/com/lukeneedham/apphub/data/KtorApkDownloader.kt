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
import java.util.zip.ZipFile

/**
 * Downloads into [directory]. A file is written under a temporary name, so a finished name is always whole.
 * A workflow artifact is a zip: it is downloaded whole, then the APK in it is extracted.
 */
class KtorApkDownloader(
    okHttp: OkHttpClient,
    private val directory: File,
) : ApkDownloader {

    // No HTTP cache: an APK is large and is stored on disk by us anyway
    private val client = HttpClient(OkHttp) {
        engine { preconfigured = okHttp.newBuilder().cache(null).build() }
    }

    override fun existing(fileName: String): File? =
        File(directory, fileName).takeIf { it.isFile && it.length() > 0 && isValidApk(it) }

    override fun download(url: String, fileName: String, fromZip: Boolean): Flow<DownloadEvent> = flow {
        directory.mkdirs()
        val target = File(directory, fileName)
        val partial = File(directory, fileName + PARTIAL_SUFFIX)
        val extracted = File(directory, "$fileName.extract$PARTIAL_SUFFIX")
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
            // The length check above only fires when the server sends Content-Length; without it (or if
            // the bytes are wrong despite a matching length), a corrupt file would otherwise still be
            // handed to the installer as "done" - and then sit there failing to install every time, since
            // existing() would keep treating it as a valid cached download. Catch that here instead.
            val apk = if (fromZip) extracted.also { extractApk(partial, it) } else partial
            if (!isValidApk(apk)) throw IOException("Downloaded file is not a valid APK")
            target.delete()
            if (!apk.renameTo(target)) throw IOException("Could not save the APK")
            emit(DownloadEvent.Done(target))
        } finally {
            partial.delete()
            extracted.delete()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun prune(keep: Set<String>) {
        withContext(Dispatchers.IO) {
            directory.listFiles()?.forEach { file ->
                // The temporary files of a kept name are named after it: ".part", ".extract.part"
                if (file.name !in keep && keep.none { file.name.startsWith("$it.") }) file.delete()
            }
        }
    }

    /** Copies the first APK in the artifact [zip] to [into]. */
    private fun extractApk(zip: File, into: File) {
        ZipFile(zip).use { archive ->
            val entry = archive.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".apk") }
                ?: throw IOException("The build holds no APK")
            archive.getInputStream(entry).use { input -> into.outputStream().use { input.copyTo(it) } }
        }
    }

    /** A truncated or corrupted download fails to open as a zip, even when its size looks plausible. */
    private fun isValidApk(file: File): Boolean =
        try {
            ZipFile(file).close()
            true
        } catch (e: Exception) {
            false
        }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}
