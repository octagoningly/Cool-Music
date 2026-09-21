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
    val platforms = LX_CROSS_PLATFORM_ORDER + LX_NETEASE_PLATFORM_ID
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
    LxDefaultSearchPage(
        songs = results.flatten().distinctBy { "${it.sourceId}|${it.songMid}" }.map(::toLxSongItem),
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

internal fun toLxSongItem(hit: LxCrossPlatformHit): SongItem {
    val platform = hit.sourceId
    require(platform in LX_CROSS_PLATFORM_ORDER || platform == LX_NETEASE_PLATFORM_ID)
    require(hit.songMid.isNotBlank())
    val numericId = if (platform == LX_NETEASE_PLATFORM_ID) {
        hit.songMid.toLongOrNull()
    } else {
        null
    } ?: ("$platform|${hit.songMid}".hashCode().toLong() and 0xffffffffL) + 1L
    return SongItem(
        id = numericId,
        name = hit.name,
        artist = hit.artist,
        album = hit.albumName,
        albumId = hit.albumId.toLongOrNull() ?: 0L,
        durationMs = hit.durationSec.coerceAtLeast(0) * 1000L,
        coverUrl = hit.coverUrl,
        channelId = "$LX_SEARCH_CHANNEL_PREFIX$platform",
        audioId = hit.songMid,
        subAudioId = hit.qualityHashes.takeIf { it.isNotEmpty() }?.let { JSONObject(it).toString() }
    )
}

internal fun SongItem.lxSearchHitOrNull(): LxCrossPlatformHit? {
    val channel = channelId ?: return null
    if (!channel.startsWith(LX_SEARCH_CHANNEL_PREFIX)) return null
    val platform = channel.removePrefix(LX_SEARCH_CHANNEL_PREFIX)
    if (platform !in LX_CROSS_PLATFORM_ORDER && platform != LX_NETEASE_PLATFORM_ID) return null
    val songMid = audioId?.takeIf { it.isNotBlank() } ?: return null
    val hashes = runCatching {
        val json = JSONObject(subAudioId ?: "{}")
        json.keys().asSequence().associateWith { json.optString(it) }.filterValues { it.isNotBlank() }
    }.getOrDefault(emptyMap())
    return LxCrossPlatformHit(
        sourceId = platform,
        songMid = songMid,
        name = name,
        artist = artist,
        durationSec = (durationMs / 1000L).toInt(),
        albumName = album,
        albumId = albumId.takeIf { it > 0L }?.toString().orEmpty(),
        qualityHashes = hashes,
        coverUrl = coverUrl
    )
}

internal fun SongItem.lxNeteaseLyricsIdOrNull(): Long? {
    if (channelId?.startsWith(LX_SEARCH_CHANNEL_PREFIX) != true) return null
    if (matchedLyricSource == MusicPlatform.CLOUD_MUSIC) {
        matchedSongId?.toLongOrNull()?.let { return it }
    }
    return if (channelId == "lx:wy") audioId?.toLongOrNull() else null
}
