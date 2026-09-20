package moe.ouom.neriplayer.data.source.lxmusic

import moe.ouom.neriplayer.core.logging.NPLogger
import org.json.JSONArray
import org.json.JSONObject

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
 * File: moe.ouom.neriplayer.data.source.lxmusic/LxMusicSourceParser
 * Updated: 2026/3/23
 */

data class LxJsScriptMetadata(
    val name: String,
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val sourceIds: List<String> = listOf("kw", "kg", "tx", "wy", "mg")
)

/**
 * 解析落雪音乐（LX Music）自定义「网络音源」JSON。
 *
 * 支持 JSON API 型；JS 脚本型由 QuickJS 运行时处理。
 */
object LxMusicSourceParser {
    private const val TAG = "LxMusicSourceParser"

    fun looksLikeLxMusicJsSource(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("const ") ||
            trimmed.startsWith("let ") ||
            trimmed.startsWith("var ") ||
            trimmed.startsWith("async function") ||
            trimmed.startsWith("module.exports") ||
            trimmed.contains("exports.default") ||
            trimmed.contains("lx.send") ||
            (trimmed.contains("getMusicUrl") && trimmed.contains("function"))
    }

    fun parseJsScriptMetadata(script: String): LxJsScriptMetadata {
        // 优先解析落雪标准头： /* @name xxx @author yyy ... */
        val header = Regex("""/\*[\s\S]+?\*/""").find(script)?.value
        fun fromHeader(tag: String): String {
            if (header == null) return ""
            return Regex("""^\s*\*\s*@$tag\s+(.+)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
                .find(header)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                .orEmpty()
        }
        fun fromObjectLiteral(vararg keys: String): String {
            for (key in keys) {
                val regex = Regex("""['"]?$key['"]?\s*:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
                regex.find(script)?.groupValues?.getOrNull(1)?.let { return it.trim() }
            }
            return ""
        }
        val name = fromHeader("name").ifBlank { fromObjectLiteral("name", "title") }
            .ifBlank { "LX JS Source" }
        val sourceIds = buildList {
            listOf("kw", "kg", "tx", "wy", "mg").forEach { id ->
                if (script.contains(id, ignoreCase = true)) add(id)
            }
        }.ifEmpty { listOf("kw", "kg", "tx", "wy", "mg") }
        return LxJsScriptMetadata(
            name = name,
            description = fromHeader("description").ifBlank { fromObjectLiteral("description", "desc") },
            author = fromHeader("author").ifBlank { fromObjectLiteral("author") },
            version = fromHeader("version").ifBlank { fromObjectLiteral("version") },
            sourceIds = sourceIds
        )
    }

    fun parseSourceDefinition(body: String): LxSourceDefinition? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (looksLikeLxMusicJsSource(trimmed)) {
            return null
        }
        return runCatching {
            val root = JSONObject(trimmed)
            val api = root.optJSONObject("api") ?: return null
            val searchUrl = extractApiUrl(api, "search")
            val songUrl = extractApiUrl(api, "songUrl")
                .ifBlank { extractApiUrl(api, "url") }
            if (searchUrl.isBlank() || songUrl.isBlank()) {
                NPLogger.w(TAG, "LX source missing search/songUrl API")
                return null
            }
            val type = root.optString("type", "music")
            if (type.isNotBlank() && type != "music") {
                NPLogger.w(TAG, "Unsupported LX source type: $type")
                return null
            }
            LxSourceDefinition(
                name = root.optString("name").trim().ifBlank { "LX Source" },
                description = root.optString("description").trim(),
                author = root.optString("author").trim(),
                version = root.optString("version").trim(),
                srcId = root.optString("srcId").trim().ifBlank {
                    root.optString("id").trim()
                },
                supportedQualities = parseSupportedQualities(root),
                searchApiUrl = searchUrl,
                songUrlApiUrl = songUrl,
                lyricApiUrl = extractApiUrl(api, "lyric").ifBlank {
                    extractApiUrl(api, "lrc")
                },
                picApiUrl = extractApiUrl(api, "pic")
            )
        }.onFailure {
            NPLogger.w(TAG, "Failed to parse LX source definition: ${it.message}")
        }.getOrNull()
    }

