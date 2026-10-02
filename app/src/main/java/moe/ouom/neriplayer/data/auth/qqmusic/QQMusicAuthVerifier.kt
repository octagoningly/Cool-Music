package moe.ouom.neriplayer.data.auth.qqmusic

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
 * File: moe.ouom.neriplayer.data.auth.qqmusic/QQMusicAuthVerifier
 * Created: 2026/9/29
 */

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.api.qqmusic.buildQQMusicCookieHeader
import moe.ouom.neriplayer.core.logging.NPLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 登录态在线验证结果 */
enum class QQMusicAuthVerifyResult {
    /** 服务端认可当前 Cookie */
    VALID,

    /** 服务端返回未登录（code=1000），Cookie 已失效，需要引导重新登录 */
    EXPIRED,

    /** 网络失败，无法判断 */
    UNKNOWN
}

/**
 * QQ 音乐登录态在线验证。
 *
 * 复用 jsososo 同款 profile 接口：code=1000 表示未登录/失效
 * （docs/qq-music-link-notes.md §4）。
 */
class QQMusicAuthVerifier(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val LOG_TAG = "NERI-QQMusicAuthVerifier"
        private const val VERIFY_URL =
            "https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg" +
                "?cid=205360838&reqfrom=1&format=json&inCharset=utf-8&outCharset=utf-8" +
                "&notice=0&platform=yqq.json&needNewCode=0&g_tk=5381"

        internal const val CODE_NOT_LOGGED_IN = 1000
    }

    suspend fun verify(cookies: Map<String, String>): QQMusicAuthVerifyResult =
        withContext(Dispatchers.IO) {
            val auth = QQMusicAuthBundle(cookies = cookies).normalized()
            if (!auth.hasLoginCookies()) {
                return@withContext QQMusicAuthVerifyResult.EXPIRED
            }

            val cookieHeader = buildQQMusicCookieHeader(auth)
            val request = Request.Builder()
                .url("$VERIFY_URL&uin=${auth.uin().orEmpty()}")
                .header("Referer", "https://y.qq.com/")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/154.0.0.0 Safari/537.36"
                )
                .header("Cookie", cookieHeader)
                .get()
                .build()

            val body = runCatching {
                httpClient.newCall(request).execute().use { resp ->
                    if (resp.code !in 200..299) {
                        return@withContext QQMusicAuthVerifyResult.UNKNOWN
                    }
                    resp.body?.string().orEmpty()
                }
            }.getOrElse { error ->
                NPLogger.w(LOG_TAG, "verify network error", error)
                return@withContext QQMusicAuthVerifyResult.UNKNOWN
            }

            val code = runCatching { JSONObject(body).optInt("code", -1) }.getOrDefault(-1)
            when (code) {
                CODE_NOT_LOGGED_IN -> {
                    NPLogger.d(LOG_TAG, "verify: cookie expired (code=1000)")
                    QQMusicAuthVerifyResult.EXPIRED
                }
                0 -> {
                    NPLogger.d(LOG_TAG, "verify: valid")
                    QQMusicAuthVerifyResult.VALID
                }
                else -> {
                    NPLogger.w(LOG_TAG, "verify: unexpected code=$code")
                    QQMusicAuthVerifyResult.UNKNOWN
                }
            }
        }
}
