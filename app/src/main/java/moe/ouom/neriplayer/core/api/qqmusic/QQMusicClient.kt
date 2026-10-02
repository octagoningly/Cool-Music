package moe.ouom.neriplayer.core.api.qqmusic

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.core.api.qqmusic/QQMusicClient
 * Created: 2026/9/29
 */

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.data.auth.qqmusic.QQMusicAuthBundle
import moe.ouom.neriplayer.data.auth.qqmusic.QQMusicCookieRepository
import moe.ouom.neriplayer.data.platform.qqmusic.QQMusicQuality
import moe.ouom.neriplayer.data.platform.qqmusic.QQMusicStreamInfo
import moe.ouom.neriplayer.core.logging.NPLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class QQMusicPlaylistSummary(
    val dissId: Long,
    val name: String,
    val coverUrl: String? = null,
    val songCount: Int = 0
)

internal fun parseQQMusicUserPlaylists(body: String): List<QQMusicPlaylistSummary> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val data = root.optJSONObject("data") ?: root.optJSONObject("playlist") ?: root
    val list = data.optJSONArray("disslist")
        ?: data.optJSONArray("list")
        ?: data.optJSONArray("playlists")
        ?: return emptyList()
    return buildList {
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val id = item.optLong("dissid", 0L).takeIf { it > 0L }
                ?: item.optLong("dirid", 0L).takeIf { it > 0L }
                ?: item.optLong("tid", 0L).takeIf { it > 0L }
                ?: continue
            val name = item.optString("dissname")
                .ifBlank { item.optString("name") }
                .ifBlank { item.optString("title") }
                .trim()
                .takeIf { it.isNotBlank() }
                ?: continue
            val cover = item.optString("cover_url")
                .ifBlank { item.optString("coverUrl") }
                .ifBlank { item.optString("logo") }
                .takeIf { it.isNotBlank() }
            val count = item.optInt("songnum", 0).takeIf { it > 0 }
                ?: item.optInt("song_cnt", 0).takeIf { it > 0 }
                ?: 0
            add(QQMusicPlaylistSummary(id, name, cover, count))
        }
    }.distinctBy { it.dissId }
}

/**
 * 取流失败原因分类。
 *
 * - [NOT_LOGGED_IN]：本地无可用登录 Cookie（uin/music key 缺失或格式非法）
 * - [NO_AUTHORITY_OR_NO_FILE]：服务端 104003——登录态下仍拒绝，可能是
 *   会员音质墙 / 版权墙 / 该音质文件不存在（上层可结合详情 size/pay 字段细分）
 * - [MALFORMED_RESPONSE]：响应缺字段或结构不符
 * - [NETWORK]：HTTP/IO 层失败
 */
enum class QQMusicPlayUrlFailure {
    NOT_LOGGED_IN,
    NO_AUTHORITY_OR_NO_FILE,
    MALFORMED_RESPONSE,
    NETWORK
}

sealed class QQMusicPlayUrlResult {
    data class Success(val stream: QQMusicStreamInfo) : QQMusicPlayUrlResult()
    data class Failure(val reason: QQMusicPlayUrlFailure) : QQMusicPlayUrlResult()
}

/** 服务端 104003（匿名/无权限/无文件统一业务码） */
private const val QQ_RESULT_NO_AUTHORITY = 104003

private const val PLAY_URL_ENDPOINT = "https://u.y.qq.com/cgi-bin/musicu.fcg"
private const val PLAY_URL_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36"
private const val PLAY_URL_REFERER = "https://y.qq.com/"

/**
 * 构造取流请求 body（`{comm, req}` 顶层结构，规格见 docs/qq-music-link-notes.md §1）。
 * 为纯函数，便于单测锁定结构与 g_tk 派生。
 */