    fun parseSearchResponse(body: String): List<LxSearchItem> {
        return runCatching {
            val root = JSONObject(body)
            if (root.has("code")) {
                val code = root.optInt("code", 200)
                if (code != 200 && code != 0) return emptyList()
            }
            val data = when {
                root.has("data") && root.opt("data") is JSONArray -> root.getJSONArray("data")
                root.has("data") && root.opt("data") is JSONObject -> {
                    val nested = root.getJSONObject("data")
                    when {
                        nested.has("list") -> nested.optJSONArray("list")
                        nested.has("songs") -> nested.optJSONArray("songs")
                        nested.has("items") -> nested.optJSONArray("items")
                        else -> null
                    }
                }
                root.has("list") -> root.optJSONArray("list")
                root.has("songs") -> root.optJSONArray("songs")
                root.has("items") -> root.optJSONArray("items")
                root.opt("result") is JSONArray -> root.getJSONArray("result")
                else -> null
            } ?: return emptyList()

            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                        .ifBlank { item.optString("title") }
                        .trim()
                    if (name.isBlank()) continue
                    val musicId = item.optString("musicId")
                        .ifBlank { item.optString("id") }
                        .ifBlank { item.optString("songmid") }
                        .trim()
                    if (musicId.isBlank()) continue
                    add(
                        LxSearchItem(
                            name = name,
                            singer = item.optString("singer")
                                .ifBlank { item.optString("artist") }
                                .ifBlank { item.optString("artistName") }
                                .trim(),
                            musicId = musicId,
                            intervalText = item.optString("interval").trim(),
                            albumName = item.optString("albumName")
                                .ifBlank { item.optString("album") }
                                .trim(),
                            picUrl = item.optString("picUrl")
                                .ifBlank { item.optString("pic") }
                                .trim(),
                            qualitys = parseQualityList(item.opt("qualitys"))
                                .ifEmpty { parseQualityList(item.opt("qualities")) }
                        )
                    )
                }
            }
        }.getOrElse {
            NPLogger.w(TAG, "Failed to parse LX search response: ${it.message}")
            emptyList()
        }
    }

    fun parseSongUrlResponse(body: String): LxSongUrlResult {
        return runCatching {
            val root = JSONObject(body)
            if (root.has("code")) {
                val code = root.optInt("code", 200)
                if (code != 200 && code != 0) return LxSongUrlResult.Failure
            }
            val url = when {
                root.has("data") && root.opt("data") is JSONObject ->
                    root.getJSONObject("data").optString("url")
                root.has("data") && root.opt("data") is String ->
                    root.optString("data")
                else -> root.optString("url")
            }.trim()
            if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) {
                return LxSongUrlResult.Failure
            }
            val quality = if (root.opt("data") is JSONObject) {
                root.getJSONObject("data").optString("quality")
            } else {
                root.optString("quality")
            }.trim().ifBlank { null }
            LxSongUrlResult.Success(url = url, quality = quality)
        }.getOrElse {
            NPLogger.w(TAG, "Failed to parse LX song url response: ${it.message}")
            LxSongUrlResult.Failure
        }
    }

    fun parseRemoteSourceRegistry(body: String): LxRemoteSourceRegistry {
        return runCatching {
            val root = JSONObject(body)
            val sourcesArray = root.optJSONArray("sources") ?: JSONArray()
            val sources = buildList {
                for (index in 0 until sourcesArray.length()) {
                    val item = sourcesArray.optJSONObject(index) ?: continue
                    val url = item.optString("url").trim()
                    if (!url.startsWith("http://", ignoreCase = true) &&
                        !url.startsWith("https://", ignoreCase = true)
                    ) {
                        continue
                    }
                    val healthyChannels = parseStringArray(item.optJSONArray("healthyChannels"))
                    if (healthyChannels.isEmpty()) continue
                    add(
                        LxRemoteSourceEntry(
                            url = url,
                            name = item.optString("name").trim().ifBlank { "LX Source" },
                            kind = item.optString("kind").trim(),
                            description = item.optString("description").trim(),
                            author = item.optString("author").trim(),
                            version = item.optString("version").trim(),
                            sourcePage = item.optString("sourcePage").trim(),
                            healthyChannels = healthyChannels,
                            lastValidatedAt = item.optString("lastValidatedAt").trim()
                        )
                    )
                }
            }
            LxRemoteSourceRegistry(
                schemaVersion = root.optInt("schemaVersion", 1),
                generatedAt = root.optString("generatedAt").trim(),
                minimumHealthyChannels = root.optInt("minimumHealthyChannels", 1),
                sources = sources
            )
        }.getOrElse {
            NPLogger.w(TAG, "Failed to parse LX remote source registry: ${it.message}")
            LxRemoteSourceRegistry()
        }
    }

    private fun extractApiUrl(api: JSONObject, key: String): String {
        val raw = api.opt(key) ?: return ""
        return when (raw) {
            is String -> raw.trim()
            is JSONObject -> raw.optString("url").trim()
            else -> ""
        }
    }

    private fun parseSupportedQualities(root: JSONObject): List<String> {
        return parseQualityList(root.opt("supportedQualitys"))
            .ifEmpty { parseQualityList(root.opt("supportedQualities")) }
    }

    private fun parseQualityList(raw: Any?): List<String> {
        return when (raw) {
            is JSONArray -> buildList {
                for (i in 0 until raw.length()) {
                    val value = when (val item = raw.opt(i)) {
                        is String -> item
                        is Boolean -> continue
                        is JSONObject -> {
                            // 兼容 { "flac": true } 形式以外的对象列表
                            item.optString("id").ifBlank { item.optString("name") }
                        }
                        else -> item?.toString().orEmpty()
                    }
                    value.trim().takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
                }
            }
            is JSONObject -> buildList {
                val keys = raw.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (raw.optBoolean(key, false)) {
                        add(key.trim().lowercase())
                    }
                }
            }
            is String -> listOf(raw.trim().lowercase()).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun parseStringArray(raw: JSONArray?): List<String> {
        if (raw == null) return emptyList()
        return buildList {
            for (index in 0 until raw.length()) {
                raw.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }
}
