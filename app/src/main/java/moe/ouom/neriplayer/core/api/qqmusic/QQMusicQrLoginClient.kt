package moe.ouom.neriplayer.core.api.qqmusic

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.HttpCookie
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import org.json.JSONObject

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
    val uin = cookies["uin"].orEmpty().trim().removePrefix("o")
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
                            exchangeForQQMusicCookies(
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

    /**
     * ptqrlogin 的成功回调只代表 QQ 通用登录完成。还必须用 p_skey 换 OAuth code，
     * 再调 QQConnectLogin.LoginServer，QQ 音乐才会下发 qm_keyst/qqmusic_key。
     */
    private fun exchangeForQQMusicCookies(
        jumpUrl: String?,
        initialCookies: Map<String, String>
    ): Map<String, String> {
        if (jumpUrl.isNullOrBlank()) return initialCookies
        var cookies = initialCookies
        fun request(builder: Request.Builder): okhttp3.Response? =
            runCatching { http.newCall(builder.header("Cookie", cookieHeader(cookies)).header("User-Agent", UA).build()).execute() }
                .getOrNull()
        fun collect(response: okhttp3.Response) {
            cookies = cookies + response.headers.values("Set-Cookie")
                .flatMap { header -> parseCookieHeader(header).entries }
                .associate { entry -> entry.key to entry.value }
        }

        val checkSig = request(Request.Builder().url(jumpUrl).header("Referer", LOGIN_REFERER))
            ?: return cookies
        checkSig.use { collect(it) }
        val pSkey = cookies["p_skey"].orEmpty()
        if (pSkey.isBlank()) return cookies

        val authorizePayload = buildString {
            append("response_type=code")
            append("&client_id=100497308")
            append("&redirect_uri=").append(java.net.URLEncoder.encode(MUSIC_REDIRECT_URI, "UTF-8"))
            append("&scope=get_user_info%2Cget_app_friends&state=state&switch=&from_ptlogin=1&src=1")
            append("&update_auth=1&openapi=1010_1030&g_tk=").append(gtk(pSkey))
            append("&auth_time=").append(System.currentTimeMillis())
            append("&ui=").append(java.util.UUID.randomUUID())
        }
        val authorize = request(
            Request.Builder()
                .url("https://graph.qq.com/oauth2.0/authorize")
                .post(authorizePayload.toRequestBody(FORM_MEDIA_TYPE))
                .header("Referer", "https://graph.qq.com/")
        ) ?: return cookies
        val location = authorize.use {
            collect(it)
            it.header("Location")
        } ?: return cookies
        val code = runCatching {
            URLDecoder.decode(location.substringAfter("code=").substringBefore('&'), "UTF-8")
        }.getOrDefault("")
        if (code.isBlank()) return cookies

        val musicPayload = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("g_tk", gtk(pSkey)); put("platform", "yqq"); put("ct", 24); put("cv", 0)
            })
            put("req", JSONObject().apply {
                put("module", "QQConnectLogin.LoginServer"); put("method", "QQLogin")
                put("param", JSONObject().put("code", code))
            })
        }.toString()
        request(
            Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .post(musicPayload.toRequestBody(FORM_MEDIA_TYPE))
                .header("Referer", "https://y.qq.com/")
        )?.use(::collect)
        return cookies
    }

    private fun cookieHeader(cookies: Map<String, String>): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun gtk(value: String): Int {
        var hash = 5381
        value.forEach { hash += (hash shl 5) + it.code }
        return hash and 0x7fffffff
    }

    private fun parseCookieHeader(header: String): Map<String, String> =
        runCatching {
            HttpCookie.parse(header).associate { it.name to it.value }
        }.getOrDefault(emptyMap())

    private companion object {
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        const val MUSIC_REDIRECT_URI = "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/"
        const val UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        const val LOGIN_REFERER = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin" +
            "?appid=716027609&style=20" +
            "&s_url=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump" +
            "&maskOpacity=60&daid=383&target=self"
    }
}
