package moe.ouom.neriplayer.core.api.qqmusic

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpCookie
import java.net.URI
import java.util.concurrent.TimeUnit

internal data class QQMusicQrSession(
    val imageBytes: ByteArray,
    val qrsig: String,
    val cookies: Map<String, String>
)

internal enum class QQMusicQrStatus {
    WAITING,
    SCANNED,
    CONFIRMED,
    EXPIRED,
    FAILED
}

internal data class QQMusicQrPollResult(
    val status: QQMusicQrStatus,
    val cookies: Map<String, String> = emptyMap()
)

internal class QQMusicQrLoginClient {
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    fun create(): QQMusicQrSession? {
        val url = "https://ssl.ptlogin2.qq.com/ptqrshow" +
            "?appid=716027609&e=2&l=M&s=3&d=72&v=4&daid=383&pt_3rd_aid=100497308"
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val qrsig = response.headers.values("Set-Cookie")
                    .asSequence()
                    .mapNotNull { parseCookieHeader(it)["qrsig"] }
                    .firstOrNull()
                    ?: return null
                val cookies = response.headers.values("Set-Cookie")
                    .flatMap { parseCookieHeader(it).entries }
                    .associate { it.key to it.value }
                QQMusicQrSession(response.body?.bytes() ?: return null, qrsig, cookies)
            }
        }.getOrNull()
    }

    fun poll(session: QQMusicQrSession): QQMusicQrPollResult {
        val token = hash33(session.qrsig)
        val url = "https://ssl.ptlogin2.qq.com/ptqrlogin" +
            "?u1=https%3A%2F%2Fy.qq.com%2F&ptqrtoken=$token&ptredirect=0&h=1&t=1" +
            "&g=1&from_ui=1&ptlang=2052&action=0-0-0&js_ver=210" +
            "&js_type=1&pt_uistyle=40&aid=716027609&daid=383&has_onekey=1" +
            "&pt_3rd_aid=100497308"
        val cookie = session.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" } +
            "; qrsig=${session.qrsig}"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("Referer", "https://xui.ptlogin2.qq.com/")
            .header("User-Agent", UA)
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val returnedCookies = response.headers.values("Set-Cookie")
                    .flatMap { parseCookieHeader(it).entries }
                    .associate { it.key to it.value }
                when {
                    body.startsWith("ptuiCB('0'") -> QQMusicQrPollResult(
                        QQMusicQrStatus.CONFIRMED,
                        session.cookies + returnedCookies
                    )
                    body.startsWith("ptuiCB('65'") -> QQMusicQrPollResult(QQMusicQrStatus.SCANNED)
                    body.startsWith("ptuiCB('66'") -> QQMusicQrPollResult(QQMusicQrStatus.WAITING)
                    body.startsWith("ptuiCB('68'") || body.startsWith("ptuiCB('67'") ->
                        QQMusicQrPollResult(QQMusicQrStatus.EXPIRED)
                    else -> QQMusicQrPollResult(QQMusicQrStatus.FAILED)
                }
            }
        }.getOrElse { QQMusicQrPollResult(QQMusicQrStatus.FAILED) }
    }

    private fun hash33(value: String): String {
        var hash = 0L
        value.forEach { hash = (hash shl 5) - hash + it.code }
        return (hash and 0x7fffffff).toString()
    }

    private fun parseCookieHeader(header: String): Map<String, String> =
        runCatching {
            HttpCookie.parse(header).associate { it.name to it.value }
        }.getOrDefault(emptyMap())

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }
}
