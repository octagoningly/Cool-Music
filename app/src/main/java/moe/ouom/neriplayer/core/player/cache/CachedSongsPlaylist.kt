@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package moe.ouom.neriplayer.core.player.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.history.PlayHistoryRepository
import moe.ouom.neriplayer.data.history.toSongItem
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.data.platform.youtube.extractYouTubeMusicVideoId
import moe.ouom.neriplayer.data.platform.youtube.buildYouTubeMusicMediaUri
import moe.ouom.neriplayer.data.platform.youtube.stableYouTubeMusicId
import moe.ouom.neriplayer.data.platform.youtube.youtubeMusicThumbnailUrl
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

private const val SONG_METADATA_KEY = "${ContentMetadata.KEY_CUSTOM_PREFIX}neriplayer_cached_song"

data class CachedSong(
    val song: SongItem,
    val bytes: Long,
    val complete: Boolean,
    val lastTouched: Long
)

data class CachedSongsSnapshot(
    val songs: List<CachedSong>,
    val unrecognizedBytes: Long,
    val unrecognizedResources: Int,
    val otherDiskBytes: Long,
    val unrecognizedGroups: Map<CachedUnrecognizedKind, Long>
) {
    companion object {
        val Empty = CachedSongsSnapshot(emptyList(), 0L, 0, 0L, emptyMap())
    }
}

enum class CachedUnrecognizedKind {
    LX,
    BILI_FALLBACK,
    SHARED_STREAM,
    DIRECT_URL,
    OTHER
}

internal fun unrecognizedCacheKind(key: String): CachedUnrecognizedKind = when {
    key.startsWith("lx-") || key.startsWith("lxjs-") -> CachedUnrecognizedKind.LX
    key.startsWith("bili-auto-") -> CachedUnrecognizedKind.BILI_FALLBACK
    key.startsWith("listen-together-stream-") -> CachedUnrecognizedKind.SHARED_STREAM
    key.startsWith("http://") || key.startsWith("https://") -> CachedUnrecognizedKind.DIRECT_URL
    else -> CachedUnrecognizedKind.OTHER
}

internal fun Cache.writeCachedSong(cacheKey: String, song: SongItem) {
    val data = JSONObject().apply {
        put("id", song.id)
        put("name", song.name)
        put("artist", song.artist)
        put("album", song.album)
        put("albumId", song.albumId)
        put("durationMs", song.durationMs)
        put("coverUrl", song.coverUrl)
        put("mediaUri", song.mediaUri)
        put("channelId", song.channelId)
        put("audioId", song.audioId)
        put("subAudioId", song.subAudioId)
        put("playlistContextId", song.playlistContextId)
        put("sourceStableKey", song.sourceStableKey)
        put("customName", song.customName)
        put("customArtist", song.customArtist)
        put("customCoverUrl", song.customCoverUrl)
    }
    applyContentMetadataMutations(
        cacheKey,
        ContentMetadataMutations().set(SONG_METADATA_KEY, data.toString())
    )
}

private fun Cache.readCachedSong(cacheKey: String): SongItem? = runCatching {
    val raw = getContentMetadata(cacheKey).get(SONG_METADATA_KEY, null as String?) ?: return@runCatching null
    val data = JSONObject(raw)
    val name = data.optString("name").takeIf(String::isNotBlank) ?: return@runCatching null
    SongItem(
        id = data.getLong("id"),
        name = name,
        artist = data.optString("artist"),
        album = data.optString("album"),
        albumId = data.optLong("albumId"),
        durationMs = data.optLong("durationMs"),
        coverUrl = data.optionalString("coverUrl"),
        mediaUri = data.optionalString("mediaUri"),
        channelId = data.optionalString("channelId"),
        audioId = data.optionalString("audioId"),
        subAudioId = data.optionalString("subAudioId"),
        playlistContextId = data.optionalString("playlistContextId"),
        sourceStableKey = data.optionalString("sourceStableKey"),
        customName = data.optionalString("customName"),
        customArtist = data.optionalString("customArtist"),
        customCoverUrl = data.optionalString("customCoverUrl")
    )
}.getOrNull()

private fun JSONObject.optionalString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun cacheKeyMatchesSong(key: String, song: SongItem): Boolean {
    val youtubeId = extractYouTubeMusicVideoId(song.mediaUri)
    if (youtubeId != null) return key.startsWith("ytmusic-$youtubeId-")
    if (song.channelId == "bilibili" || song.album.startsWith("Bilibili")) {
        val audioId = song.audioId ?: song.id.toString()
        return key.startsWith("bili-$audioId-")
    }
    return key.startsWith("netease-${song.id}-") ||
        key.startsWith("netease-preview-v1-${song.id}-") ||
        key.startsWith("lx-${song.id}-") ||
        (key.startsWith("lxjs-") && key.contains("-wy-${song.id}-"))
}

internal fun matchingKnownCachedSong(key: String, candidates: List<SongItem>): SongItem? {
    val matching = candidates.filter { cacheKeyMatchesSong(key, it) }
    return matching.firstOrNull { !it.name.startsWith("ID ") } ?: matching.firstOrNull()
}

private val neteaseKey = Regex("^(?:netease-|netease-preview-v1-)([0-9]+)-")
private val simpleLxKey = Regex("^lx-([0-9]+)-[^-]+$")
private val lxJsNeteaseKey = Regex("^lxjs-.+-wy-([0-9]+)-[^-]+$")
private val biliKey = Regex("^bili-([0-9]+)(?:-([0-9]+))?-[^-]+$")
private val listenTogetherKey = Regex("^listen-together-stream-([0-9a-f]{24})-[0-9a-f]{24}$")

