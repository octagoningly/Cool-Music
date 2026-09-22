package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

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
 * File: moe.ouom.neriplayer.core.player.resolver.lxmusic/LxMusicLyricsSource
 */

private const val TAG = "NERI-LxMusicSource"
private const val LX_LYRICS_CACHE_LIMIT = 128

/** 在线音源同步到的歌词 */
internal data class LxFetchedLyric(
    val sourceId: String,
    val lyric: String,
    val translatedLyric: String? = null
)

/**
 * 从在线音源对应的平台同步歌词。
 *
 * 复用跨平台取流时已经拿到的曲目 ID（没有则现搜），
 * 平台歌词接口本身是公开的，因此不需要音源脚本支持 lyric 动作。
 */
internal suspend fun fetchLxSourceLyric(
    song: SongItem,
    platformOverride: String? = null
): LxFetchedLyric? {
    val songKey = song.stableKey()
    val cacheKey = "$songKey|${platformOverride.orEmpty()}"
    LxLyricsCache.get(cacheKey)?.let { return it }

    val client = runCatching { AppContainer.sharedOkHttpClient }.getOrNull() ?: return null
    val directPlatform = song.lxSearchHitOrNull()?.sourceId
    val platforms = platformOverride?.let { listOf(it) } ?: listOfNotNull(
        directPlatform?.takeIf { it in LX_LYRICS_PLATFORM_ORDER }
    ) + LX_LYRICS_PLATFORM_ORDER.filterNot { it == directPlatform }

    for (platform in platforms) {
        val hit = resolveLyricLookupHit(song, platform) ?: continue
        val lyric = when (platform) {
            LX_QQ_PLATFORM_ID -> fetchLxQqLyric(client, hit.songMid)
            LX_KUGOU_PLATFORM_ID -> {
                val hash = hit.qualityHashes["320k"]
                    ?: hit.qualityHashes["128k"]
                    ?: hit.qualityHashes["flac"]
                if (hash == null) null else fetchLxKugouLyric(client, hash)
            }

            else -> null
        } ?: continue
        if (lyric.lyric.isBlank()) continue
        NPLogger.w(
            TAG,
            "LX lyrics synced: song=${song.name}, platform=$platform, " +
                "songmid=${hit.songMid}, length=${lyric.lyric.length}"
        )
        LxLyricsCache.put(cacheKey, lyric)
        return lyric
    }
    return null
}

/** 歌词取哪个平台的曲目 ID：优先复用播放时命中的平台缓存 */
private suspend fun resolveLyricLookupHit(
    song: SongItem,
    platform: String
): LxCrossPlatformHit? {
    song.lxSearchHitOrNull()?.takeIf { it.sourceId == platform }?.let { return it }
    val cached = LxCrossPlatformHitCache.get("${song.stableKey()}|$platform")
    if (cached != null) return cached
    return searchLxCrossPlatformHit(song, platform)
}

/** QQ 音乐歌词：一次请求即可拿到 LRC */
private suspend fun fetchLxQqLyric(client: OkHttpClient, songMid: String): LxFetchedLyric? {
    if (songMid.isBlank()) return null
    val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=" +
        java.net.URLEncoder.encode(songMid, "UTF-8") + "&format=json&nobase64=1&g_tk=5381"
    val body = fetchLyricText(client, url, referer = "https://y.qq.com/portal/player.html")
        ?: return null
    return parseLxQqLyricBody(body)
}

/** 解析 QQ 歌词响应（nobase64=1 时 lyric/trans 都是明文 LRC） */
internal fun parseLxQqLyricBody(body: String): LxFetchedLyric? {
    if (body.isBlank()) return null
    return runCatching {
        val json = JSONObject(body)
        val lyric = json.optString("lyric")
        if (lyric.isBlank()) return@runCatching null
        LxFetchedLyric(
            sourceId = LX_QQ_PLATFORM_ID,
            lyric = lyric,
            translatedLyric = json.optString("trans").takeIf { it.isNotBlank() }
        )
    }.getOrElse {
        NPLogger.w(TAG, "QQ lyric parse failed: ${it.message}")
        null
    }
}

/**
 * 酷狗歌词：先按 hash 查歌词 id + accesskey，再取 LRC（返回 base64）。
 */
private suspend fun fetchLxKugouLyric(client: OkHttpClient, hash: String): LxFetchedLyric? {
    if (hash.isBlank()) return null
    val searchUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=&hash=" +
        java.net.URLEncoder.encode(hash, "UTF-8")
    val searchBody = fetchLyricText(client, searchUrl, referer = "https://www.kugou.com/")
        ?: return null
    val candidate = runCatching {
        JSONObject(searchBody).optJSONArray("candidates")?.optJSONObject(0)
    }.getOrNull() ?: return null

    val lyricId = candidate.optString("id")
    val accessKey = candidate.optString("accesskey")
    if (lyricId.isBlank() || accessKey.isBlank()) return null

    val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=" +
        java.net.URLEncoder.encode(lyricId, "UTF-8") + "&accesskey=" +
        java.net.URLEncoder.encode(accessKey, "UTF-8") + "&fmt=lrc&charset=utf8"
    val downloadBody = fetchLyricText(client, downloadUrl, referer = "https://www.kugou.com/")
        ?: return null
    return parseLxKugouLyricDownloadBody(downloadBody)
}

/** 解析酷狗歌词下载响应（content 字段是 base64 的 LRC） */
internal fun parseLxKugouLyricDownloadBody(body: String): LxFetchedLyric? {
    if (body.isBlank()) return null
    return runCatching {
        val content = JSONObject(body).optString("content")
        if (content.isBlank()) return@runCatching null
        val decoded = runCatching {
            String(java.util.Base64.getDecoder().decode(content), Charsets.UTF_8)
        }.getOrElse { content }
        if (decoded.isBlank()) return@runCatching null
        LxFetchedLyric(sourceId = LX_KUGOU_PLATFORM_ID, lyric = decoded)
    }.getOrElse {
        NPLogger.w(TAG, "Kugou lyric parse failed: ${it.message}")
        null
    }
}

private suspend fun fetchLyricText(
    client: OkHttpClient,
    url: String,
    referer: String
): String? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        .header("Referer", referer)
        .header("Accept", "application/json")
        .get()
        .build()
    runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                NPLogger.w(TAG, "LX lyric http ${response.code}: ${url.take(80)}")
                return@use null
            }
            response.body?.string()
        }
    }.getOrElse { error ->
        NPLogger.w(TAG, "LX lyric request failed: ${error.message}")
        null
    }
}

/** 歌词按「QQ → 酷狗」顺序尝试；酷我歌词接口需要额外参数，暂未接入 */
private val LX_LYRICS_PLATFORM_ORDER = listOf(LX_QQ_PLATFORM_ID, LX_KUGOU_PLATFORM_ID)

private object LxLyricsCache {
    private val cache = ConcurrentHashMap<String, LxFetchedLyric>()

    fun get(key: String): LxFetchedLyric? = cache[key]

    fun put(key: String, value: LxFetchedLyric) {
        if (cache.size >= LX_LYRICS_CACHE_LIMIT) cache.clear()
        cache[key] = value
    }

    fun clear() = cache.clear()
}
