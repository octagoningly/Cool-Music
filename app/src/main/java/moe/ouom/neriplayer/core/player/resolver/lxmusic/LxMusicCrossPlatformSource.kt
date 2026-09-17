package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.model.SongItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

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
 * File: moe.ouom.neriplayer.core.player.resolver.lxmusic/LxMusicCrossPlatformSource
 */

private const val TAG = "NERI-LxMusicSource"

/** 酷我平台在落雪协议里的 source id */
internal const val LX_KUWO_PLATFORM_ID = "kw"

/** 酷我客户端参数里的固定 uid（公开客户端值，落雪同款） */
private const val LX_KUWO_SEARCH_UID = "794762570"

/** 跨平台候选的最低接受分（歌名 + 歌手 + 时长） */
internal const val LX_CROSS_PLATFORM_MIN_SCORE = 72

/** 候选带版本后缀（Live/演唱会…）时的降权分 */
internal const val LX_CROSS_PLATFORM_SUFFIX_PENALTY = 8

/** 时长差超过这个绝对值，判定为串烧/不同版本 */
internal const val LX_CROSS_PLATFORM_MAX_DURATION_DELTA_MS = 45_000L

/** 时长比超过这个倍数（长或短），判定为不同版本 */
internal const val LX_CROSS_PLATFORM_MAX_DURATION_RATIO = 1.35

private const val LX_CROSS_PLATFORM_SEARCH_LIMIT = 12
private const val LX_CROSS_PLATFORM_CACHE_LIMIT = 128

/**
 * 伴奏 / 翻唱 / 铃声等同名异版，命中即排除。
 * 不做这一步会直接播成「晴天 (KTV版伴奏)」这类错误版本。
 */
private val lxCrossPlatformNoiseRegex = Regex(
    "伴奏|纯音乐|instrumental|off\\s*vocal|karaoke|清唱|和声|铃声|remix|dj版|翻唱|cover",
    RegexOption.IGNORE_CASE
)

/** 歌名里的版本后缀：「晴天 (Live)」→「晴天」 */
private val lxCrossPlatformVersionRegex = Regex("[（(【\\[][^）)】\\]]*[）)】\\]]")

/**
 * 一个可用于取流的跨平台曲目。
 * NeriPlayer 只有网易云曲目自带平台 ID，其余平台靠搜索拿到 ID 后再交给在线音源取流。
 */
internal data class LxCrossPlatformHit(
    val sourceId: String,
    val songMid: String,
    val name: String,
    val artist: String,
    val durationSec: Int,
    val albumName: String
)

/** 去掉歌名里的版本括号内容，便于比对正题名 */
internal fun normalizeLxCrossPlatformTitle(raw: String): String {
    return raw.replace(lxCrossPlatformVersionRegex, " ").trim()
}

/**
 * 候选评分：歌名 60 + 歌手 30 + 时长 12。
 * 伴奏/翻唱等版本直接判 0，宁可不用也不放错歌。
 */
internal fun scoreLxCrossPlatformHit(song: SongItem, hit: LxCrossPlatformHit): Int {
    if (hit.songMid.isBlank()) return 0
    if (lxCrossPlatformNoiseRegex.containsMatchIn(hit.name)) return 0

    val targetTitle = normalizeLxText(normalizeLxCrossPlatformTitle(song.originalName ?: song.name))
    val targetArtist = normalizeLxText(song.originalArtist ?: song.artist)
    val candidateTitle = normalizeLxText(normalizeLxCrossPlatformTitle(hit.name))
    val candidateArtist = normalizeLxText(hit.artist)
    if (targetTitle.isBlank() || candidateTitle.isBlank()) return 0

    var score = (textSimilarityScore(targetTitle, candidateTitle) * 60).toInt()
    score += if (targetArtist.isNotBlank() && candidateArtist.isNotBlank()) {
        (textSimilarityScore(targetArtist, candidateArtist) * 30).toInt()
    } else {
        10
    }
    // 候选带版本后缀（Live/演唱会/串烧…）时轻微降权，原版优先
    val targetHadSuffix = (song.originalName ?: song.name) != normalizeLxCrossPlatformTitle(song.originalName ?: song.name)
    val candidateHasSuffix = hit.name != normalizeLxCrossPlatformTitle(hit.name)
    if (candidateHasSuffix && !targetHadSuffix) score -= LX_CROSS_PLATFORM_SUFFIX_PENALTY

    if (song.durationMs > 0L && hit.durationSec > 0) {
        val candidateMs = hit.durationSec * 1000L
        val deltaMs = (song.durationMs - candidateMs).absoluteValue
        // 时长差太多基本是串烧/演唱会串场，直接判不合格，避免放错版本
        if (deltaMs > LX_CROSS_PLATFORM_MAX_DURATION_DELTA_MS ||
            candidateMs > song.durationMs * LX_CROSS_PLATFORM_MAX_DURATION_RATIO ||
            candidateMs < song.durationMs / LX_CROSS_PLATFORM_MAX_DURATION_RATIO
        ) {
            return 0
        }
        score += when {
            deltaMs <= 3_000L -> 12
            deltaMs <= 8_000L -> 8
            deltaMs <= 15_000L -> 4
            else -> 1
        }
    } else {
        score += 5
    }
    return score
}

