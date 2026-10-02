package moe.ouom.neriplayer.data.platform.qqmusic

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
 * File: moe.ouom.neriplayer.data.platform.qqmusic/QQMusicAudioSelector
 * Created: 2026/9/29
 */

import java.util.Locale

/**
 * QQ 音乐音质与取流文件名映射（规格见 docs/qq-music-link-notes.md §1）。
 *
 * 取流按 filename 逐个请求（非一次拉列表），故降级链 = 按 key 从高到低依次请求。
 * 免费账号实测：m4a(试听)/128k 可用；320k/flac 触发 104003（会员墙）。
 */
enum class QQMusicQuality(
    val key: String,
    val filePrefix: String,
    val ext: String,
    val minBitrateKbps: Int
) {
    /** FLAC 无损（F000{songmid}.flac） */
    LOSSLESS("flac", "F000", ".flac", 900),

    /** 320kbps（M800{songmid}.mp3） */
    HIGH("320k", "M800", ".mp3", 300),

    /** 128kbps（M500{songmid}.mp3） */
    MEDIUM("128k", "M500", ".mp3", 120),

    /** 试听 m4a（C400{songmid}.m4a） */
    TRY("m4a", "C400", ".m4a", 0);

    fun fileNameFor(songmid: String): String = "$filePrefix$songmid$ext"

    fun mimeType(): String = when (ext) {
        ".flac" -> "audio/flac"
        ".mp3" -> "audio/mpeg"
        ".m4a" -> "audio/mp4"
        else -> "audio/mpeg"
    }

    companion object {
        /** 从高到低：flac → 320k → 128k → 试听 */
        private val order = listOf(LOSSLESS, HIGH, MEDIUM, TRY)

        /** 免费账号可用的最高常规音质；上层偏好可再高，降级时会落到这档 */
        val FREE_ACCOUNT_TOP: QQMusicQuality = MEDIUM

        fun fromKey(key: String): QQMusicQuality {
            val normalized = key.trim().lowercase(Locale.US)
            return order.find { it.key == normalized } ?: MEDIUM
        }

        /** 从 [from] 到最低的一条降级链（含 from 本身） */
        fun degradeChain(from: QQMusicQuality): List<QQMusicQuality> {
            val startIdx = order.indexOf(from).coerceAtLeast(0)
            return order.drop(startIdx)
        }
    }
}

/**
 * 取流结果中的单条音频流描述。
 * [url] 为优先使用的完整直链（sip 域名 + purl）；[candidateUrls] 是同一
 * vkey 响应给出的备用 CDN，在首个 CDN 不可用时供播放器立即切换。
 */
data class QQMusicStreamInfo(
    val songmid: String,
    val qualityKey: String,
    val filename: String,
    val url: String,
    val candidateUrls: List<String> = emptyList(),
    val mimeType: String,
    val vkey: String = "",
    val guid: String = "",
    val fromTag: String? = null
)
