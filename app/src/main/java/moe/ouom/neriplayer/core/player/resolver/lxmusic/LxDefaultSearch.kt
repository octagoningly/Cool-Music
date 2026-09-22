package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.api.search.SongSearchInfo
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.model.SongItem
import org.json.JSONObject

internal const val LX_SEARCH_CHANNEL_PREFIX = "lx:"
internal const val LX_DEFAULT_SEARCH_PAGE_SIZE = 20

internal data class LxDefaultSearchPage(
    val songs: List<SongItem>,
    val hasMore: Boolean
)

internal suspend fun searchLxDefaultSongs(keyword: String, page: Int): LxDefaultSearchPage = coroutineScope {
    val engines = lxOnlineSearchEngines()
    val platforms = LX_ONLINE_SEARCH_PLATFORM_ORDER.filter { it in engines }
    val useSourceCover = isLxSourceCoverFallbackEnabled()
    if (platforms.isEmpty()) {
        return@coroutineScope LxDefaultSearchPage(songs = emptyList(), hasMore = false)
    }
    val results = platforms.map { platform ->
        async {
            try {
                if (platform == LX_NETEASE_PLATFORM_ID) {
                    AppContainer.cloudMusicSearchApi.search(keyword, page).mapNotNull(::neteaseSearchHit)
                } else {
                    fetchLxCrossPlatformHits(
                        client = AppContainer.sharedOkHttpClient,
                        sourceId = platform,
                        keyword = keyword,
                        limit = LX_DEFAULT_SEARCH_PAGE_SIZE,
                        page = page
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                NPLogger.w("NERI-LxDefaultSearch", "$platform search failed: ${error.message}")
                emptyList()
            }
        }
    }.map { it.await() }
    val mapped = results.flatten()
        .distinctBy { "${it.sourceId}|${it.songMid}" }
        .mapNotNull { hit ->
            runCatching { toLxSongItem(hit, useSourceCover) }
                .onFailure { error ->
                    NPLogger.w("NERI-LxDefaultSearch", "map hit failed: ${error.message}")
                }
                .getOrNull()
        }
    val songs = if (useSourceCover) {
        runCatching { fillLxSongCoverGaps(mapped) }.getOrDefault(mapped)
    } else {
        mapped
    }
    LxDefaultSearchPage(
        songs = songs,
        hasMore = results.any { it.size >= LX_DEFAULT_SEARCH_PAGE_SIZE }
    )
}

private fun neteaseSearchHit(item: SongSearchInfo): LxCrossPlatformHit? {
    if (item.id.isBlank() || item.songName.isBlank()) return null
    val parts = item.duration.split(':').mapNotNull(String::toIntOrNull)
    val durationSec = parts.fold(0) { seconds, part -> seconds * 60 + part }
    return LxCrossPlatformHit(
        sourceId = LX_NETEASE_PLATFORM_ID,
        songMid = item.id,
        name = item.songName,
        artist = item.singer,
        durationSec = durationSec,
        albumName = item.albumName.orEmpty(),
        coverUrl = item.coverUrl
    )
}

internal fun toLxSongItem(
    hit: LxCrossPlatformHit,
    useSourceCover: Boolean = true
): SongItem {
    val platform = hit.sourceId
    require(platform in LX_CROSS_PLATFORM_ORDER || platform == LX_NETEASE_PLATFORM_ID)
    require(hit.songMid.isNotBlank())
    val numericId = if (platform == LX_NETEASE_PLATFORM_ID) {
        hit.songMid.toLongOrNull()
    } else {
        null
    } ?: ("$platform|${hit.songMid}".hashCode().toLong() and 0xffffffffL) + 1L
    val coverUrl = if (platform == LX_NETEASE_PLATFORM_ID) {
        normalizeLxCoverUrl(hit.coverUrl)
    } else if (useSourceCover) {
        lxSourceCoverUrlFromHit(hit)
    } else {
        null
    }
    val subPayload = JSONObject()
    hit.qualityHashes.forEach { (key, value) -> subPayload.put(key, value) }
    if (hit.albumId.isNotBlank()) subPayload.put("albumId", hit.albumId)
    if (hit.coverUrl.isNullOrBlank().not()) subPayload.put("rawCover", hit.coverUrl.orEmpty())
    return SongItem(
        id = numericId,
        name = hit.name,
        artist = hit.artist,
        album = hit.albumName,
        albumId = hit.albumId.toLongOrNull() ?: 0L,
        durationMs = hit.durationSec.coerceAtLeast(0) * 1000L,
        coverUrl = coverUrl,
        originalCoverUrl = coverUrl,
        channelId = "$LX_SEARCH_CHANNEL_PREFIX$platform",
        audioId = hit.songMid,
        subAudioId = subPayload.toString().takeIf { it != "{}" }
    )
}

internal fun SongItem.lxSearchHitOrNull(): LxCrossPlatformHit? {
    val channel = channelId ?: return null
    if (!channel.startsWith(LX_SEARCH_CHANNEL_PREFIX)) return null
    val platform = channel.removePrefix(LX_SEARCH_CHANNEL_PREFIX)
    if (platform !in LX_CROSS_PLATFORM_ORDER && platform != LX_NETEASE_PLATFORM_ID) return null
    val songMid = audioId?.takeIf { it.isNotBlank() } ?: return null
    val payload = runCatching { JSONObject(subAudioId ?: "{}") }.getOrDefault(JSONObject())
    val hashes = payload.keys().asSequence()
        .filter { it != "albumId" && it != "rawCover" }
        .associateWith { payload.optString(it) }
        .filterValues { it.isNotBlank() }
    val payloadAlbumId = payload.optString("albumId").ifBlank {
        this.albumId.takeIf { it > 0L }?.toString().orEmpty()
    }
    val rawCover = payload.optString("rawCover").ifBlank { this.coverUrl }
    return LxCrossPlatformHit(
        sourceId = platform,
        songMid = songMid,
        name = name,
        artist = artist,
        durationSec = (durationMs / 1000L).toInt(),
        albumName = album,
        albumId = payloadAlbumId,
        qualityHashes = hashes,
        coverUrl = rawCover
    )
}

internal fun SongItem.lxNeteaseLyricsIdOrNull(): Long? {
    if (channelId?.startsWith(LX_SEARCH_CHANNEL_PREFIX) != true) return null
    if (matchedLyricSource == MusicPlatform.CLOUD_MUSIC) {
        matchedSongId?.toLongOrNull()?.let { return it }
    }
    return if (channelId == "lx:wy") audioId?.toLongOrNull() else null
}
