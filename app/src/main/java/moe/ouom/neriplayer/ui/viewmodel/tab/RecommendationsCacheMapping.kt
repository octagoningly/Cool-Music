package moe.ouom.neriplayer.ui.viewmodel.tab

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
 * File: moe.ouom.neriplayer.ui.viewmodel.tab/RecommendationsCacheMapping
 * Created: 2026/9/22
 */

import moe.ouom.neriplayer.data.cache.CachedPlaylistDto
import moe.ouom.neriplayer.data.cache.CachedPlaylistSectionDto
import moe.ouom.neriplayer.data.cache.CachedSongSectionDto
import moe.ouom.neriplayer.data.cache.CachedYtPlaylistDto
import moe.ouom.neriplayer.data.cache.HomeFeedCacheSnapshot
import moe.ouom.neriplayer.data.cache.toCachedSongDto
import moe.ouom.neriplayer.data.cache.toCachedYtShelfDto
import moe.ouom.neriplayer.data.cache.toSongItem
import moe.ouom.neriplayer.data.cache.toYouTubeMusicHomeShelf

fun PlaylistSummary.toCachedPlaylistDto(): CachedPlaylistDto {
    return CachedPlaylistDto(
        id = id,
        name = name,
        picUrl = picUrl,
        playCount = playCount,
        trackCount = trackCount
    )
}

fun CachedPlaylistDto.toPlaylistSummary(): PlaylistSummary {
    return PlaylistSummary(
        id = id,
        name = name,
        picUrl = picUrl,
        playCount = playCount,
        trackCount = trackCount
    )
}

fun YouTubeMusicPlaylist.toCachedYtPlaylistDto(): CachedYtPlaylistDto {
    return CachedYtPlaylistDto(
        browseId = browseId,
        playlistId = playlistId,
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        trackCount = trackCount,
        creatorName = creatorName
    )
}

fun CachedYtPlaylistDto.toYouTubeMusicPlaylist(): YouTubeMusicPlaylist {
    return YouTubeMusicPlaylist(
        browseId = browseId,
        playlistId = playlistId,
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        trackCount = trackCount,
        creatorName = creatorName
    )
}

fun HomeNeteaseSongSectionState.toCachedSongSectionDto(): CachedSongSectionDto {
    return CachedSongSectionDto(
        source = source.name,
        items = section.items.map { it.toCachedSongDto() }
    )
}

fun CachedSongSectionDto.toHomeNeteaseSongSectionState(): HomeNeteaseSongSectionState? {
    val source = runCatching { NeteaseHomeSongSource.valueOf(source) }.getOrNull() ?: return null
    return HomeNeteaseSongSectionState(
        source = source,
        section = HomeSectionState(items = items.map { it.toSongItem() })
    )
}

fun HomeNeteasePlaylistSectionState.toCachedPlaylistSectionDto(): CachedPlaylistSectionDto {
    return CachedPlaylistSectionDto(
        source = source.name,
        items = section.items.map { it.toCachedPlaylistDto() }
    )
}

fun CachedPlaylistSectionDto.toHomeNeteasePlaylistSectionState(): HomeNeteasePlaylistSectionState? {
    val source = runCatching { NeteaseHomePlaylistSource.valueOf(source) }.getOrNull() ?: return null
    return HomeNeteasePlaylistSectionState(
        source = source,
        section = HomeSectionState(items = items.map { it.toPlaylistSummary() })
    )
}

