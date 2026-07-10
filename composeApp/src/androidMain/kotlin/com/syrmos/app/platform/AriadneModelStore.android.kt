package com.syrmos.app.platform

import com.syrmos.core.common.AriadneModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _status = MutableStateFlow(Status.NOT_DOWNLOADED)
    val statusFlow: StateFlow<Status> = _status.asStateFlow()
    val status: Status get() = _status.value

    /** Bytes downloaded so far / total, for a progress UI (0 when idle). */
    private val _progress = MutableStateFlow(0f)
    val progressFlow: StateFlow<Float> = _progress.asStateFlow()
    val progress: Float get() = _progress.value

    fun modelFile(): File? {
        val ctx = androidPlatformContext() ?: return null
        return File(File(ctx.filesDir, "ariadne").apply { mkdirs() }, AriadneModelManifest.FILE_NAME)
    }

    fun isReady(): Boolean {
        if (status == Status.READY) return true
        val f = modelFile() ?: return false
        val ready = f.exists() && f.length() == AriadneModelManifest.APPROX_BYTES
        if (ready) _status.value = Status.READY
        return ready
    }

    /** Explicit, user-triggered download. Idempotent; verifies the checksum. */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext true
        val out = modelFile() ?: return@withContext false
        _status.value = Status.DOWNLOADING
        _progress.value = 0f
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
                        _progress.value = (total / expected).coerceIn(0f, 1f)
                    }
                }
            }
            if (sha256(tmp) != AriadneModelManifest.SHA256) {
                tmp.delete(); _status.value = Status.ERROR; return@withContext false
            }
            if (out.exists()) out.delete()
            tmp.renameTo(out)
            _progress.value = 1f
            _status.value = Status.READY
            true
        } catch (_: Throwable) {
            tmp.delete()
            _status.value = Status.ERROR
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