internal fun buildPlayUrlRequestJson(
    auth: QQMusicAuthBundle,
    songmid: String,
    filename: String,
    guid: String
): String {
    val gtk = auth.gtk() ?: 0
    val uin = auth.uin().orEmpty()
    val musicKey = auth.musicKey().orEmpty()
    val param = JSONObject().apply {
        put("filename", org.json.JSONArray().put(filename))
        put("guid", guid)
        put("songmid", org.json.JSONArray().put(songmid))
        put("songtype", org.json.JSONArray().put(0))
        put("uin", uin)
        put("loginflag", 1)
        put("platform", "20")
    }
    val req = JSONObject().apply {
        put("module", "vkey.GetVkeyServer")
        put("method", "CgiGetVkey")
        put("param", param)
    }
    val comm = JSONObject().apply {
        put("g_tk", gtk)
        put("platform", "yqq")
        put("ct", 24)
        put("cv", 0)
        // 服务端部分场景读取；与 comm.g_tk 一同构成登录态签名
        if (musicKey.isNotEmpty()) {
            put("authst", musicKey)
        }
    }
    return JSONObject().apply {
        put("comm", comm)
        put("req", req)
    }.toString()
}

internal sealed class QQMusicPlayUrlParseResult {
    data class Success(val stream: QQMusicStreamInfo) : QQMusicPlayUrlParseResult()
    data class Denied(val httpCode: Int?) : QQMusicPlayUrlParseResult()
    object Malformed : QQMusicPlayUrlParseResult()
}

/**
 * 解析取流响应。成功时拼出完整直链：sip 域名 + purl。
 */
internal fun parsePlayUrlResponse(
    body: String,
    songmid: String,
    quality: QQMusicQuality,
    guid: String
): QQMusicPlayUrlParseResult {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return QQMusicPlayUrlParseResult.Malformed
    // 响应镜像请求字段名：req（兼容 req_0）
    val reqObj = root.optJSONObject("req") ?: root.optJSONObject("req_0")
        ?: return QQMusicPlayUrlParseResult.Malformed
    val data = reqObj.optJSONObject("data") ?: return QQMusicPlayUrlParseResult.Malformed
    val midurlinfo = data.optJSONArray("midurlinfo")
    val entry = midurlinfo?.optJSONObject(0) ?: return QQMusicPlayUrlParseResult.Malformed

    val resultCode = entry.optInt("result", -1)
    val purl = entry.optString("purl", "")
    if (resultCode == QQ_RESULT_NO_AUTHORITY || purl.isBlank()) {
        return QQMusicPlayUrlParseResult.Denied(resultCode.takeIf { it > 0 })
    }

    val sipArray = data.optJSONArray("sip") ?: return QQMusicPlayUrlParseResult.Malformed
    val sipList = buildList {
        for (i in 0 until sipArray.length()) {
            add(sipArray.optString(i, ""))
        }
    }.filter { it.isNotBlank() }
    if (sipList.isEmpty()) return QQMusicPlayUrlParseResult.Malformed

    val domain = sipList.firstOrNull { !it.startsWith("http://ws") } ?: sipList.first()
    val vkey = runCatching {
        val query = purl.substringAfter('?', "")
        query.split('&')
            .firstOrNull { it.startsWith("vkey=") }
            ?.removePrefix("vkey=")
            .orEmpty()
    }.getOrDefault("")
    val fromTag = runCatching {
        val query = purl.substringAfter('?', "")
        query.split('&')
            .firstOrNull { it.startsWith("fromtag=") }
            ?.removePrefix("fromtag=")
    }.getOrNull()

    val fullUrl = domain.trimEnd('/') + "/" + purl.trimStart('/')
    return QQMusicPlayUrlParseResult.Success(
        QQMusicStreamInfo(
            songmid = songmid,
            qualityKey = quality.key,
            filename = quality.fileNameFor(songmid),
            url = fullUrl,
            mimeType = quality.mimeType(),
            vkey = vkey,
            guid = guid,
            fromTag = fromTag
        )
    )
}

/**
 * QQ 音乐取流客户端。
 *
 * 匿名请求一律 104003（见调研笔记 §0）；必须携带 [QQMusicCookieRepository] 中的登录态，
 * comm.g_tk 由 qm_keyst 实时派生（[QQMusicAuthBundle.gtk]）。
 */
