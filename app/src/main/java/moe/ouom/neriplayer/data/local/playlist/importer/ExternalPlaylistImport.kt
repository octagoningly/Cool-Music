package moe.ouom.neriplayer.data.local.playlist.importer

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
 * File: moe.ouom.neriplayer.data.local.playlist.import/ExternalPlaylistImport
 * Created: 2026/09/23
 */

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.netease.NeteaseClient
import moe.ouom.neriplayer.core.api.search.CloudMusicSearchApi
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.model.NeteaseArtistSummary
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.util.network.awaitResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs

private const val TAG = "ExternalPlaylistImport"
private const val MAX_IMPORT_SONGS = 500
private const val MATCH_BATCH = 5
private const val HTTP_TIMEOUT_SECONDS = 20L
private const val MIN_MATCH_SCORE = 0.72

sealed class ExternalPlaylistLink {
    data class Netease(val playlistId: Long) : ExternalPlaylistLink()
    data class QqMusic(val disstid: Long) : ExternalPlaylistLink()
    data class Kugou(val specialId: Long) : ExternalPlaylistLink()
    data class Kuwo(val playlistId: Long) : ExternalPlaylistLink()
    data class Unsupported(val reason: String) : ExternalPlaylistLink()
}

data class ExternalImportedPlaylist(
    val name: String,
    val songs: List<SongItem>,
    val platformLabel: String,
    val unmatchedCount: Int = 0
)

sealed class ExternalPlaylistImportResult {
    data class Success(
        val playlistName: String,
        val songCount: Int,
        val unmatchedCount: Int
    ) : ExternalPlaylistImportResult()

    data class Failure(val message: String) : ExternalPlaylistImportResult()
}

private const val EXTERNAL_IMPORT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

private val EXTERNAL_URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
private val QQ_PLAYLIST_PATH_REGEX =
    Regex("""(?:playlist|taoge)/(?:detail/)?(\d+)""", RegexOption.IGNORE_CASE)
private val KUGOU_SPECIAL_PATH_REGEX =
    Regex("""(?:special/single|mix3)/(\d+)""", RegexOption.IGNORE_CASE)
private val KUWO_PLAYLIST_PATH_REGEX =
    Regex("""(?:play_detail|playlists|playlist)/(\d+)""", RegexOption.IGNORE_CASE)

internal fun extractExternalPlaylistHttpUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return EXTERNAL_URL_REGEX.find(trimmed)
        ?.value
        ?.trimEnd('。', '，', ',', '.', '）', ')', '】', ']', '}', '》', '>', '"', '“', '”')
        ?.takeIf { it.isNotBlank() }
        ?: trimmed.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