fun HomeUiState.toHomeFeedCacheSnapshot(
    neteaseAccountContext: String,
    youtubeAuthFingerprint: String,
    savedAtMs: Long = System.currentTimeMillis()
): HomeFeedCacheSnapshot {
    return HomeFeedCacheSnapshot(
        savedAtMs = savedAtMs,
        neteaseAccountContext = neteaseAccountContext,
        youtubeAuthFingerprint = youtubeAuthFingerprint,
        useYouTubeHome = internationalizationEnabled,
        hasLogin = hasLogin,
        playlistSections = playlistSections.map { it.toCachedPlaylistSectionDto() },
        trendingSongSections = trendingSongSections.map { it.toCachedSongSectionDto() },
        radarSongSections = radarSongSections.map { it.toCachedSongSectionDto() },
        radarPlaylists = radarPlaylists.items.map { it.toCachedPlaylistDto() },
        ytMusicPlaylists = ytMusicPlaylists.items.map { it.toCachedYtPlaylistDto() },
        ytMusicHomeShelves = ytMusicHomeShelves.items.map { it.toCachedYtShelfDto() }
    )
}

/**
 * 把磁盘快照合并进当前 UI 状态：
 * - 只填充有内容的分区，保留当前 loading / 结构
 * - YT 部分仅在 useYouTubeHome 时合并，避免串源
 */
fun HomeUiState.withHomeFeedCache(snapshot: HomeFeedCacheSnapshot): HomeUiState {
    val cachedPlaylistSections = snapshot.playlistSections
        .mapNotNull { it.toHomeNeteasePlaylistSectionState() }
        .associateBy { it.source }
    val cachedTrending = snapshot.trendingSongSections
        .mapNotNull { it.toHomeNeteaseSongSectionState() }
        .associateBy { it.source }
    val cachedRadar = snapshot.radarSongSections
        .mapNotNull { it.toHomeNeteaseSongSectionState() }
        .associateBy { it.source }

    fun fillSongSections(
        current: List<HomeNeteaseSongSectionState>,
        cachedBySource: Map<NeteaseHomeSongSource, HomeNeteaseSongSectionState>
    ): List<HomeNeteaseSongSectionState> {
        if (current.isEmpty()) {
            return cachedBySource.values.map { cached ->
                cached.copy(section = cached.section.copy(loading = false))
            }
        }
        return current.map { section ->
            val cached = cachedBySource[section.source] ?: return@map section
            if (cached.section.items.isEmpty()) section
            else section.copy(
                section = section.section.copy(items = cached.section.items, loading = false)
            )
        }
    }

    fun fillPlaylistSections(
        current: List<HomeNeteasePlaylistSectionState>,
        cachedBySource: Map<NeteaseHomePlaylistSource, HomeNeteasePlaylistSectionState>
    ): List<HomeNeteasePlaylistSectionState> {
        if (current.isEmpty()) {
            return cachedBySource.values.map { cached ->
                cached.copy(section = cached.section.copy(loading = false))
            }
        }
        return current.map { section ->
            val cached = cachedBySource[section.source] ?: return@map section
            if (cached.section.items.isEmpty()) section
            else section.copy(
                section = section.section.copy(items = cached.section.items, loading = false)
            )
        }
    }

    return copy(
        playlistSections = fillPlaylistSections(playlistSections, cachedPlaylistSections),
        trendingSongSections = fillSongSections(trendingSongSections, cachedTrending),
        radarSongSections = fillSongSections(radarSongSections, cachedRadar),
        radarPlaylists = if (snapshot.radarPlaylists.isEmpty()) {
            radarPlaylists
        } else {
            radarPlaylists.copy(
                items = snapshot.radarPlaylists.map { it.toPlaylistSummary() },
                loading = false
            )
        },
        ytMusicPlaylists = if (!snapshot.useYouTubeHome || snapshot.ytMusicPlaylists.isEmpty()) {
            ytMusicPlaylists
        } else {
            ytMusicPlaylists.copy(
                items = snapshot.ytMusicPlaylists.map { it.toYouTubeMusicPlaylist() },
                loading = false
            )
        },
        ytMusicHomeShelves = if (!snapshot.useYouTubeHome || snapshot.ytMusicHomeShelves.isEmpty()) {
            ytMusicHomeShelves
        } else {
            ytMusicHomeShelves.copy(
                items = snapshot.ytMusicHomeShelves.map { it.toYouTubeMusicHomeShelf() },
                loading = false
            )
        }
    )
}
