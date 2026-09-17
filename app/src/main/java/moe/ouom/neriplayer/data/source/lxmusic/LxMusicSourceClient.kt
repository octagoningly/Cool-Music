package moe.ouom.neriplayer.data.source.lxmusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.logging.NPLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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
 * File: moe.ouom.neriplayer.data.source.lxmusic/LxMusicSourceClient
 * Updated: 2026/3/23
 */

class LxMusicSourceClient(private val okHttpClient: OkHttpClient) {
    companion object {
        private const val TAG = "LxMusicSourceClient"
        private const val USER_AGENT = "NeriPlayer/1.0 (LX Music Source Client)"
        private const val MAX_BODY_BYTES = 2L * 1024L * 1024L
    }

    private val fetchClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchSourceDefinition(sourceUrl: String): Result<LxSourceDefinition> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = executeGet(sourceUrl)
                LxMusicSourceParser.parseSourceDefinition(body)
                    ?: if (LxMusicSourceParser.looksLikeLxMusicJsSource(body)) {
                        throw IOException("JS source not supported")
                    } else {
                        throw IOException("Invalid LX source JSON")
                    }
            }
        }

    /** 拉取原始正文，用于区分 JSON / JS 音源 */
    suspend fun fetchRawSourceBody(sourceUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching { executeGet(sourceUrl) }
        }

    suspend fun search(
        searchApiUrl: String,
        keyword: String,
        limit: Int = 8,
        page: Int = 1
    ): List<LxSearchItem> = withContext(Dispatchers.IO) {
        if (searchApiUrl.isBlank() || keyword.isBlank()) return@withContext emptyList()
        runCatching {
            val url = buildApiUrl(
                baseUrl = searchApiUrl,
                params = mapOf(
                    "keyword" to keyword,
                    "search" to keyword,
                    "key" to keyword,
                    "limit" to limit.toString(),
                    "count" to limit.toString(),
                    "page" to page.toString(),
                    "pagesize" to limit.toString()
                )
            )
            val body = executeGet(url)
            LxMusicSourceParser.parseSearchResponse(body).take(limit)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            NPLogger.w(TAG, "LX search failed: keyword=$keyword, error=${error.message}")
            emptyList()
        }
    }

    suspend fun resolveSongUrl(
        songUrlApiUrl: String,
        musicId: String,
        quality: String
    ): LxSongUrlResult = withContext(Dispatchers.IO) {
        if (songUrlApiUrl.isBlank() || musicId.isBlank()) {
            return@withContext LxSongUrlResult.Failure
        }
        runCatching {
            val url = buildApiUrl(
                baseUrl = songUrlApiUrl,
                params = mapOf(
                    "id" to musicId,
                    "musicId" to musicId,
                    "quality" to quality,
                    "br" to quality
                )
            )
            val body = executeGet(url)
            LxMusicSourceParser.parseSongUrlResponse(body)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            NPLogger.w(TAG, "LX song url failed: id=$musicId, quality=$quality, error=${error.message}")
            LxSongUrlResult.Failure
        }
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return fetchClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body.string()
            if (body.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) {
                throw IOException("Response too large")
            }
            body
        }
    }

    /**
     * 保留 baseUrl 中已有 query，并合并 params 中尚未出现的 key。
     * 这样同时兼容 `?keyword=`、`?search=` 等不同源站约定。
     */
    private fun buildApiUrl(baseUrl: String, params: Map<String, String>): String {
        val existingKeys = Regex("([?&])([^=&]+)=", RegexOption.IGNORE_CASE)
            .findAll(baseUrl)
            .map { it.groupValues[2].lowercase() }
            .toSet()
        val missing = params.filterKeys { it.lowercase() !in existingKeys }
        if (missing.isEmpty()) return baseUrl
        val separator = when {
            baseUrl.contains("?") -> if (baseUrl.endsWith("?") || baseUrl.endsWith("&")) "" else "&"
            else -> "?"
        }
        val query = missing.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return baseUrl + separator + query
    }
}