internal fun parseExternalPlaylistLink(input: String): ExternalPlaylistLink {
    val normalized = extractExternalPlaylistHttpUrl(input)
        ?: return ExternalPlaylistLink.Unsupported("blank")
    val uri = parseUri(normalized) ?: return ExternalPlaylistLink.Unsupported("invalid-url")
    val host = uri.host?.lowercase(Locale.US) ?: return ExternalPlaylistLink.Unsupported("no-host")
    val path = uri.path.orEmpty()
    val params = queryParameters(uri.rawQuery) +
        queryParameters(
            uri.rawFragment
                ?.takeIf { it.contains('?') }
                ?.substringAfter('?')
        )
    val fragmentPath = uri.rawFragment?.substringBefore('?').orEmpty()
    val combinedPath = "$path/$fragmentPath"

    return when {
        host.endsWith("music.163.com") || host.endsWith("163cn.tv") -> {
            if (host.endsWith("163cn.tv")) {
                // short links need expansion before id extraction
                ExternalPlaylistLink.Unsupported("netease-short:$normalized")
            } else {
                val id = params["id"]?.toLongOrNull()
                if (id != null && id > 0L && isNeteasePlaylistPath(combinedPath)) {
                    ExternalPlaylistLink.Netease(id)
                } else {
                    ExternalPlaylistLink.Unsupported("netease-id")
                }
            }
        }

        host.endsWith("y.qq.com") ||
            host.endsWith("i.y.qq.com") ||
            host.endsWith("c.y.qq.com") ||
            host.endsWith("u.y.qq.com") -> {
            val id = params["id"]?.toLongOrNull()
                ?: params["disstid"]?.toLongOrNull()
                ?: QQ_PLAYLIST_PATH_REGEX.find(combinedPath)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (id != null && id > 0L && (isQqPlaylistPath(combinedPath) || params.containsKey("id") || params.containsKey("disstid"))) {
                ExternalPlaylistLink.QqMusic(id)
            } else {
                ExternalPlaylistLink.Unsupported("qq-id")
            }
        }

        host.endsWith("kugou.com") -> {
            val id = params["specialid"]?.toLongOrNull()
                ?: KUGOU_SPECIAL_PATH_REGEX.find(combinedPath)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (id != null && id > 0L) {
                ExternalPlaylistLink.Kugou(id)
            } else {
                ExternalPlaylistLink.Unsupported("kugou-id")
            }
        }

        host.endsWith("kuwo.cn") || host.endsWith("kuwo.com") -> {
            val id = params["id"]?.toLongOrNull()
                ?: params["pid"]?.toLongOrNull()
                ?: KUWO_PLAYLIST_PATH_REGEX.find(combinedPath)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (id != null && id > 0L) {
                ExternalPlaylistLink.Kuwo(id)
            } else {
                ExternalPlaylistLink.Unsupported("kuwo-id")
            }
        }

        else -> ExternalPlaylistLink.Unsupported("platform")
    }
}

private fun isNeteasePlaylistPath(path: String): Boolean {
    val lower = path.lowercase(Locale.US)
    return lower.contains("/playlist") || lower.contains("playlist")
}

private fun isQqPlaylistPath(path: String): Boolean {
    val lower = path.lowercase(Locale.US)
    return lower.contains("playlist") || lower.contains("taoge") || lower.contains("cdinfo")
}

private fun parseUri(raw: String): URI? {
    val candidate = if (raw.contains("://")) raw else "https://$raw"
    return runCatching { URI(candidate) }.getOrNull()
}

private fun URI.pathSegments(): List<String> {
    return path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
}

private fun queryParameters(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery
        .split('&')
        .mapNotNull { part ->
            val key = part.substringBefore('=').urlDecode().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val value = part.substringAfter('=', missingDelimiterValue = "").urlDecode()
            key to value
        }
        .toMap()
}

private fun String.urlDecode(): String {
    return runCatching {
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    }.getOrDefault(this)
}

private fun toHttps(url: String?): String? {
    val normalized = url?.trim().orEmpty().replaceFirst("http://", "https://")
    return normalized.takeIf { it.isNotBlank() }
}

internal data class ExternalRawTrack(
    val platform: String,
    val platformId: String,
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?
)

