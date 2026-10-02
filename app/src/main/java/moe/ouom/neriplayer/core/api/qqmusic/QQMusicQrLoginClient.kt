package moe.ouom.neriplayer.core.api.qqmusic

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import moe.ouom.neriplayer.core.logging.NPLogger
import java.net.HttpCookie
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
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
    REFUSED,
    FAILED
}

/** 凭据交换失败的具体环节，用于给用户准确文案而非笼统的「二维码获取失败」。 */
internal enum class QQMusicQrExchangeFailure {
    NONE,
    NO_JUMP_URL,
    NO_SESSION_KEY,
    NO_CODE,
    NO_MUSIC_KEY
}

internal data class QQMusicQrPollResult(
    val status: QQMusicQrStatus,
    val cookies: Map<String, String> = emptyMap(),
    val exchangeFailure: QQMusicQrExchangeFailure = QQMusicQrExchangeFailure.NONE
)

internal data class QQMusicQrCallback(
    val status: QQMusicQrStatus,
    val jumpUrl: String? = null
)

internal data class QQCookie(val name: String, val value: String)

internal fun parseSetCookieHeader(header: String): QQCookie? {
    val pair = header.substringBefore(';').trim()
    val separator = pair.indexOf('=')
    if (separator <= 0) return null
    val name = pair.substring(0, separator).trim()
    val value = pair.substring(separator + 1).trim()
    if (name.isEmpty() || value.isEmpty()) return null
    return QQCookie(name, value)
}

/**
 * 合并 `Set-Cookie`，同名 Cookie 以服务端最新下发者为准。
 *
 * 注意：QQ 登录链路（check_sig → authorize → LoginServer）之间**不按域裁剪**，
 * 每一跳共享同一份 Cookie 全量。实测按域过滤会让发往 graph.qq.com 的请求
 * 缺少会话 Cookie，导致 authorize 拿不到 code。
 */
internal fun mergeQQCookies(
    existing: List<QQCookie>,
    setCookieHeaders: List<String>
): List<QQCookie> {
    val result = existing.toMutableList()
    for (header in setCookieHeaders) {
        val parsed = runCatching { parseSetCookieHeader(header) }.getOrNull() ?: continue
        result.removeAll { it.name == parsed.name }
        result.add(parsed)
    }
    return result
}

internal fun buildQQCookieHeader(cookies: List<QQCookie>): String =
    cookies.joinToString("; ") { "${it.name}=${it.value}" }

/** 解析 ptuiCB 回调；68 是拒绝授权，不是二维码过期。 */
internal fun parseQQMusicQrCallback(body: String): QQMusicQrCallback {
    val values = Regex("'([^']*)'")
        .findAll(body.trim())
        .map { it.groupValues[1] }
        .toList()
    return when (values.firstOrNull()) {
        "0" -> QQMusicQrCallback(
            status = QQMusicQrStatus.CONFIRMED,
            jumpUrl = values.getOrNull(2)?.replace("\\x26", "&")
        )
        "66" -> QQMusicQrCallback(QQMusicQrStatus.WAITING)
        "67" -> QQMusicQrCallback(QQMusicQrStatus.SCANNED)
        "65" -> QQMusicQrCallback(QQMusicQrStatus.EXPIRED)
        "68" -> QQMusicQrCallback(QQMusicQrStatus.REFUSED)
        else -> QQMusicQrCallback(QQMusicQrStatus.FAILED)
    }
}

internal fun isUsableQQMusicCookies(cookies: Map<String, String>): Boolean {
    val uin = (cookies["uin"] ?: cookies["wxuin"]).orEmpty().trim().removePrefix("o")
    val key = sequenceOf("qm_keyst", "qqmusic_key", "music_key", "wxskey")
        .map { cookies[it].orEmpty().trim() }
        .firstOrNull { it.isNotEmpty() }.orEmpty()
    return uin.isNotEmpty() && uin.all(Char::isDigit) && key.isNotEmpty()
}

private val MUSIC_ID_KEYS = listOf("musicid", "str_musicid", "music_id", "uin")
private val MUSIC_KEY_KEYS = listOf("musickey", "str_musickey", "music_key")