internal fun legacySongFromKey(key: String): SongItem? {
    (neteaseKey.find(key) ?: simpleLxKey.matchEntire(key) ?: lxJsNeteaseKey.matchEntire(key))
        ?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { id ->
            return SongItem(
                id = id, name = "ID $id", artist = "NetEase", album = "", albumId = 0L,
                durationMs = 0L, coverUrl = null
            )
        }
    biliKey.matchEntire(key)?.let { match ->
        val aid = match.groupValues[1].toLongOrNull() ?: return null
        val cid = match.groupValues[2].toLongOrNull()
        return SongItem(
            id = aid, name = "ID $aid", artist = "Bilibili",
            album = if (cid == null) "Bilibili" else "Bilibili|$cid",
            albumId = 0L, durationMs = 0L, coverUrl = null,
            channelId = "bilibili", audioId = aid.toString(), subAudioId = cid?.toString()
        )
    }
    if (key.startsWith("ytmusic-")) {
        val videoId = key.removePrefix("ytmusic-")
            .removeSuffix("-stable-m4a")
            .substringBeforeLast('-', "")
            .takeIf(String::isNotBlank) ?: return null
        return SongItem(
            id = stableYouTubeMusicId(videoId), name = "ID $videoId",
            artist = "YouTube Music", album = "youtube_music", albumId = 0L,
            durationMs = 0L, coverUrl = youtubeMusicThumbnailUrl(videoId),
            mediaUri = buildYouTubeMusicMediaUri(videoId), audioId = videoId
        )
    }
    return null
}

suspend fun PlayerManager.cachedSongsSnapshot(): CachedSongsSnapshot = withContext(Dispatchers.IO) {
    val mediaCache = cache ?: return@withContext CachedSongsSnapshot.Empty
    val knownSongsById = (currentQueueFlow.value +
        LocalPlaylistRepository.getInstance(application).playlists.value.flatMap { it.songs } +
        PlayHistoryRepository.getInstance(application).historyFlow.value.map { it.toSongItem() })
        .filterNot { isLocalSong(it) }
        .groupBy(SongItem::id)
    val knownSongsByHash by lazy {
        knownSongsById.values.flatten().associateBy { song ->
            MessageDigest.getInstance("SHA-256")
                .digest(song.stableKey().toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(24)
        }
    }
    val bySong = linkedMapOf<String, CachedSong>()
    val keys = runCatching { mediaCache.keys.toList() }.getOrDefault(emptyList())
    var unrecognizedBytes = 0L
    var unrecognizedResources = 0
    var indexedBytes = 0L
    val unrecognizedGroups = mutableMapOf<CachedUnrecognizedKind, Long>()
    fun recordUnrecognized(key: String, bytes: Long) {
        unrecognizedBytes += bytes
        unrecognizedResources++
        val kind = unrecognizedCacheKind(key)
        unrecognizedGroups[kind] = (unrecognizedGroups[kind] ?: 0L) + bytes
    }
    for (key in keys) {
        val spans = runCatching { mediaCache.getCachedSpans(key) }.getOrNull() ?: continue
        if (spans.isEmpty()) continue
        val bytes = spans.sumOf { it.length }
        indexedBytes += bytes
        if (mediaCache.getContentMetadata(key).get(
                "${ContentMetadata.KEY_CUSTOM_PREFIX}neriplayer_playback_cache_unsafe",
                null as String?
            ) == "1") {
            recordUnrecognized(key, bytes)
            continue
        }
        val legacySong = legacySongFromKey(key)
        val metadataSong = mediaCache.readCachedSong(key)
        val knownSong = legacySong?.let { fallback ->
            matchingKnownCachedSong(key, knownSongsById[fallback.id].orEmpty())
        }
        val song = knownSong
            ?.takeIf { metadataSong?.name?.startsWith("ID ") == true && !it.name.startsWith("ID ") }
            ?: metadataSong
            ?: knownSong
            ?: legacySong
            ?: listenTogetherKey.matchEntire(key)?.groupValues?.getOrNull(1)
                ?.let(knownSongsByHash::get)
        if (song == null) {
            recordUnrecognized(key, bytes)
            continue
        }
        val expectedLength = ContentMetadata.getContentLength(mediaCache.getContentMetadata(key))
        val complete = expectedLength > 0L && mediaCache.isCached(key, 0L, expectedLength)
        val identity = song.stableKey()
        val previous = bySong[identity]
        bySong[identity] = CachedSong(
            song = if (previous == null || previous.song.name.startsWith("ID ")) song else previous.song,
            bytes = (previous?.bytes ?: 0L) + bytes,
            complete = (previous?.complete == true) || complete,
            lastTouched = maxOf(previous?.lastTouched ?: 0L, spans.maxOf { it.lastTouchTimestamp })
        )
    }
    val diskBytes = runCatching {
        File(application.cacheDir, "media_cache").walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
    }.getOrDefault(indexedBytes)
    CachedSongsSnapshot(
        songs = bySong.values.sortedByDescending(CachedSong::lastTouched),
        unrecognizedBytes = unrecognizedBytes,
        unrecognizedResources = unrecognizedResources,
        otherDiskBytes = (diskBytes - indexedBytes).coerceAtLeast(0L),
        unrecognizedGroups = unrecognizedGroups
    )
}