internal fun selectBestLxCrossPlatformHit(
    song: SongItem,
    hits: List<LxCrossPlatformHit>,
    minScore: Int = LX_CROSS_PLATFORM_MIN_SCORE
): LxCrossPlatformHit? {
    return hits
        .map { it to scoreLxCrossPlatformHit(song, it) }
        .filter { it.second >= minScore }
        .maxByOrNull { it.second }
        ?.first
}

/**
 * 解析酷我搜索响应。
 *
 * 带客户端参数的接口返回标准 JSON；老式/精简请求返回单引号「类 JSON」。
 * 两种都支持，避免接口调整后直接失效。
 */
internal fun parseLxKuwoSearchBody(body: String): List<LxCrossPlatformHit> {
    if (body.isBlank()) return emptyList()
    val fromJson = parseLxKuwoSearchJson(body)
    if (fromJson.isNotEmpty()) return fromJson
    return parseLxKuwoSearchLoose(body)
}

private fun parseLxKuwoSearchJson(body: String): List<LxCrossPlatformHit> {
    if (!body.contains("abslist")) return emptyList()
    return runCatching {
        val array = JSONObject(body).optJSONArray("abslist") ?: return emptyList()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val hit = item.toLxCrossPlatformHit() ?: continue
                add(hit)
            }
        }.distinctBy { it.songMid }
    }.getOrElse {
        emptyList()
    }
}

private fun JSONObject.toLxCrossPlatformHit(): LxCrossPlatformHit? {
    val songMid = optString("MUSICRID")
        .substringAfter("MUSIC_", missingDelimiterValue = "")
        .trim()
        .ifBlank { optString("DC_TARGETID").trim() }
    if (songMid.isBlank()) return null
    val name = decodeLxHtmlEntities(
        optString("SONGNAME").ifBlank { optString("NAME") }
    ).trim()
    if (name.isBlank()) return null
    return LxCrossPlatformHit(
        sourceId = LX_KUWO_PLATFORM_ID,
        songMid = songMid,
        name = name,
        artist = decodeLxHtmlEntities(optString("ARTIST")).trim(),
        durationSec = optString("DURATION").trim().toIntOrNull() ?: 0,
        albumName = decodeLxHtmlEntities(optString("ALBUM")).trim()
    )
}

/** 兼容单引号「类 JSON」的历史响应格式 */
private fun parseLxKuwoSearchLoose(body: String): List<LxCrossPlatformHit> {
    if (!body.contains("DC_TARGETID")) return emptyList()
    return extractLxArrayObjects(body, "abslist").mapNotNull { block ->
        val songMid = block.lxField("MUSICRID")
            ?.substringAfter("MUSIC_", missingDelimiterValue = "")
            ?.trim()
            .orEmpty()
            .ifBlank { block.lxField("DC_TARGETID")?.trim().orEmpty() }
        val name = block.lxField("NAME")
            ?.let(::decodeLxHtmlEntities)
            ?.trim()
            .orEmpty()
            .ifBlank { block.lxField("SONGNAME")?.let(::decodeLxHtmlEntities)?.trim().orEmpty() }
        if (songMid.isBlank() || name.isBlank()) return@mapNotNull null
        LxCrossPlatformHit(
            sourceId = LX_KUWO_PLATFORM_ID,
            songMid = songMid,
            name = name,
            artist = block.lxField("ARTIST")?.let(::decodeLxHtmlEntities).orEmpty().trim(),
            durationSec = block.lxField("DURATION")?.trim()?.toIntOrNull() ?: 0,
            albumName = block.lxField("ALBUM")?.let(::decodeLxHtmlEntities).orEmpty().trim()
        )
    }.distinctBy { it.songMid }
}

/**
 * 取出 `'<arrayKey>':[ ... ]` 里的顶层对象文本。
 * 单引号字符串内的花括号/方括号不参与计数，因此能正确处理嵌套。
 */