/**
 * QQConnectLogin.LoginServer 会把 QQ 音乐身份放在 JSON 正文的
 * musicid/musickey 中，而不保证通过 Set-Cookie 返回。不同网关版本会把
 * userInfo 放在 req、req_0 或更深一层，且 str_ 前缀变体与 uin 均可能出现，
 * 因此逐个对象用候选字段名成对查找。
 */
internal fun mergeQQMusicLoginResponseCookies(
    cookies: Map<String, String>,
    body: String
): Map<String, String> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return cookies
    val credentials = findQQMusicCredentials(root) ?: return cookies
    val musicId = credentials.first.trim().removePrefix("o")
    val musicKey = credentials.second.trim()
    if (musicId.isEmpty() || !musicId.all(Char::isDigit) || musicKey.isEmpty()) return cookies
    return cookies + mapOf(
        "uin" to musicId,
        "qm_keyst" to musicKey,
        "qqmusic_key" to musicKey
    )
}

private fun findQQMusicCredentials(value: Any?, depth: Int = 0): Pair<String, String>? {
    if (depth > 8) return null
    return when (value) {
        is JSONObject -> {
            val musicId = MUSIC_ID_KEYS.asSequence()
                .mapNotNull { key ->
                    value.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()
                }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
            val musicKey = MUSIC_KEY_KEYS.asSequence()
                .mapNotNull { key -> value.optString(key).trim().takeIf { it.isNotEmpty() } }
                .firstOrNull()
                .orEmpty()
            if (musicId.isNotEmpty() && musicKey.isNotEmpty()) {
                musicId to musicKey
            } else {
                value.keys().asSequence()
                    .mapNotNull { key -> findQQMusicCredentials(value.opt(key), depth + 1) }
                    .firstOrNull()
            }
        }
        is JSONArray -> (0 until value.length()).asSequence()
            .mapNotNull { index -> findQQMusicCredentials(value.opt(index), depth + 1) }
            .firstOrNull()
        else -> null
    }
}