class ExternalPlaylistImportService(
    private val context: Context,
    private val localRepo: LocalPlaylistRepository = LocalPlaylistRepository.getInstance(context),
    private val neteaseClient: NeteaseClient = AppContainer.neteaseClient,
    private val cloudMusicSearchApi: CloudMusicSearchApi = AppContainer.cloudMusicSearchApi,
    private val httpClient: OkHttpClient = AppContainer.sharedOkHttpClient
) {

    suspend fun importFromText(rawText: String): ExternalPlaylistImportResult =
        withContext(Dispatchers.IO) {
            try {
                val resolvedLink = resolveLink(rawText)
                when (resolvedLink) {
                    is ExternalPlaylistLink.Unsupported -> ExternalPlaylistImportResult.Failure(
                        unsupportedMessage(resolvedLink)
                    )

                    is ExternalPlaylistLink.Netease -> importNetease(resolvedLink.playlistId)
                    is ExternalPlaylistLink.QqMusic -> importQqMusic(resolvedLink.disstid)
                    is ExternalPlaylistLink.Kugou -> importKugou(resolvedLink.specialId)
                    is ExternalPlaylistLink.Kuwo -> importKuwo(resolvedLink.playlistId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                NPLogger.e(TAG, "import failed: ${error.message}", error)
                ExternalPlaylistImportResult.Failure(
                    context.getString(R.string.error_load_playlist, error.message ?: "unknown")
                )
            }
        }

    private suspend fun resolveLink(rawText: String): ExternalPlaylistLink {
        var link = parseExternalPlaylistLink(rawText)
        if (link is ExternalPlaylistLink.Unsupported && link.reason.startsWith("netease-short:")) {
            val shortUrl = link.reason.removePrefix("netease-short:")
            val expanded = expandRedirect(shortUrl)
            link = parseExternalPlaylistLink(expanded)
        }
        // Some share cards keep the short host after expansion attempts; retry extract.
        if (link is ExternalPlaylistLink.Unsupported) {
            val embedded = extractExternalPlaylistHttpUrl(rawText)
            if (embedded != null && embedded != extractExternalPlaylistHttpUrl(rawText.trim())) {
                link = parseExternalPlaylistLink(embedded)
            }
        }
        return link
    }

    private fun unsupportedMessage(link: ExternalPlaylistLink.Unsupported): String {
        return when {
            link.reason == "blank" ->
                context.getString(R.string.external_playlist_import_invalid_link)

            link.reason.startsWith("netease-short") ->
                context.getString(R.string.external_playlist_import_expand_failed)

            else -> context.getString(R.string.external_playlist_import_unsupported_link)
        }
    }

    private suspend fun expandRedirect(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", EXTERNAL_IMPORT_USER_AGENT)
            .build()
        return httpClient.newCall(request).awaitResponse { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.request.url.toString()
        }
    }

    private suspend fun importNetease(playlistId: Long): ExternalPlaylistImportResult {
        val raw = neteaseClient.getPlaylistDetail(playlistId)
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) {
            val code = root.optInt("code", -1)
            return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.error_api_code, code)
            )
        }
        val playlist = root.optJSONObject("playlist")
            ?: return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.error_missing_node, "playlist")
            )
        val playlistName = playlist.optString("name").ifBlank {
            context.getString(R.string.external_playlist_import_default_name_netease)
        }
        val existing = LinkedHashMap<Long, SongItem>()
        playlist.optJSONArray("tracks")?.let { tracks ->
            for (i in 0 until tracks.length()) {
                val track = tracks.optJSONObject(i) ?: continue
                parseNeteaseTrack(track)?.let { song -> existing[song.id] = song }
            }
        }
        val trackIds = LinkedHashSet<Long>()
        playlist.optJSONArray("trackIds")?.let { ids ->
            for (i in 0 until ids.length()) {
                val id = ids.optJSONObject(i)?.optLong("id", 0L) ?: 0L
                if (id > 0L) trackIds.add(id)
            }
        }
        if (trackIds.isEmpty()) {
            existing.keys.forEach { trackIds.add(it) }
        }

        val ordered = ArrayList<SongItem>(trackIds.size)
        val missing = ArrayList<Long>()
        trackIds.forEach { id ->
            val song = existing[id]
            if (song != null) ordered.add(song) else missing.add(id)
        }

        if (missing.isNotEmpty()) {
            val fetched = fetchNeteaseSongDetails(missing)
            val byId = existing + fetched
            ordered.clear()
            trackIds.forEach { id -> byId[id]?.let(ordered::add) }
        }

        return commitImportedPlaylist(
            playlistName = playlistName,
            tracks = ordered.map { it.toRawTrack("netease") },
            preferNeteaseNative = ordered
        )
    }

    private suspend fun fetchNeteaseSongDetails(ids: List<Long>): Map<Long, SongItem> {
        val result = LinkedHashMap<Long, SongItem>()
        ids.chunked(300).forEach { page ->
            val raw = neteaseClient.getSongDetail(page)
            val root = JSONObject(raw)
            if (root.optInt("code", -1) != 200) return@forEach
            val songs = root.optJSONArray("songs") ?: return@forEach
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                parseNeteaseTrack(song)?.let { item -> result[item.id] = item }
            }
        }
        return result
    }

    private fun parseNeteaseTrack(song: JSONObject): SongItem? {
        val id = song.optLong("id", 0L)
        val name = song.optString("name", "")
        if (id <= 0L || name.isBlank()) return null
        val artistItems = parseNeteaseArtists(song.optJSONArray("ar"))
            .ifEmpty { parseNeteaseArtists(song.optJSONArray("artists")) }
        val album = song.optJSONObject("al") ?: song.optJSONObject("album")
        val cover = toHttps(
            album?.optString("picUrl", "")?.ifBlank { album.optString("picUrl_str", "") }
        )
        return SongItem(
            id = id,
            name = name,
            artist = artistItems.joinToString(" / ") { it.name },
            album = album?.optString("name", "").orEmpty(),
            albumId = album?.optLong("id", 0L) ?: 0L,
            durationMs = song.optLong("dt", song.optLong("duration", 0L)),
            coverUrl = cover,
            originalCoverUrl = cover,
            channelId = "netease",
            audioId = id.toString(),
            neteaseArtists = artistItems
        )
    }

    private fun parseNeteaseArtists(array: org.json.JSONArray?): List<NeteaseArtistSummary> {
        if (array == null) return emptyList()
        val result = ArrayList<NeteaseArtistSummary>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optLong("id", 0L)
            val name = item.optString("name", "")
            if (name.isBlank()) continue
            result.add(NeteaseArtistSummary(id = id, name = name))
        }
        return result
    }

    private suspend fun importQqMusic(disstid: Long): ExternalPlaylistImportResult {
        val requestData = JSONObject()
            .put(
                "playlist",
                JSONObject()
                    .put("module", "music.srfDissInfo.aiDissInfo")
                    .put("method", "uniform_get_Dissinfo")
                    .put(
                        "param",
                        JSONObject()
                            .put("disstid", disstid)
                            .put("songlist", 1)
                            .put("tag", 1)
                            .put("userinfo", 1)
                    )
            )
            .toString()
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=${java.net.URLEncoder.encode(requestData, "UTF-8")}"
        val body = httpGet(url)
        val root = JSONObject(body)
        val data = root.optJSONObject("playlist")?.optJSONObject("data")
            ?: root.optJSONObject("data")
            ?: return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.error_missing_node, "playlist")
            )
        val playlistName = data.optJSONObject("dirinfo")?.optString("title")
            ?.ifBlank { null }
            ?: data.optString("dissname").ifBlank { null }
            ?: context.getString(R.string.external_playlist_import_default_name_qq)
        val songlist = data.optJSONArray("songlist")
            ?: data.optJSONArray("songlist")
            ?: return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.error_missing_node, "songlist")
            )
        val tracks = ArrayList<ExternalRawTrack>(songlist.length())
        for (i in 0 until songlist.length()) {
            val song = songlist.optJSONObject(i) ?: continue
            val name = song.optString("songname")
                .ifBlank { song.optString("name") }
                .ifBlank { song.optString("title") }
            if (name.isBlank()) continue
            val singers = song.optJSONArray("singer")
            val artist = if (singers != null && singers.length() > 0) {
                buildList {
                    for (s in 0 until singers.length()) {
                        val singerName = singers.optJSONObject(s)?.optString("name")
                            ?.takeIf { it.isNotBlank() }
                        if (singerName != null) add(singerName)
                    }
                }.joinToString(" / ")
            } else {
                song.optString("singername").ifBlank { song.optString("singer") }
            }
            val albumObj = song.optJSONObject("album")
            val albumMid = albumObj?.optString("mid").orEmpty()
            val songId = song.optLong("songid", 0L)
            val songMid = song.optString("songmid").ifBlank { song.optString("mid") }
            val interval = song.optLong("interval", 0L)
            tracks.add(
                ExternalRawTrack(
                    platform = "qq",
                    platformId = if (songId > 0L) songId.toString() else songMid,
                    name = name,
                    artist = artist,
                    album = albumObj?.optString("name").orEmpty(),
                    durationMs = if (interval > 0L) interval * 1000L else 0L,
                    coverUrl = if (albumMid.isNotBlank()) {
                        "https://y.qq.com/music/photo_new/T002R800x800M000$albumMid.jpg"
                    } else {
                        null
                    }
                )
            )
        }
        return commitImportedPlaylist(playlistName, tracks)
    }

    private suspend fun importKugou(specialId: Long): ExternalPlaylistImportResult {
        val url = "https://mobilecdn.kugou.com/api/v3/special/song" +
            "?specialid=$specialId&page=1&pagesize=$MAX_IMPORT_SONGS&plat=2&version=8400"
        val body = httpGet(url)
        val root = JSONObject(body)
        if (root.optInt("status", -1) != 1 && root.optInt("errcode", -1) != 0) {
            // some gateways return data without status; continue if info exists
            if (root.optJSONArray("info") == null && root.optJSONObject("data")?.optJSONArray("info") == null) {
                return ExternalPlaylistImportResult.Failure(
                    context.getString(R.string.error_load_playlist, "kugou status")
                )
            }
        }
        val infoArray = root.optJSONArray("info")
            ?: root.optJSONObject("data")?.optJSONArray("info")
            ?: return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.error_missing_node, "info")
            )
        val tracks = ArrayList<ExternalRawTrack>(infoArray.length())
        var playlistName = context.getString(R.string.external_playlist_import_default_name_kugou)
        for (i in 0 until infoArray.length()) {
            val item = infoArray.optJSONObject(i) ?: continue
            val filename = item.optString("filename")
            val name = item.optString("songname")
                .ifBlank {
                    filename.substringAfter(" - ", filename)
                }
                .trim()
            val artist = item.optString("singername")
                .ifBlank {
                    filename.substringBefore(" - ", "")
                }
                .trim()
            if (name.isBlank()) continue
            val hash = item.optString("hash").ifBlank { item.optString("audio_id") }
            val duration = item.optLong("duration", item.optLong("timelen", 0L))
            val durationMs = when {
                duration <= 0L -> 0L
                duration > 24L * 60 * 60 -> duration // already ms-like
                duration > 60 * 60 -> duration // seconds large
                else -> duration * 1000L
            }
            tracks.add(
                ExternalRawTrack(
                    platform = "kugou",
                    platformId = hash.ifBlank { "${specialId}_$i" },
                    name = name,
                    artist = artist,
                    album = item.optString("albumname").orEmpty(),
                    durationMs = durationMs,
                    coverUrl = toHttps(item.optString("cover"))
                )
            )
        }
        if (tracks.isEmpty()) {
            return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.external_playlist_import_empty)
            )
        }
        return commitImportedPlaylist(playlistName, tracks)
    }

    private suspend fun importKuwo(playlistId: Long): ExternalPlaylistImportResult {
        val url = "https://nplserver.kuwo.cn/pl.svc" +
            "?op=getlistinfo&pid=$playlistId&pn=0&rn=$MAX_IMPORT_SONGS" +
            "&encode=utf-8&keyset=pl2012&vipver=MUSIC_9.1.1.2_BCS2"
        val body = httpGet(url)
        val root = JSONObject(body)
        if (root.optInt("result", root.optInt("code", -1)) !in listOf(1, 200)) {
            // kuwo uses result=1 for success
            if (root.optJSONArray("musiclist") == null) {
                return ExternalPlaylistImportResult.Failure(
                    context.getString(R.string.error_load_playlist, "kuwo")
                )
            }
        }
        val playlistName = root.optString("name")
            .ifBlank { root.optString("title") }
            .ifBlank { context.getString(R.string.external_playlist_import_default_name_kuwo) }
        val musiclist = root.optJSONArray("musiclist")
            ?: return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.error_missing_node, "musiclist")
            )
        val tracks = ArrayList<ExternalRawTrack>(musiclist.length())
        for (i in 0 until musiclist.length()) {
            val item = musiclist.optJSONObject(i) ?: continue
            val name = item.optString("name").ifBlank { item.optString("songname") }
            val artist = item.optString("artist").ifBlank { item.optString("singer") }
            if (name.isBlank()) continue
            val duration = item.optLong("duration", item.optLong("songTimeMinutes", 0L))
            val durationMs = when {
                duration <= 0L -> 0L
                duration > 24L * 60 * 60 -> duration
                else -> duration * 1000L
            }
            tracks.add(
                ExternalRawTrack(
                    platform = "kuwo",
                    platformId = item.optLong("rid", 0L).takeIf { it > 0L }?.toString()
                        ?: item.optString("id").ifBlank { "${playlistId}_$i" },
                    name = name,
                    artist = artist,
                    album = item.optString("album").orEmpty(),
                    durationMs = durationMs,
                    coverUrl = toHttps(item.optString("pic"))
                        ?: toHttps(item.optString("cover"))
                )
            )
        }
        return commitImportedPlaylist(playlistName, tracks)
    }

    private suspend fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", EXTERNAL_IMPORT_USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .build()
        return httpClient.newCall(request).awaitResponse { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string().ifBlank { throw IOException("empty body") }
        }
    }

    private suspend fun commitImportedPlaylist(
        playlistName: String,
        tracks: List<ExternalRawTrack>,
        preferNeteaseNative: List<SongItem>? = null
    ): ExternalPlaylistImportResult {
        val limited = tracks.take(MAX_IMPORT_SONGS)
        if (preferNeteaseNative != null) {
            if (preferNeteaseNative.isEmpty()) {
                return ExternalPlaylistImportResult.Failure(
                    context.getString(R.string.external_playlist_import_empty)
                )
            }
            val playlist = localRepo.createPlaylistWithSongs(
                name = playlistName,
                songs = preferNeteaseNative
            )
            return ExternalPlaylistImportResult.Success(
                playlistName = playlist.name,
                songCount = playlist.songs.size,
                unmatchedCount = 0
            )
        }

        if (limited.isEmpty()) {
            return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.external_playlist_import_empty)
            )
        }

        val resolved = resolveTracksToNetease(limited)
        val songs = resolved.songs
        if (songs.isEmpty()) {
            return ExternalPlaylistImportResult.Failure(
                context.getString(R.string.external_playlist_import_no_match)
            )
        }
        val playlist = localRepo.createPlaylistWithSongs(
            name = playlistName,
            songs = songs
        )
        return ExternalPlaylistImportResult.Success(
            playlistName = playlist.name,
            songCount = playlist.songs.size,
            unmatchedCount = resolved.unmatchedCount
        )
    }

    private data class ResolvedImport(
        val songs: List<SongItem>,
        val unmatchedCount: Int
    )

    private suspend fun resolveTracksToNetease(tracks: List<ExternalRawTrack>): ResolvedImport {
        val songs = ArrayList<SongItem>(tracks.size)
        var unmatched = 0
        tracks.chunked(MATCH_BATCH).forEach { batch ->
            val batchResults = coroutineScope {
                batch.map { track ->
                    async {
                        track to matchNeteaseSong(track)
                    }
                }.awaitAll()
            }
            batchResults.forEach { (track, matched) ->
                if (matched != null) {
                    songs.add(matched)
                } else {
                    unmatched += 1
                    // Keep a searchable stub so the imported playlist structure is complete.
                    // Playback falls back through LX search by name/artist when possible.
                    songs.add(
                        SongItem(
                            id = syntheticId(track),
                            name = track.name,
                            artist = track.artist,
                            album = track.album,
                            albumId = 0L,
                            durationMs = track.durationMs,
                            coverUrl = track.coverUrl,
                            originalCoverUrl = track.coverUrl,
                            channelId = "netease",
                            audioId = null,
                            neteaseArtists = emptyList()
                        )
                    )
                }
            }
        }
        return ResolvedImport(songs = songs, unmatchedCount = unmatched)
    }

    private suspend fun matchNeteaseSong(track: ExternalRawTrack): SongItem? {
        val keyword = listOf(track.name, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .ifBlank { return null }
        return try {
            val candidates = cloudMusicSearchApi.search(keyword, page = 1).take(8)
            val best = candidates
                .map { candidate -> candidate to scoreCandidate(track, candidate) }
                .filter { (_, score) -> score >= MIN_MATCH_SCORE }
                .maxByOrNull { (_, score) -> score }
                ?.first
                ?: return null
            val songId = best.id.toLongOrNull()?.takeIf { it > 0L } ?: return null
            SongItem(
                id = songId,
                name = best.songName,
                artist = best.singer,
                album = best.albumName.orEmpty(),
                albumId = 0L,
                durationMs = track.durationMs.takeIf { it > 0L } ?: parseDuration(best.duration),
                coverUrl = toHttps(best.coverUrl) ?: track.coverUrl,
                originalCoverUrl = toHttps(best.coverUrl) ?: track.coverUrl,
                channelId = "netease",
                audioId = songId.toString(),
                neteaseArtists = emptyList()
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            NPLogger.d(TAG, "match failed for ${track.name}: ${error.message}")
            null
        }
    }

    private fun scoreCandidate(
        track: ExternalRawTrack,
        candidate: moe.ouom.neriplayer.core.api.search.SongSearchInfo
    ): Double {
        val nameScore = similarity(track.name, candidate.songName)
        val artistScore = if (track.artist.isBlank() || candidate.singer.isBlank()) {
            0.55
        } else {
            similarity(track.artist, candidate.singer)
        }
        var score = nameScore * 0.72 + artistScore * 0.28
        if (track.durationMs > 0L) {
            val candidateMs = parseDuration(candidate.duration)
            if (candidateMs > 0L) {
                val delta = abs(track.durationMs - candidateMs)
                if (delta > 45_000L) score -= 0.18
                else if (delta <= 2_000L) score += 0.05
            }
        }
        val noise = Regex("伴奏|纯音乐|instrumental|karaoke|铃声|remix|dj", RegexOption.IGNORE_CASE)
        if (noise.containsMatchIn(candidate.songName) && !noise.containsMatchIn(track.name)) {
            score -= 0.2
        }
        return score
    }

    private fun parseDuration(duration: String): Long {
        val parts = duration.split(':')
        return when (parts.size) {
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0L
                val s = parts[1].toLongOrNull() ?: 0L
                (m * 60 + s) * 1000L
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val s = parts[2].toLongOrNull() ?: 0L
                (h * 3600 + m * 60 + s) * 1000L
            }
            else -> duration.toLongOrNull() ?: 0L
        }
    }

    private fun similarity(a: String, b: String): Double {
        val left = normalizeText(a)
        val right = normalizeText(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        if (left.contains(right) || right.contains(left)) return 0.86
        val leftTokens = left.split(Regex("\\s+")).filter { it.isNotBlank() }
        val rightTokens = right.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return levenshteinRatio(left, right)
        }
        val hit = leftTokens.count { token -> rightTokens.any { it.contains(token) || token.contains(it) } }
        val tokenScore = hit.toDouble() / maxOf(leftTokens.size, rightTokens.size)
        return maxOf(tokenScore, levenshteinRatio(left, right) * 0.85)
    }

    private fun normalizeText(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[\\[\\]（）()【】]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val prev = IntArray(b.length + 1)
        val curr = IntArray(b.length + 1)
        for (j in 0..b.length) prev[j] = j
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, prev.size)
        }
        val distance = prev[b.length]
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - distance.toDouble() / maxLen
    }

    private fun syntheticId(track: ExternalRawTrack): Long {
        val key = "${track.platform}:${track.platformId}:${track.name}:${track.artist}"
        val hash = key.hashCode().toLong()
        return if (hash > 0L) -hash else hash
    }

    private fun SongItem.toRawTrack(platform: String): ExternalRawTrack {
        return ExternalRawTrack(
            platform = platform,
            platformId = id.toString(),
            name = name,
            artist = artist,
            album = album,
            durationMs = durationMs,
            coverUrl = coverUrl
        )
    }

}