internal fun extractLxArrayObjects(body: String, arrayKey: String): List<String> {
    val arrayStart = body.indexOf("'$arrayKey'")
    if (arrayStart < 0) return emptyList()
    var index = body.indexOf('[', arrayStart)
    if (index < 0) return emptyList()
    index++

    val objects = mutableListOf<String>()
    val buffer = StringBuilder()
    var depth = 0
    var inString = false
    while (index < body.length) {
        val char = body[index]
        when {
            inString -> {
                if (char == '\\' && index + 1 < body.length) {
                    buffer.append(char).append(body[index + 1])
                    index += 2
                    continue
                }
                if (char == '\'') inString = false
                buffer.append(char)
            }

            char == '\'' -> {
                inString = true
                buffer.append(char)
            }

            char == '{' -> {
                depth++
                buffer.append(char)
            }

            char == '}' -> {
                depth--
                buffer.append(char)
                if (depth == 0) {
                    objects += buffer.toString()
                    buffer.clear()
                }
            }

            char == ']' && depth == 0 -> break

            depth > 0 -> buffer.append(char)
        }
        index++
    }
    return objects
}

/** 兼容单引号（酷我真实响应）与双引号（测试/其他变体）两种写法 */
private fun String.lxField(key: String): String? {
    return Regex("['\"]$key['\"]\\s*:\\s*['\"]([^'\"]*)['\"]")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
}

private fun decodeLxHtmlEntities(value: String): String {
    return value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

/**
 * 按关键词搜索酷我曲目。
 *
 * 必须带上酷我客户端的参数（uid/ver/vipver/strategy/...）：
 * 精简参数时接口只回翻唱和伴奏，带客户端参数才会把原版排在最前，
 * 这也是落雪等客户端能跨平台换源的原因。
 */
internal suspend fun fetchLxKuwoHits(
    client: OkHttpClient,
    keyword: String,
    limit: Int = LX_CROSS_PLATFORM_SEARCH_LIMIT
): List<LxCrossPlatformHit> = withContext(Dispatchers.IO) {
    if (keyword.isBlank() || limit <= 0) return@withContext emptyList()
    val url = buildString {
        append("https://search.kuwo.cn/r.s?client=kt&uid=")
        append(LX_KUWO_SEARCH_UID)
        append("&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1")
        append("&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1")
        append("&all=")
        append(java.net.URLEncoder.encode(keyword, "UTF-8"))
        append("&pn=0&rn=")
        append(limit)
    }
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        .header("Referer", "https://www.kuwo.cn/")
        .header("Accept", "application/json")
        .get()
        .build()
    runCatching {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                NPLogger.w(TAG, "Kuwo search http ${response.code}: keyword=$keyword")
                return@use emptyList()
            }
            parseLxKuwoSearchBody(body)
        }
    }.getOrElse { error ->
        NPLogger.w(TAG, "Kuwo search failed: keyword=$keyword, error=${error.message}")
        emptyList()
    }
}

/**
 * 跨平台曲目 ID 的内存缓存。
 * 在线音源服务端的某个平台通道挂掉时，用它避免每次播放都重新搜索。
 */
internal object LxCrossPlatformHitCache {
    private val cache = ConcurrentHashMap<String, LxCrossPlatformHit>()

    fun get(songKey: String): LxCrossPlatformHit? = cache[songKey]

    fun put(songKey: String, hit: LxCrossPlatformHit) {
        if (cache.size >= LX_CROSS_PLATFORM_CACHE_LIMIT) cache.clear()
        cache[songKey] = hit
    }

    fun clear() = cache.clear()
}

/** 用于日志：把候选拼成一行，便于排查匹配结果 */
internal fun LxCrossPlatformHit.describe(): String {
    return "$sourceId/$songMid ${name.take(20)} - ${artist.take(20)} (${durationSec}s)"
}

/** 供单测构造酷我风格的搜索响应 */
internal fun lxKuwoSearchBodyForTesting(entries: List<LxCrossPlatformHit>): String {
    val list = entries.joinToString(",") { hit ->
        "{'DC_TARGETID':'${hit.songMid}','NAME':'${hit.name}'," +
            "'ARTIST':'${hit.artist}','DURATION':'${hit.durationSec}'," +
            "'ALBUM':'${hit.albumName}','MUSICRID':'MUSIC_${hit.songMid}'}"
    }
    return "{'ARTISTPIC':'','HIT':'1','abslist':[$list]}"
}
