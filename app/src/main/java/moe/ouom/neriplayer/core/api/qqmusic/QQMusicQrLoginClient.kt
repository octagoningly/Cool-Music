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

internal fun isUsableQQMusicCookies(cookies: Map<String, String>): Boolean {
    val uin = cookies["uin"].orEmpty().trim()
    val key = cookies["qm_keyst"].orEmpty().ifBlank { cookies["qqmusic_key"].orEmpty() }.trim()
    return uin.isNotEmpty() && uin.all(Char::isDigit) && key.isNotEmpty()
}

internal class QQMusicQrLoginClient {
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    fun create(): QQMusicQrSession? {
        val url = "https://ssl.ptlogin2.qq.com/ptqrshow" +
            "?appid=716027609&e=2&l=M&s=3&d=72&v=4&t=${Math.random()}" +
            "&daid=383&pt_3rd_aid=100497308" +
            "&u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump"
        val request = Request.Builder()
            .url(url)
            .header("Referer", LOGIN_REFERER)
            .header("User-Agent", UA)
            .build()
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
            "?ptqrtoken=$token&from_ui=1&aid=716027609&daid=383" +
            "&pt_3rd_aid=100497308" +
            "&u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump"
        val cookie = session.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" } +
            "; qrsig=${session.qrsig}"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("Referer", LOGIN_REFERER)
            .header("User-Agent", UA)
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val returnedCookies = response.headers.values("Set-Cookie")
                    .flatMap { parseCookieHeader(it).entries }
                    .associate { it.key to it.value }
                when {
                    body.startsWith("ptuiCB('0'") -> {
                        val jumpUrl = Regex("ptuiCB\\('0','0','([^']*)'").find(body)
                            ?.groupValues?.getOrNull(1)
                            ?.replace("\\x26", "&")
                        QQMusicQrPollResult(
                            QQMusicQrStatus.CONFIRMED,
                            followLoginRedirects(
                                jumpUrl,
                                session.cookies + returnedCookies
                            )
                        )
                    }
                    body.startsWith("ptuiCB('67'") -> QQMusicQrPollResult(QQMusicQrStatus.SCANNED)
                    body.startsWith("ptuiCB('66'") -> QQMusicQrPollResult(QQMusicQrStatus.WAITING)
                    body.startsWith("ptuiCB('65'") || body.startsWith("ptuiCB('68'") ->
                        QQMusicQrPollResult(QQMusicQrStatus.EXPIRED)
                    else -> QQMusicQrPollResult(QQMusicQrStatus.FAILED)
                }
            }
        }.getOrElse { QQMusicQrPollResult(QQMusicQrStatus.FAILED) }
    }

    private fun hash33(value: String): String {
        var hash = 0L
        value.forEach { hash += (hash shl 5) + it.code }
        return (hash and 0x7fffffff).toString()
    }

    private fun followLoginRedirects(
        jumpUrl: String?,
        initialCookies: Map<String, String>
    ): Map<String, String> {
        if (jumpUrl.isNullOrBlank()) return initialCookies
        var currentUrl = jumpUrl
        var cookies = initialCookies
        repeat(6) {
            val request = Request.Builder()
                .url(currentUrl!!)
                .header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                .header("Referer", LOGIN_REFERER)
                .header("User-Agent", UA)
                .build()
            val response = runCatching { http.newCall(request).execute() }.getOrNull()
                ?: return cookies
            response.use {
                cookies = cookies + it.headers.values("Set-Cookie")
                    .flatMap { header -> parseCookieHeader(header).entries }
                    .associate { entry -> entry.key to entry.value }
                val location = it.header("Location") ?: return cookies
                currentUrl = URI(currentUrl!!).resolve(location).toString()
            }
        }
        return cookies
    }

    private fun parseCookieHeader(header: String): Map<String, String> =
        runCatching {
            HttpCookie.parse(header).associate { it.name to it.value }
        }.getOrDefault(emptyMap())

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        const val LOGIN_REFERER = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin" +
            "?appid=716027609&style=20" +
            "&s_url=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump" +
            "&maskOpacity=60&daid=383&target=self"
    }
}
