package moe.ouom.neriplayer.data.cache

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
 * File: moe.ouom.neriplayer.data.cache/RecommendationsCacheModels
 * Created: 2026/9/22
 */

/**
 * 首页 / 探索推荐的磁盘快照 DTO。
 * 与 UI 模型解耦，方便序列化与单元测试。
 */
data class CachedArtistDto(
    val id: Long = 0L,
    val name: String = ""
)

data class CachedSongDto(
    val id: Long = 0L,
    val name: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val channelId: String? = null,
    val audioId: String? = null,
    val neteaseArtists: List<CachedArtistDto> = emptyList()
)

data class CachedPlaylistDto(
    val id: Long = 0L,
    val name: String = "",
    val picUrl: String = "",
    val playCount: Long = 0L,
    val trackCount: Int = 0
)

data class CachedSongSectionDto(
    val source: String = "",
    val items: List<CachedSongDto> = emptyList()
)

data class CachedPlaylistSectionDto(
    val source: String = "",
    val items: List<CachedPlaylistDto> = emptyList()
)

data class CachedYtPlaylistDto(
    val browseId: String = "",
    val playlistId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val creatorName: String = ""
)

data class CachedYtShelfItemDto(
    val title: String = "",
    val subtitle: String = "",
    val coverUrl: String = "",
    val browseId: String = "",
    val videoId: String = "",
    val pageType: String = "",
    val durationText: String = "",
    val durationMs: Long = 0L
)

data class CachedYtShelfDto(
    val title: String = "",
    val items: List<CachedYtShelfItemDto> = emptyList()
)

/** 首页推荐整体快照（网易分区 + 雷达歌单 + YT 首页） */
data class HomeFeedCacheSnapshot(
    val version: Int = VERSION,
    val savedAtMs: Long = 0L,
    /** neteaseRadarCacheContext 结果；换号后不复用 */
    val neteaseAccountContext: String = "",
    /** YouTube 登录指纹；变化后不复用 YT 部分 */
    val youtubeAuthFingerprint: String = "",
    val useYouTubeHome: Boolean = false,
    val hasLogin: Boolean = false,
    val playlistSections: List<CachedPlaylistSectionDto> = emptyList(),
    val trendingSongSections: List<CachedSongSectionDto> = emptyList(),
    val radarSongSections: List<CachedSongSectionDto> = emptyList(),
    val radarPlaylists: List<CachedPlaylistDto> = emptyList(),
    val ytMusicPlaylists: List<CachedYtPlaylistDto> = emptyList(),
    val ytMusicHomeShelves: List<CachedYtShelfDto> = emptyList()
) {
    companion object {
        const val VERSION = 1
    }
}

/** 探索页精品歌单网格：按 tag 分桶 */
data class ExploreGridCacheSnapshot(
    val version: Int = VERSION,
    val savedAtMs: Long = 0L,
    val grids: Map<String, List<CachedPlaylistDto>> = emptyMap()
) {
    companion object {
        const val VERSION = 1
    }
}

/** 探索页 YouTube 音乐库歌单 */
data class ExploreYtLibraryCacheSnapshot(
    val version: Int = VERSION,
    val savedAtMs: Long = 0L,
    val youtubeAuthFingerprint: String = "",
    val playlists: List<CachedYtPlaylistDto> = emptyList()
) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * SWR 应用策略：账号/模式一致才展示缓存。
 * 纯函数，便于单测。
 */
fun shouldApplyHomeFeedCache(
    snapshot: HomeFeedCacheSnapshot?,
    neteaseAccountContext: String,
    youtubeAuthFingerprint: String,
    useYouTubeHome: Boolean
): Boolean {
    if (snapshot == null) return false
    if (snapshot.version != HomeFeedCacheSnapshot.VERSION) return false
    if (snapshot.neteaseAccountContext != neteaseAccountContext) return false
    if (snapshot.useYouTubeHome != useYouTubeHome) return false
    if (useYouTubeHome && snapshot.youtubeAuthFingerprint != youtubeAuthFingerprint) {
        return false
    }
    val hasNeteaseContent = snapshot.playlistSections.any { it.items.isNotEmpty() } ||
        snapshot.trendingSongSections.any { it.items.isNotEmpty() } ||
        snapshot.radarSongSections.any { it.items.isNotEmpty() } ||
        snapshot.radarPlaylists.isNotEmpty()
    val hasYouTubeContent = snapshot.ytMusicPlaylists.isNotEmpty() ||
        snapshot.ytMusicHomeShelves.any { it.items.isNotEmpty() }
    return if (useYouTubeHome) hasYouTubeContent else hasNeteaseContent
}

fun shouldApplyExploreYtLibraryCache(
    snapshot: ExploreYtLibraryCacheSnapshot?,
    youtubeAuthFingerprint: String
): Boolean {
    if (snapshot == null) return false
    if (snapshot.version != ExploreYtLibraryCacheSnapshot.VERSION) return false
    if (snapshot.youtubeAuthFingerprint != youtubeAuthFingerprint) return false
    return snapshot.playlists.isNotEmpty()
}

fun exploreGridFromCache(
    snapshot: ExploreGridCacheSnapshot?,
    tag: String
): List<CachedPlaylistDto> {
    if (snapshot == null) return emptyList()
    if (snapshot.version != ExploreGridCacheSnapshot.VERSION) return emptyList()
    return snapshot.grids[tag].orEmpty()
}

/**
 * 网络失败时保留已有条目（缓存或上次成功结果），只在空列表时暴露错误。
 */
fun <T> mergeSectionAfterFailure(
    previousItems: List<T>,
    error: String?
): Pair<List<T>, String?> {
    return if (previousItems.isEmpty()) {
        emptyList<T>() to error
    } else {
        previousItems to null
    }
}
