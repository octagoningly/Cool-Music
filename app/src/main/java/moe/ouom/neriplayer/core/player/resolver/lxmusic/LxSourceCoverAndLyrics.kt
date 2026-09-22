package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.model.SongItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线搜索引擎选择、音源封面/歌词开关，以及无封面时从音源补封面。
 */

internal val LX_ONLINE_SEARCH_PLATFORM_ORDER = listOf(
    LX_QQ_PLATFORM_ID,
    LX_KUGOU_PLATFORM_ID,
    LX_KUWO_PLATFORM_ID,
    LX_NETEASE_PLATFORM_ID
)

internal fun parseLxOnlineSearchEngines(raw: String?): Set<String> {
    val tokens = raw?.split(',', ';', ' ')
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()
    val known = tokens.filter { it in LX_ONLINE_SEARCH_PLATFORM_ORDER }.toSet()
    return known.ifEmpty { LX_ONLINE_SEARCH_PLATFORM_ORDER.toSet() }
}

internal fun encodeLxOnlineSearchEngines(ids: Set<String>): String {
    return LX_ONLINE_SEARCH_PLATFORM_ORDER.filter { it in ids }.joinToString(",")
}

internal suspend fun lxOnlineSearchEngines(): Set<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            parseLxOnlineSearchEngines(
                AppContainer.settingsRepo.lxOnlineSearchEnginesFlow.first()
            )
        }.getOrDefault(LX_ONLINE_SEARCH_PLATFORM_ORDER.toSet())
    }
}

internal suspend fun isLxSourceCoverFallbackEnabled(): Boolean {
    return withContext(Dispatchers.IO) {
        runCatching { AppContainer.settingsRepo.lxSourceCoverFallbackEnabledFlow.first() }
            .getOrDefault(true)
    }
}

internal suspend fun isLxSourceLyricsFallbackEnabled(): Boolean {
    return withContext(Dispatchers.IO) {
        runCatching { AppContainer.settingsRepo.lxSourceLyricsFallbackEnabledFlow.first() }
            .getOrDefault(true)
    }
}

internal fun normalizeLxCoverUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    if (value.startsWith("//")) return "https:$value"
    if (value.startsWith("http://")) return value.replaceFirst("http://", "https://")
    if (value.startsWith("https://")) return value
    return null
}

internal fun isPlaceholderLxCoverUrl(raw: String?): Boolean {
    val value = raw?.lowercase().orEmpty()
    if (value.isBlank()) return true
    return PLACEHOLDER_COVER_MARKERS.any { value.contains(it) }
}

private val PLACEHOLDER_COVER_MARKERS = listOf(
    "default",
    "unknown",
    "nopic",
    "no_pic",
    "no-cover",
    "no_cover",
    "placeholder",
    "record.png",
    "record.jpg",
    "vinyl",
    "album_default",
    "default_240",
    "default_500",
    "softmusic/common/default",
    "softmusic/common/unknown",
    "/star/default",
    "img1.kuwo.cn/star/albumcover/000",
    "imge.kugou.com/softmusic/common/0.jpg",
    "imge.kugou.com/mfcc/"
)

/** 搜索响应里能直接拿到封面时，按平台拼出可用 https 地址 */
internal fun lxSourceCoverUrlFromHit(hit: LxCrossPlatformHit): String? {
    val direct = normalizeLxCoverUrl(hit.coverUrl)
    if (direct != null && !isPlaceholderLxCoverUrl(direct)) return direct
    return when (hit.sourceId) {
        LX_QQ_PLATFORM_ID -> hit.albumId
            .takeIf { it.isNotBlank() && it != "0" && it.all { ch -> ch.isLetterOrDigit() } }
            ?.let { "https://y.qq.com/music/photo_new/T002R300x300M000${it}.jpg" }

        LX_KUGOU_PLATFORM_ID -> null
        LX_KUWO_PLATFORM_ID -> null
        else -> null
    }
}

private data class LxCoverCacheKey(val platform: String, val id: String)

private val lxSourceCoverCache = ConcurrentHashMap<LxCoverCacheKey, String>()
private val lxSourceCoverMisses = ConcurrentHashMap.newKeySet<LxCoverCacheKey>()

/**
 * 歌曲没有封面时，按音源平台补一张。
 * 优先搜索结果里的封面；仍为空再调公开接口。
 * 注意：ConcurrentHashMap 不允许 null value， miss 必须用单独集合。
 */
internal suspend fun resolveLxSourceCoverUrl(
    song: SongItem,
    client: OkHttpClient = AppContainer.sharedOkHttpClient
): String? {
    val existing = normalizeLxCoverUrl(song.coverUrl)
    if (existing != null && !isPlaceholderLxCoverUrl(existing)) return existing
    song.lxSearchHitOrNull()?.let { hit ->
        lxSourceCoverUrlFromHit(hit)?.let { return it }
    }
    val platform = song.channelId?.removePrefix(LX_SEARCH_CHANNEL_PREFIX) ?: return null
    val musicId = song.audioId?.takeIf { it.isNotBlank() } ?: return null
    val cacheKey = LxCoverCacheKey(platform, musicId)
    lxSourceCoverCache[cacheKey]?.let { return it }
    if (cacheKey in lxSourceCoverMisses) return null
    val albumId = song.lxSearchHitOrNull()?.albumId.orEmpty()
    val resolved = withContext(Dispatchers.IO) {
        runCatching {
            when (platform) {
                LX_KUGOU_PLATFORM_ID -> fetchKugouCover(client, musicId, albumId)
                LX_KUWO_PLATFORM_ID -> fetchKuwoCover(client, musicId)
                LX_QQ_PLATFORM_ID -> fetchQqCover(client, musicId)
                else -> null
            }
        }.getOrNull()
    }
    val usable = resolved?.takeIf { !isPlaceholderLxCoverUrl(it) }
    if (usable == null) {
        if (lxSourceCoverMisses.size >= 256) lxSourceCoverMisses.clear()
        lxSourceCoverMisses.add(cacheKey)
        return null
    }
    if (lxSourceCoverCache.size >= 256) {
        lxSourceCoverCache.clear()
        lxSourceCoverMisses.clear()
    }
    lxSourceCoverCache[cacheKey] = usable
    return usable
}

