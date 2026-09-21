package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.Dispatchers
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
 * 在线音源封面/歌词开关 + 无封面时从音源解析封面。
 */
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

/** 搜索响应里能直接拿到封面时，按平台拼出可用 https 地址 */
internal fun lxSourceCoverUrlFromHit(hit: LxCrossPlatformHit): String? {
    normalizeLxCoverUrl(hit.coverUrl)?.let { return it }
    return when (hit.sourceId) {
        LX_QQ_PLATFORM_ID -> hit.albumId
            .takeIf { it.isNotBlank() && it != "0" }
            ?.let { "https://y.qq.com/music/photo_new/T002R300x300M000${it}.jpg" }

        LX_KUGOU_PLATFORM_ID -> null
        LX_KUWO_PLATFORM_ID -> null
        else -> null
    }
}

private data class LxCoverCacheKey(val platform: String, val id: String)

private val lxSourceCoverCache = ConcurrentHashMap<LxCoverCacheKey, String?>()

/**
 * 歌曲没有封面时，按音源平台补一张。
 * 优先搜索结果里的封面；仍为空再调公开接口。
 */
internal suspend fun resolveLxSourceCoverUrl(
    song: SongItem,
    client: OkHttpClient = AppContainer.sharedOkHttpClient
): String? {
    song.coverUrl?.takeIf { it.isNotBlank() }?.let { return normalizeLxCoverUrl(it) }
    song.lxSearchHitOrNull()?.let { hit ->
        lxSourceCoverUrlFromHit(hit)?.let { return it }
    }
    val platform = song.channelId?.removePrefix(LX_SEARCH_CHANNEL_PREFIX) ?: return null
    val musicId = song.audioId?.takeIf { it.isNotBlank() } ?: return null
    val cacheKey = LxCoverCacheKey(platform, musicId)
    if (lxSourceCoverCache.containsKey(cacheKey)) {
        return lxSourceCoverCache[cacheKey]
    }
    val resolved = withContext(Dispatchers.IO) {
        runCatching {
            when (platform) {
                LX_KUGOU_PLATFORM_ID -> fetchKugouCover(client, musicId)
                LX_KUWO_PLATFORM_ID -> fetchKuwoCover(client, musicId)
                LX_QQ_PLATFORM_ID -> fetchQqCover(client, musicId, song.album)
                else -> null
            }
        }.getOrNull()
    }
    lxSourceCoverCache[cacheKey] = resolved
    return resolved
}

private fun fetchKugouCover(client: OkHttpClient, musicId: String): String? {
    val url = "https://mobilecdn.kugou.com/api/v3/song/info?hash=$musicId&cmd=playInfo&from=mkugou"
    val body = httpText(client, url, referer = "https://www.kugou.com/") ?: return null
    val root = JSONObject(body)
    val image = sequenceOf("image", "Image", "cover", "img")
        .map { root.optString(it) }
        .firstOrNull { it.isNotBlank() }
        ?: root.optJSONObject("data")?.let { data ->
            sequenceOf("image", "Image", "cover", "img")
                .map { data.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }
    return normalizeLxCoverUrl(image)
}

private fun fetchKuwoCover(client: OkHttpClient, musicId: String): String? {
    val url = "https://mobilecdn.kuwo.cn/api/v1/music/musicInfo?mid=$musicId&httpsStatus=1"
    val body = httpText(client, url, referer = "https://www.kuwo.cn/") ?: return null
    val root = JSONObject(body)
    val data = root.optJSONObject("data") ?: root
    val image = sequenceOf("pic", "pic120", "pic240", "pic500", "albumpic", "img")
        .map { data.optString(it) }
        .firstOrNull { it.isNotBlank() }
    return normalizeLxCoverUrl(image)
}

private fun fetchQqCover(client: OkHttpClient, musicMid: String, albumName: String): String? {
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
