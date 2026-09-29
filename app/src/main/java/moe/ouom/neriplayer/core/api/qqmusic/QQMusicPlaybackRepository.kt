package moe.ouom.neriplayer.core.api.qqmusic

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
 * File: moe.ouom.neriplayer.core.api.qqmusic/QQMusicPlaybackRepository
 * Created: 2026/9/29
 */

import moe.ouom.neriplayer.data.platform.qqmusic.QQMusicQuality
import moe.ouom.neriplayer.core.logging.NPLogger

/**
 * 取流来源抽象（便于单测注入 fake）。
 */
interface QQMusicPlayUrlSource {
    suspend fun getPlayUrl(
        songmid: String,
        quality: QQMusicQuality,
        guid: String
    ): QQMusicPlayUrlResult
}

/**
 * QQ 音乐播放仓库：按音质偏好逐级请求取流（非一次拉列表，因 QQ 按 filename 单发请求）。
 *
 * 降级规则：
 * - [QQMusicPlayUrlFailure.NOT_LOGGED_IN]：登录缺失，**不降级**直接失败（更高音质同样无权）
 * - [QQMusicPlayUrlFailure.NO_AUTHORITY_OR_NO_FILE]：该档被墙/无文件，继续尝试下一档
 * - 其他失败：跳过该档继续（避免单次网络抖动毁掉整条链）
 */
class QQMusicPlaybackRepository(
    private val client: QQMusicPlayUrlSource
) {
    companion object {
        private const val LOG_TAG = "NERI-QQMusicRepo"
    }

    suspend fun getBestPlayableStream(
        songmid: String,
        preferredKey: String = QQMusicQuality.FREE_ACCOUNT_TOP.key
    ): QQMusicPlayUrlResult {
        val chain = QQMusicQuality.degradeChain(QQMusicQuality.fromKey(preferredKey))
        var lastFailure: QQMusicPlayUrlFailure? = null

        for (quality in chain) {
            when (val result = client.getPlayUrl(songmid, quality, QQMusicClient.randomGuid())) {
                is QQMusicPlayUrlResult.Success -> {
                    if (quality != chain.first()) {
                        NPLogger.d(
                            LOG_TAG,
                            "getBestPlayableStream degraded song=$songmid " +
                                "pref=$preferredKey hit=${quality.key}"
                        )
                    }
                    return result
                }
                is QQMusicPlayUrlResult.Failure -> {
                    lastFailure = result.reason
                    NPLogger.d(
                        LOG_TAG,
                        "getBestPlayableStream miss song=$songmid q=${quality.key} " +
                            "reason=${result.reason}"
                    )
                    if (result.reason == QQMusicPlayUrlFailure.NOT_LOGGED_IN) {
                        return result
                    }
                }
            }
        }

        return QQMusicPlayUrlResult.Failure(
            lastFailure ?: QQMusicPlayUrlFailure.NO_AUTHORITY_OR_NO_FILE
        )
    }

    /** 单档直取（不降级），供调试/精确控制使用 */
    suspend fun getStreamAt(
        songmid: String,
        qualityKey: String
    ): QQMusicPlayUrlResult {
        return client.getPlayUrl(
            songmid,
            QQMusicQuality.fromKey(qualityKey),
            QQMusicClient.randomGuid()
        )
    }
}