internal class QQMusicQrLoginClient {
    private val http = OkHttpClient.Builder()
        // check_sig 与 authorize 都必须读原始响应：前者从 Set-Cookie 取 p_skey，
        // 后者从 Location 取 code。跟随重定向会把这两个头吃掉。
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
            "?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump" +
            "&ptqrtoken=$token&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052" +
            "&action=0-0-${System.currentTimeMillis()}" +
            "&js_ver=23111510&js_type=1&pt_uistyle=40" +
            "&aid=716027609&daid=383&pt_3rd_aid=100497308"
        val request = Request.Builder()
            .url(url)
            // ptqrlogin 只需要这次二维码对应的 qrsig。重复同名 Cookie 会导致
            // 服务端在确认瞬间读取到错误会话，并把已确认二维码判成失效。
            .header("Cookie", "qrsig=${session.qrsig}")
            .header("Referer", LOGIN_REFERER)
            .header("User-Agent", UA)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val returnedCookies = response.headers.values("Set-Cookie")
                    .flatMap { parseCookieHeader(it).entries }
                    .associate { it.key to it.value }
                val callback = parseQQMusicQrCallback(body)
                NPLogger.d(LOG_TAG, "QR poll status=${callback.status}")
                when (callback.status) {
                    QQMusicQrStatus.CONFIRMED -> {
                        val exchange = exchangeForQQMusicCookies(
                            callback.jumpUrl,
                            seedCookies(session.cookies + returnedCookies)
                        )
                        QQMusicQrPollResult(
                            status = QQMusicQrStatus.CONFIRMED,
                            cookies = exchange.cookies,
                            exchangeFailure = exchange.failure
                        )
                    }
                    else -> QQMusicQrPollResult(callback.status)
                }
            }
        }.getOrElse { error ->
            NPLogger.w(LOG_TAG, "QR poll failed at ${error.javaClass.simpleName}")
            QQMusicQrPollResult(QQMusicQrStatus.FAILED)
        }
    }

    private fun hash33(value: String): String {
        var hash = 0L
        value.forEach { hash += (hash shl 5) + it.code }
        return (hash and 0x7fffffff).toString()
    }

    private fun seedCookies(values: Map<String, String>): List<QQCookie> =
        values.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .map { QQCookie(it.key.trim(), it.value.trim()) }

    private fun findCookie(cookies: List<QQCookie>, name: String): String =
        cookies.lastOrNull { it.name == name && it.value.isNotBlank() }?.value.orEmpty()

    /**
     * ptqrlogin 返回 0 只代表 QQ 通用登录完成，后续三跳都必需：
     * check_sig 换 p_skey → authorize 换 code → LoginServer 换 qm_keyst。
     *
     * 参数形态以公开可用实现为准（Rain120/qq-music-api · checkQQLoginQr）：
     * g_tk 由 **p_skey** 派生（这条链路不下发 skey），authorize 走 **POST + form body**。
     */
    private fun exchangeForQQMusicCookies(
        jumpUrl: String?,
        initialCookies: List<QQCookie>
    ): ExchangeResult {
        fun finish(
            cookies: List<QQCookie>,
            failure: QQMusicQrExchangeFailure
        ): ExchangeResult = ExchangeResult(toMusicCookies(cookies), failure)

        if (jumpUrl.isNullOrBlank()) {
            NPLogger.w(LOG_TAG, "QR exchange skipped: no jump url")
            return finish(initialCookies, QQMusicQrExchangeFailure.NO_JUMP_URL)
        }
        var cookies = initialCookies

        fun request(url: String, configure: Request.Builder.() -> Unit): okhttp3.Response? {
            val builder = Request.Builder().url(url)
            builder.configure()
            return runCatching {
                http.newCall(
                    builder
                        .header("Cookie", buildQQCookieHeader(cookies))
                        .header("User-Agent", UA)
                        .build()
                ).execute()
            }.getOrNull()
        }

        fun collect(response: okhttp3.Response) {
            cookies = mergeQQCookies(cookies, response.headers.values("Set-Cookie"))
        }

        // 第 1 跳：check_sig 换取 p_skey。
        val checkSig = request(jumpUrl) { header("Referer", LOGIN_REFERER) }
        if (checkSig == null) {
            NPLogger.w(LOG_TAG, "QR exchange check_sig request failed")
            return finish(cookies, QQMusicQrExchangeFailure.NO_SESSION_KEY)
        }
        checkSig.use { collect(it) }
        val pSkey = findCookie(cookies, "p_skey")
        NPLogger.d(LOG_TAG, "QR exchange check_sig p_skey=${pSkey.isNotBlank()}")
        if (pSkey.isBlank()) {
            return finish(cookies, QQMusicQrExchangeFailure.NO_SESSION_KEY)
        }
        // 关键：g_tk 来自 p_skey，这条链路上不会下发 skey。
        val gTk = gtk(pSkey)

        // 第 2 跳：authorize 换授权 code。必须 POST + form body，
        // 改 GET 会拿不到 302 的 Location。
        val form = buildAuthorizeForm(gTk)
        val authorize = request(AUTHORIZE_URL) {
            post(form.toRequestBody(FORM_MEDIA_TYPE))
            header("Referer", "https://graph.qq.com/")
            header("Origin", "https://graph.qq.com")
        }
        if (authorize == null) {
            NPLogger.w(LOG_TAG, "QR exchange authorize request failed")
            return finish(cookies, QQMusicQrExchangeFailure.NO_CODE)
        }
        val code = authorize.use { response ->
            collect(response)
            val location = response.header("Location")
            val snippet = response.body?.string().orEmpty().take(200)
            NPLogger.d(
                LOG_TAG,
                "QR exchange authorize http=${response.code} hasLocation=${!location.isNullOrBlank()} " +
                    "body=${snippet.replace(Regex("\\s+"), " ")}"
            )
            extractAuthorizationCode(location) ?: extractAuthorizationCode(snippet)
        }.orEmpty()
        NPLogger.d(LOG_TAG, "QR exchange authorize hasCode=${code.isNotBlank()}")
        if (code.isBlank()) {
            return finish(cookies, QQMusicQrExchangeFailure.NO_CODE)
        }

        // 第 3 跳：LoginServer 换音乐登录态。
        val musicPayload = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("g_tk", gTk)
                put("platform", "yqq")
                put("ct", 24)
                put("cv", 0)
            })
            put("req", JSONObject().apply {
                put("module", "QQConnectLogin.LoginServer")
                put("method", "QQLogin")
                put("param", JSONObject().put("code", code))
            })
        }.toString()
        val loginResponse = request("https://u.y.qq.com/cgi-bin/musicu.fcg") {
            post(musicPayload.toRequestBody(JSON_MEDIA_TYPE))
            header("Referer", "https://y.qq.com/")
        }
        if (loginResponse == null) {
            NPLogger.w(LOG_TAG, "QR exchange loginServer request failed")
            return finish(cookies, QQMusicQrExchangeFailure.NO_MUSIC_KEY)
        }
        val merged = loginResponse.use { response ->
            collect(response)
            val body = response.body?.string().orEmpty()
            NPLogger.d(
                LOG_TAG,
                "QR exchange loginServer http=${response.code} " +
                    "body=${body.take(300).replace(Regex("\\s+"), " ")}"
            )
            mergeQQMusicLoginResponseCookies(cookies.associate { it.name to it.value }, body)
        }
        val usable = isUsableQQMusicCookies(merged)
        NPLogger.d(LOG_TAG, "QR exchange loginServer usable=$usable")
        if (!usable) {
            return finish(cookies, QQMusicQrExchangeFailure.NO_MUSIC_KEY)
        }
        // merged 里含 p_skey / qrsig 等 QQ 账号会话密钥，而这个 map 会被写入
        // 本地存储，只保留音乐接口真正需要的字段。
        return ExchangeResult(
            cookies = merged.filterKeys { it in MUSIC_COOKIE_KEYS },
            failure = QQMusicQrExchangeFailure.NONE
        )
    }

    private fun buildAuthorizeForm(gTk: Int): String = buildString {
        fun field(name: String, value: String) {
            if (isNotEmpty()) append('&')
            append(name).append('=').append(URLEncoder.encode(value, "UTF-8"))
        }
        field("response_type", "code")
        field("client_id", MUSIC_CLIENT_ID)
        field("redirect_uri", MUSIC_REDIRECT_URI)
        field("scope", MUSIC_SCOPE)
        field("state", "state")
        field("switch", "")
        field("from_ptlogin", "1")
        field("src", "1")
        field("update_auth", "1")
        field("openapi", "1010_1030")
        field("g_tk", gTk.toString())
        field("auth_time", System.currentTimeMillis().toString())
        field("ui", UUID.randomUUID().toString())
    }

    /** 只保留音乐接口真正需要的字段，避免把 QQ 账号会话密钥写进本地存储。 */
    private fun toMusicCookies(cookies: List<QQCookie>): Map<String, String> =
        cookies
            .filter { it.name in MUSIC_COOKIE_KEYS }
            .associate { it.name to it.value }
            .filterValues { it.isNotBlank() }

    private fun extractAuthorizationCode(source: String?): String? {
        if (source.isNullOrBlank()) return null
        val raw = Regex("(?:[?&])code=([^&\"'\\s\\\\]+)").find(source)?.groupValues?.getOrNull(1)
        return raw?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault("") }
            ?.takeIf { it.isNotBlank() }
    }

    private fun gtk(value: String): Int {
        var hash = 5381
        value.forEach { hash += (hash shl 5) + it.code }
        return hash and 0x7fffffff
    }

    private fun parseCookieHeader(header: String): Map<String, String> =
        runCatching {
            HttpCookie.parse(header).associate { it.name to it.value }
        }.getOrDefault(emptyMap())

    private data class ExchangeResult(
        val cookies: Map<String, String>,
        val failure: QQMusicQrExchangeFailure
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json;charset=utf-8".toMediaType()
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        const val AUTHORIZE_URL = "https://graph.qq.com/oauth2.0/authorize"
        const val MUSIC_CLIENT_ID = "100497308"
        const val MUSIC_SCOPE = "get_user_info,get_app_friends"
        const val MUSIC_REDIRECT_URI =
            "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/"
        val MUSIC_COOKIE_KEYS = setOf(
            "uin",
            "wxuin",
            "qm_keyst",
            "qqmusic_key",
            "music_key",
            "wxskey"
        )
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        const val LOG_TAG = "NERI-QQMusicQrLogin"
        const val LOGIN_REFERER = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin" +
            "?appid=716027609&style=20" +
            "&s_url=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump" +
            "&maskOpacity=60&daid=383&target=self"
    }
}