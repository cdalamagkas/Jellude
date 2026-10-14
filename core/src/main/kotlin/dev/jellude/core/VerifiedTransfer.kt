package dev.jellude.core

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

class TransferFailure(val status: Int, message: String) : IOException(message)
data class TransferResult(val bytes: Long, val sha256: String)

/**
 * An asset has a .part file and .etag sidecar until its caller validates and publishes it.
 * Only a strong ETag permits resume. No validator means a clean restart on the next attempt.
 */
class VerifiedTransfer(private val client: OkHttpClient) {
    fun download(request: Request, partial: File, cancelled: () -> Boolean = { false }): TransferResult {
        partial.parentFile?.mkdirs()
        val validatorFile = File(partial.path + ".etag")
        val etag = validatorFile.takeIf { it.exists() }?.readText()?.takeIf(::strong)
        val offset = if (etag != null && partial.exists()) partial.length() else 0L
        val outgoing = request.newBuilder().header("Accept-Encoding", "identity").apply {
            if (offset > 0) { header("Range", "bytes=$offset-"); header("If-Range", etag!!) }
        }.build()
        client.newCall(outgoing).execute().use { response ->
            if (!response.isSuccessful) throw TransferFailure(response.code, "HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val type = body.contentType()?.toString().orEmpty()
            if (type.contains("text/html") || type.contains("json")) throw IOException("Server returned an error document")
            var append = false
            var total: Long? = null
            if (response.code == 206) {
                val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                    ?: run { validatorFile.delete(); throw IOException("Invalid Content-Range") }
                val (start, end, size) = match.destructured
                if (offset == 0L || start.toLong() != offset || end.toLong() < offset || size.toLong() <= end.toLong()) {
                    validatorFile.delete(); throw IOException("Unsafe resume range")
                }
                if (response.header("ETag") != etag) { validatorFile.delete(); throw IOException("Source changed while resuming") }
                total = size.toLong()
                append = true
            } else if (response.code != 200) throw IOException("Unexpected download response ${response.code}")
            val newTag = response.header("ETag")
            if (newTag != null && strong(newTag)) validatorFile.writeText(newTag) else validatorFile.delete()
            var copied = 0L
            RandomAccessFile(partial, "rw").use { output ->
                if (!append) output.setLength(0)
                output.seek(if (append) offset else 0)
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw IOException("Transfer interrupted")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                    }
                }
                output.fd.sync()
            }
            if (body.contentLength() >= 0 && copied != body.contentLength()) throw IOException("Truncated response")
            if (total != null && partial.length() != total) throw IOException("Incomplete resumed file")
            if (partial.length() == 0L) throw IOException("Empty audio file")
            return TransferResult(partial.length(), sha256(partial))
        }
    }

    companion object {
        private fun strong(value: String) = value.startsWith('"') && value.endsWith('"') && !value.startsWith("W/")
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