/** 搜索结果里缺封面的曲目，有限并发补齐。补图失败不得影响搜索本身。 */
internal suspend fun fillLxSongCoverGaps(
    songs: List<SongItem>,
    client: OkHttpClient = AppContainer.sharedOkHttpClient
): List<SongItem> {
    if (songs.isEmpty()) return songs
    return runCatching {
        val missing = songs.mapIndexedNotNull { index, song ->
            val cover = normalizeLxCoverUrl(song.coverUrl)
            if (cover == null || isPlaceholderLxCoverUrl(cover)) index else null
        }
        if (missing.isEmpty()) return@runCatching songs
        val resolved = coroutineScope {
            missing.map { index ->
                async {
                    index to runCatching { resolveLxSourceCoverUrl(songs[index], client) }
                        .getOrNull()
                }
            }.awaitAll()
        }
        if (resolved.all { it.second == null }) return@runCatching songs
        val result = songs.toMutableList()
        resolved.forEach { (index, cover) ->
            if (cover != null) {
                result[index] = result[index].copy(coverUrl = cover, originalCoverUrl = cover)
            }
        }
        result
    }.getOrElse { error ->
        NPLogger.w("NERI-LxCover", "fill cover gaps failed: ${error.message}")
        songs
    }
}

private fun fetchKugouCover(client: OkHttpClient, musicId: String, albumId: String): String? {
    if (albumId.isNotBlank() && albumId != "0") {
        val albumUrl = "https://mobilecdn.kugou.com/api/v3/album/info?albumid=$albumId&plat=0"
        httpText(client, albumUrl, referer = "https://www.kugou.com/")
            ?.let { body ->
                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: root
                sequenceOf("img", "Image", "cover", "Pic")
                    .map { key -> data.optString(key) }
                    .firstOrNull { it.isNotBlank() }
                    ?.let { normalizeLxCoverUrl(it) }
                    ?.takeIf { !isPlaceholderLxCoverUrl(it) }
                    ?.let { return it }
            }
    }
    val url = "https://mobilecdn.kugou.com/api/v3/song/info?hash=$musicId&cmd=playInfo&from=mkugou"
    val body = httpText(client, url, referer = "https://www.kugou.com/") ?: return null
    val root = JSONObject(body)
    val candidates = buildList {
        sequenceOf("image", "Image", "cover", "img", "album_img")
            .map { root.optString(it) }
            .filter { it.isNotBlank() }
            .forEach { add(it) }
        root.optJSONObject("data")?.let { data ->
            sequenceOf("image", "Image", "cover", "img", "album_img")
                .map { data.optString(it) }
                .filter { it.isNotBlank() }
                .forEach { add(it) }
        }
    }
    return candidates
        .mapNotNull { normalizeLxCoverUrl(it) }
        .firstOrNull { !isPlaceholderLxCoverUrl(it) }
}

private fun fetchKuwoCover(client: OkHttpClient, musicId: String): String? {
    val url = "https://mobilecdn.kuwo.cn/api/v1/music/musicInfo?mid=$musicId&httpsStatus=1"
    val body = httpText(client, url, referer = "https://www.kuwo.cn/") ?: return null
    val root = JSONObject(body)
    val data = root.optJSONObject("data") ?: root
    return sequenceOf("pic", "pic120", "pic240", "pic500", "albumpic", "img", "MUSICPIC")
        .map { key -> data.optString(key) }
        .filter { it.isNotBlank() }
        .mapNotNull { normalizeLxCoverUrl(it) }
        .firstOrNull { !isPlaceholderLxCoverUrl(it) }
}

private fun fetchQqCover(client: OkHttpClient, musicMid: String): String? {
    val data = JSONObject()
        .put(
            "songinfo",
            JSONObject()
                .put("method", "get_song_detail_yqq")
                .put("module", "music.pf_song_detail_svr")
                .put("param", JSONObject().put("song_mid", musicMid))
        )
        .toString()
    val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=" +
        java.net.URLEncoder.encode(data, "UTF-8")
    val body = httpText(client, url, referer = "https://y.qq.com/") ?: return null
    val track = JSONObject(body)
        .optJSONObject("songinfo")
        ?.optJSONObject("data")
        ?.optJSONObject("track_info")
        ?: return null
    val albumMid = track.optJSONObject("album")?.optString("mid").orEmpty()
    if (albumMid.isNotBlank()) {
        return "https://y.qq.com/music/photo_new/T002R300x300M000${albumMid}.jpg"
    }
    return null
}

private fun httpText(client: OkHttpClient, url: String, referer: String): String? {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        .header("Referer", referer)
        .get()
        .build()
    return runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                NPLogger.w("NERI-LxCover", "cover http ${response.code}: $url")
                return@use null
            }
            response.body.string()
        }
    }.getOrElse { error ->
        NPLogger.w("NERI-LxCover", "cover fetch failed: $url, error=${error.message}")
        null
    }
}
