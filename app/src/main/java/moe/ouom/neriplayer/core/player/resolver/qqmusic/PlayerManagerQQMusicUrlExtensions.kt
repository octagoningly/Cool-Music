package moe.ouom.neriplayer.core.player.resolver.qqmusic

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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
 * File: moe.ouom.neriplayer.core.player.resolver.qqmusic/PlayerManagerQQMusicUrlExtensions
 * Created: 2026/9/29
 */

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlayUrlFailure
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlayUrlResult
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlaybackRepository
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.model.PlayerEvent
import moe.ouom.neriplayer.core.player.model.SongUrlResult
import moe.ouom.neriplayer.core.player.policy.refresh.RefreshResolverSideEffects
import moe.ouom.neriplayer.core.player.quality.effectiveQQMusicQuality
import moe.ouom.neriplayer.core.player.url.buildQQMusicPlaybackAudioInfo
import moe.ouom.neriplayer.core.player.url.buildQQMusicRepresentationIdentity
import moe.ouom.neriplayer.data.model.SongItem

private const val LOG_TAG = "NERI-PlayerManager-QQMusicUrl"

/**
 * QQ 音乐曲目取流：优先取 [SongItem.audioId] 中的 songmid。
 */
internal suspend fun PlayerManager.getQQMusicAudioUrl(
    song: SongItem,
    suppressError: Boolean = false,
    sideEffects: RefreshResolverSideEffects = RefreshResolverSideEffects()
): SongUrlResult = withContext(Dispatchers.IO) {
    val songmid = song.audioId?.trim()?.takeIf { it.isNotBlank() }
    if (songmid == null) {
        if (!suppressError) {
            sideEffects.emitError {
                postPlayerEvent(PlayerEvent.ShowError(getLocalizedString(R.string.error_no_play_url)))
            }
        }
        return@withContext SongUrlResult.Failure
    }

    try {
        val repo: QQMusicPlaybackRepository = AppContainer.qqMusicPlaybackRepository
        val preferred = effectiveQQMusicQuality()
        val result = repo.getBestPlayableStream(songmid = songmid, preferredKey = preferred)
        when (result) {
            is QQMusicPlayUrlResult.Success -> {
                val stream = result.stream
                NPLogger.d(LOG_TAG, "resolved song=$songmid q=${stream.qualityKey} url=${stream.url.take(80)}")
                SongUrlResult.Success(
                    url = stream.url,
                    candidateUrls = stream.candidateUrls,
                    mimeType = stream.mimeType,
                    expectedContentLength = null,
                    audioInfo = buildQQMusicPlaybackAudioInfo(
                        stream = stream,
                        preferredKey = preferred
                    ) { getLocalizedString(it) },
                    representationIdentity = buildQQMusicRepresentationIdentity(stream)
                )
            }
            is QQMusicPlayUrlResult.Failure -> {
                NPLogger.w(LOG_TAG, "resolve failed song=$songmid reason=${result.reason}")
                // 登录态可能已失效：刷新健康状态以驱动设置页提示重新登录
                if (result.reason == QQMusicPlayUrlFailure.NOT_LOGGED_IN ||
                    result.reason == QQMusicPlayUrlFailure.NO_AUTHORITY_OR_NO_FILE
                ) {
                    runCatching { AppContainer.qqMusicCookieRepo.refreshHealth() }
                }
                if (!suppressError) {
                    val messageRes = when (result.reason) {
                        QQMusicPlayUrlFailure.NOT_LOGGED_IN ->
                            R.string.settings_qq_music_status_missing
                        else -> R.string.error_no_play_url
                    }
                    sideEffects.emitError {
                        postPlayerEvent(
                            PlayerEvent.ShowError(getLocalizedString(messageRes))
                        )
                    }
                }
                SongUrlResult.Failure
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NPLogger.e(LOG_TAG, "Failed to get QQ Music play url", e)
        if (!suppressError) {
            sideEffects.emitError {
                postPlayerEvent(
                    PlayerEvent.ShowError(
                        getLocalizedString(
                            R.string.player_playback_url_error_detail,
                            e.message.orEmpty()
                        )
                    )
                )
            }
        }
        SongUrlResult.Failure
    }
}
