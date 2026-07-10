package com.syrmos.app.platform

import com.syrmos.core.common.AriadneModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * On-demand store for Ariadne's model on Android. The ~1.1 GB GGUF is NOT bundled
 * and never auto-downloaded; the user opts in, we fetch it from the pinned
 * manifest URL, verify its SHA-256, and cache it in the app's files dir. Until it
 * is present, the classifier returns null and Ariadne uses the rule parser.
 */
object AriadneModelStore {

    enum class Status { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    @Volatile
    var status: Status = Status.NOT_DOWNLOADED
        private set

    /** Bytes downloaded so far / total, for a progress UI (0 when idle). */
    @Volatile
    var progress: Float = 0f
        private set

    fun modelFile(): File? {
        val ctx = androidPlatformContext() ?: return null
        return File(File(ctx.filesDir, "ariadne").apply { mkdirs() }, AriadneModelManifest.FILE_NAME)
    }

    fun isReady(): Boolean {
        if (status == Status.READY) return true
        val f = modelFile() ?: return false
        val ready = f.exists() && f.length() == AriadneModelManifest.APPROX_BYTES
        if (ready) status = Status.READY
        return ready
    }

    /** Explicit, user-triggered download. Idempotent; verifies the checksum. */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext true
        val out = modelFile() ?: return@withContext false
        status = Status.DOWNLOADING
        progress = 0f
        val tmp = File(out.parentFile, out.name + ".part")
        try {
            val conn = (URL(AriadneModelManifest.URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var total = 0L
                    val expected = AriadneModelManifest.APPROX_BYTES.toFloat()
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        total += n
                        progress = (total / expected).coerceIn(0f, 1f)
                    }
                }
            }
            if (sha256(tmp) != AriadneModelManifest.SHA256) {
                tmp.delete(); status = Status.ERROR; return@withContext false
            }
            if (out.exists()) out.delete()
            tmp.renameTo(out)
            status = Status.READY
            true
        } catch (_: Throwable) {
            tmp.delete()
            status = Status.ERROR
            false
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
