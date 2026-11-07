package moe.ouom.neriplayer.data.source.lxmusic.js

import java.io.IOException
import okhttp3.ResponseBody
import okio.Buffer

/**
 * 用户导入的 LX JS 音源会通过 native bridge 接收 HTTP 正文。这里必须限制正文，
 * 避免错误的音源把整段音频或异常大文件序列化到 QuickJS，导致应用 OOM。
 */
internal const val LX_JS_RESPONSE_MAX_BYTES = 2L * 1024L * 1024L

internal class LxJsResponseTooLargeException(
    limitBytes: Long,
    declaredBytes: Long? = null
) : IOException(
    buildString {
        append("LX JS response exceeds ")
        append(limitBytes)
        append(" bytes")
        declaredBytes?.let {
            append(" (declared ")
            append(it)
            append(')')
        }
    }
)

/**
 * 在把 HTTP 正文转换成 JSON / 字符串之前进行限长读取。
 *
 * 即使服务端没有声明 Content-Length，也只多读一个字节确认超限，不能在内存中
 * 累积完整响应后再检查。
 */
internal fun ResponseBody.readLxJsTextWithLimit(
    maxBytes: Long = LX_JS_RESPONSE_MAX_BYTES
): String {
    require(maxBytes > 0L) { "maxBytes must be positive" }
    val declaredBytes = contentLength()
    if (declaredBytes > maxBytes) {
        throw LxJsResponseTooLargeException(maxBytes, declaredBytes)
    }

    val sink = Buffer()
    val source = source()
    var totalBytes = 0L
    while (true) {
        val remainingBytes = maxBytes - totalBytes
        val readBytes = source.read(
            sink,
            minOf(remainingBytes + 1L, 64L * 1024L)
        )
        if (readBytes == -1L) break
        totalBytes += readBytes
        if (totalBytes > maxBytes) {
            throw LxJsResponseTooLargeException(maxBytes)
        }
    }

    val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    return sink.readString(charset)
}
