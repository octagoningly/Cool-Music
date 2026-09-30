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
 * File: moe.ouom.neriplayer.data.cache/RecommendationsCacheMappers
 * Created: 2026/9/22
 */

import moe.ouom.neriplayer.core.api.youtube.YouTubeMusicHomeItem
import moe.ouom.neriplayer.core.api.youtube.YouTubeMusicHomeShelf
import moe.ouom.neriplayer.data.model.NeteaseArtistSummary
import moe.ouom.neriplayer.data.model.SongItem

fun SongItem.toCachedSongDto(): CachedSongDto {
    return CachedSongDto(
        id = id,
        name = name,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        coverUrl = coverUrl,
        channelId = channelId,
        audioId = audioId,
        neteaseArtists = neteaseArtists.orEmpty().map { artist ->
            CachedArtistDto(id = artist.id, name = artist.name)
        }
    )
}

fun CachedSongDto.toSongItem(): SongItem {
    return SongItem(
        id = id,
        name = name,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        coverUrl = coverUrl,
        channelId = channelId,
        audioId = audioId,
        neteaseArtists = neteaseArtists.map { artist ->
            NeteaseArtistSummary(id = artist.id, name = artist.name)
        }
    )
}

fun YouTubeMusicHomeShelf.toCachedYtShelfDto(): CachedYtShelfDto {
    return CachedYtShelfDto(
        title = title,
        items = items.map { item ->
            CachedYtShelfItemDto(
                title = item.title,
                subtitle = item.subtitle,
                coverUrl = item.coverUrl,
                browseId = item.browseId,
                videoId = item.videoId,
                pageType = item.pageType,
                durationText = item.durationText,
                durationMs = item.durationMs
            )
        }
    )
}

fun CachedYtShelfDto.toYouTubeMusicHomeShelf(): YouTubeMusicHomeShelf {
    return YouTubeMusicHomeShelf(
        title = title,
        items = items.map { item ->
            YouTubeMusicHomeItem(
                title = item.title,
                subtitle = item.subtitle,
                coverUrl = item.coverUrl,
                browseId = item.browseId,
                videoId = item.videoId,
                pageType = item.pageType,
                durationText = item.durationText,
                durationMs = item.durationMs
            )
        }
    )
}