class QQMusicClient(
    private val cookieRepo: QQMusicCookieRepository,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : QQMusicPlayUrlSource {
    companion object {
        private const val LOG_TAG = "NERI-QQMusicClient"

        fun randomGuid(): String = Random.nextInt(100_000_000, 999_999_999).toString()
    }

    override suspend fun getPlayUrl(
        songmid: String,
        quality: QQMusicQuality,
        guid: String
    ): QQMusicPlayUrlResult = withContext(Dispatchers.IO) {
        val auth = cookieRepo.getAuthBundleOnce()
        if (!auth.hasLoginCookies()) {
            NPLogger.w(LOG_TAG, "getPlayUrl skipped: no login cookies (song=$songmid q=${quality.key})")
            return@withContext QQMusicPlayUrlResult.Failure(QQMusicPlayUrlFailure.NOT_LOGGED_IN)
        }

        val filename = quality.fileNameFor(songmid)
        val bodyJson = buildPlayUrlRequestJson(auth, songmid, filename, guid)
        val cookieHeader = buildString {
            append("uin=").append(auth.uin().orEmpty())
            append("; qm_keyst=").append(auth.musicKey().orEmpty())
            append("; qqmusic_key=").append(auth.musicKey().orEmpty())
        }

        val request = Request.Builder()
            .url(PLAY_URL_ENDPOINT)
            .header("Referer", PLAY_URL_REFERER)
            .header("User-Agent", PLAY_URL_UA)
            .header("Cookie", cookieHeader)
            .post(bodyJson.toRequestBody("application/json;charset=utf-8".toMediaType()))
            .build()

        val response = runCatching {
            httpClient.newCall(request).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        }.getOrElse { error ->
            NPLogger.w(LOG_TAG, "getPlayUrl network error song=$songmid q=${quality.key}", error)
            return@withContext QQMusicPlayUrlResult.Failure(QQMusicPlayUrlFailure.NETWORK)
        }

        val (httpCode, body) = response
        if (httpCode !in 200..299) {
            NPLogger.w(LOG_TAG, "getPlayUrl http=$httpCode song=$songmid q=${quality.key}")
            return@withContext QQMusicPlayUrlResult.Failure(QQMusicPlayUrlFailure.NETWORK)
        }

        when (val parsed = parsePlayUrlResponse(body, songmid, quality, guid)) {
            is QQMusicPlayUrlParseResult.Success -> {
                NPLogger.d(
                    LOG_TAG,
                    "getPlayUrl ok song=$songmid q=${quality.key} url=${parsed.stream.url.take(80)}..."
                )
                QQMusicPlayUrlResult.Success(parsed.stream)
            }
            is QQMusicPlayUrlParseResult.Denied -> {
                NPLogger.w(
                    LOG_TAG,
                    "getPlayUrl denied song=$songmid q=${quality.key} code=${parsed.httpCode}"
                )
                QQMusicPlayUrlResult.Failure(QQMusicPlayUrlFailure.NO_AUTHORITY_OR_NO_FILE)
            }
            QQMusicPlayUrlParseResult.Malformed -> {
                NPLogger.w(LOG_TAG, "getPlayUrl malformed song=$songmid q=${quality.key}")
                QQMusicPlayUrlResult.Failure(QQMusicPlayUrlFailure.MALFORMED_RESPONSE)
            }
        }
    }

    suspend fun getUserCreatedPlaylists(): List<QQMusicPlaylistSummary> = withContext(Dispatchers.IO) {
        val auth = cookieRepo.getAuthBundleOnce()
        val uin = auth.uin().orEmpty()
        if (uin.isBlank() || !auth.hasLoginCookies()) return@withContext emptyList()
        val url = "https://c.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss".toHttpUrl()
            .newBuilder()
            .addQueryParameter("hostUin", "0")
            .addQueryParameter("hostuin", uin)
            .addQueryParameter("sin", "0")
            .addQueryParameter("size", "200")
            .addQueryParameter("g_tk", auth.gtk().toString())
            .addQueryParameter("loginUin", uin)
            .addQueryParameter("format", "json")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .build()
        val cookie = "uin=${auth.uin()}; qm_keyst=${auth.musicKey()}; qqmusic_key=${auth.musicKey()}"
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://y.qq.com/portal/profile.html")
            .header("User-Agent", PLAY_URL_UA)
            .header("Cookie", cookie)
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parseQQMusicUserPlaylists(response.body?.string().orEmpty())
            }
        }.getOrElse { error ->
            NPLogger.w(LOG_TAG, "getUserCreatedPlaylists failed", error)
            emptyList()
        }
    }
}
