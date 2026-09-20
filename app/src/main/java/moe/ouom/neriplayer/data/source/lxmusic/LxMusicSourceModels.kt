package moe.ouom.neriplayer.data.source.lxmusic

import kotlinx.serialization.Serializable

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
 * File: moe.ouom.neriplayer.data.source.lxmusic/LxMusicSourceModels
 * Updated: 2026/3/23
 */

/** 已导入的 LX Music 音源（JSON API 型 / JS 脚本型） */
@Serializable
data class LxImportedSource(
    val id: String,
    val url: String,
    val name: String,
    /** json = 网络 API 型；js = QuickJS 脚本型 */
    val kind: String = "json",
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val srcId: String = "",
    val enabled: Boolean = true,
    val supportedQualities: List<String> = emptyList(),
    val searchApiUrl: String = "",
    val songUrlApiUrl: String = "",
    val lyricApiUrl: String = "",
    val picApiUrl: String = "",
    /** JS 脚本本地文件相对路径（filesDir 下） */
    val scriptPath: String = "",
    /** JS 脚本声明的可解析平台源，如 kw/kg/tx/wy/mg */
    val jsSourceIds: List<String> = emptyList(),
    val importedAt: Long = 0L,
    val lastValidatedAt: Long = 0L,
    val lastError: String? = null
) {
    val isJsSource: Boolean get() = kind.equals("js", ignoreCase = true)
}

@Serializable
data class LxImportedSourceList(
    val sources: List<LxImportedSource> = emptyList()
)

/** GitHub 每日验证清单中的一个可导入音源 */
data class LxRemoteSourceEntry(
    val url: String,
    val name: String,
    val kind: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val sourcePage: String = "",
    val healthyChannels: List<String> = emptyList(),
    val lastValidatedAt: String = ""
)

/** GitHub 每日验证清单，供设置页“获取”按钮读取 */
data class LxRemoteSourceRegistry(
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val minimumHealthyChannels: Int = 1,
    val sources: List<LxRemoteSourceEntry> = emptyList()
)

data class LxSourceDefinition(
    val name: String,
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val srcId: String = "",
    val supportedQualities: List<String> = emptyList(),
    val searchApiUrl: String,
    val songUrlApiUrl: String,
    val lyricApiUrl: String = "",
    val picApiUrl: String = ""
)

data class LxSearchItem(
    val name: String,
    val singer: String,
    val musicId: String,
    val intervalText: String = "",
    val albumName: String = "",
    val picUrl: String = "",
    val qualitys: List<String> = emptyList()
)

sealed class LxSongUrlResult {
    data class Success(
        val url: String,
        val quality: String? = null
    ) : LxSongUrlResult()

    data object Failure : LxSongUrlResult()
}

/**
 * LX 音质与 NeriPlayer 网易云音质键的映射。
 * 优先请求更高音质，失败时按列表顺序降级。
 */
internal fun mapNeteaseQualityToLxOrder(qualityKey: String?): List<String> {
    return when (qualityKey?.trim()?.lowercase()) {
        "jymaster", "hires", "sky" ->
            listOf("flac24bit", "wav", "flac", "sq", "320k", "hq", "aac", "128k")
        "lossless", "jyeffect" ->
            listOf("flac", "sq", "wav", "flac24bit", "320k", "hq", "aac", "128k")
        "exhigh", "higher" ->
            listOf("320k", "hq", "flac", "sq", "aac", "128k", "wav", "flac24bit")
        else ->
            listOf("128k", "hq", "aac", "320k", "flac", "sq", "wav", "flac24bit")
    }
}

internal fun normalizeLxQualityLabel(quality: String?): String {
    return when (quality?.trim()?.lowercase()) {
        "128k" -> "128K"
        "320k" -> "320K"
        "flac" -> "FLAC"
        "flac24bit" -> "FLAC 24bit"
        "wav" -> "WAV"
        "sq" -> "SQ"
        "hq" -> "HQ"
        "aac" -> "AAC"
        else -> quality?.trim()?.uppercase().orEmpty().ifBlank { "LX" }
    }
}

internal fun inferLxMimeType(quality: String?, url: String?): String {
    val qualityLower = quality?.trim()?.lowercase().orEmpty()
    val urlLower = url?.lowercase().orEmpty()
    return when {
        qualityLower == "flac" || qualityLower == "flac24bit" || qualityLower == "sq" ||
            urlLower.endsWith(".flac") -> "audio/flac"
        qualityLower == "wav" || urlLower.endsWith(".wav") -> "audio/wav"
        qualityLower == "aac" || urlLower.endsWith(".m4a") || urlLower.endsWith(".aac") ->
            "audio/mp4"
        urlLower.contains("m3u8") -> "application/x-mpegURL"
        else -> "audio/mpeg"
    }
}
