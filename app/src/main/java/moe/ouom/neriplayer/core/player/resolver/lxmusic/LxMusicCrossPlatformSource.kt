package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.source.lxmusic.LxImportedSource
import moe.ouom.neriplayer.data.source.lxmusic.js.LxJsSourceEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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

/** 酷狗平台在落雪协议里的 source id */
internal const val LX_KUGOU_PLATFORM_ID = "kg"

/** QQ 音乐在落雪协议里的 source id */
internal const val LX_QQ_PLATFORM_ID = "tx"

/** 跨平台兜底按顺序尝试的平台（酷我 → 酷狗 → QQ） */
internal val LX_CROSS_PLATFORM_ORDER = listOf(
    LX_KUWO_PLATFORM_ID,
    LX_KUGOU_PLATFORM_ID,
    LX_QQ_PLATFORM_ID
)

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
    val albumName: String,
    val albumId: String = "",
    val coverUrl: String? = null,
    /**
     * 平台专有：酷狗按音质档位区分的文件 hash。
     * 音源站的酷狗通道实际按 hash 取流，缺了它只会拿到 502。
     */
    val qualityHashes: Map<String, String> = emptyMap()
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

internal fun selectLxCrossPlatformHit(
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
        albumName = decodeLxHtmlEntities(optString("ALBUM")).trim(),
        coverUrl = sequenceOf("PIC", "pic", "IMG", "img", "ALBUMPIC", "albumpic")
            .map { key -> optString(key) }
            .firstOrNull { it.isNotBlank() }
            ?.let { normalizeLxCoverUrl(it) }
            ?.takeIf { !isPlaceholderLxCoverUrl(it) }
            ?: normalizeLxCoverUrl(optString("DC_TARGETPIC"))?.takeIf { !isPlaceholderLxCoverUrl(it) }
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
            albumName = block.lxField("ALBUM")?.let(::decodeLxHtmlEntities).orEmpty().trim(),
            coverUrl = listOf("PIC", "pic", "IMG", "img", "DC_TARGETPIC")
                .mapNotNull { key -> block.lxField(key) }
                .firstOrNull { it.isNotBlank() }
                ?.let { normalizeLxCoverUrl(it) }
                ?.takeIf { !isPlaceholderLxCoverUrl(it) }
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
 * 按关键词在指定平台搜索曲目。
 * 每个平台都用该平台客户端实际使用的搜索入口，否则拿不到原版。
 */
internal suspend fun fetchLxCrossPlatformHits(
    client: OkHttpClient,
    sourceId: String,
    keyword: String,
    limit: Int = LX_CROSS_PLATFORM_SEARCH_LIMIT,
    page: Int = 1
): List<LxCrossPlatformHit> {
    if (keyword.isBlank() || limit <= 0) return emptyList()
    return when (sourceId) {
        LX_KUWO_PLATFORM_ID -> fetchLxKuwoHits(client, keyword, limit, page)
        LX_KUGOU_PLATFORM_ID -> fetchLxKugouHits(client, keyword, limit, page)
        LX_QQ_PLATFORM_ID -> fetchLxQqHits(client, keyword, limit, page)
        else -> emptyList()
    }
}

/** 按关键词搜索酷我曲目 */
internal suspend fun fetchLxKuwoHits(
    client: OkHttpClient,
    keyword: String,
    limit: Int = LX_CROSS_PLATFORM_SEARCH_LIMIT,
    page: Int = 1
): List<LxCrossPlatformHit> {
    val url = buildString {
        append("https://search.kuwo.cn/r.s?client=kt&uid=")
        append(LX_KUWO_SEARCH_UID)
        append("&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1")
        append("&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1")
        append("&all=")
        append(java.net.URLEncoder.encode(keyword, "UTF-8"))
        append("&pn=${(page - 1).coerceAtLeast(0)}&rn=")
        append(limit)
    }
    val body = fetchText(
        client = client,
        url = url,
        referer = "https://www.kuwo.cn/",
        label = "Kuwo",
        keyword = keyword
    ) ?: return emptyList()
    return parseLxKuwoSearchBody(body)
}

/** 按关键词搜索酷狗曲目（song_search_v2，明文接口） */
internal suspend fun fetchLxKugouHits(
    client: OkHttpClient,
    keyword: String,
    limit: Int = LX_CROSS_PLATFORM_SEARCH_LIMIT,
    page: Int = 1
): List<LxCrossPlatformHit> {
    val url = buildString {
        append("https://songsearch.kugou.com/song_search_v2?keyword=")
        append(java.net.URLEncoder.encode(keyword, "UTF-8"))
        append("&page=${page.coerceAtLeast(1)}&pagesize=")
        append(limit)
        append("&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1")
    }
    val body = fetchText(
        client = client,
        url = url,
        referer = "https://www.kugou.com/",
        label = "Kugou",
        keyword = keyword
    ) ?: return emptyList()
    return parseLxKugouSearchBody(body)
}

/** 按关键词搜索 QQ 音乐曲目 */
internal suspend fun fetchLxQqHits(
    client: OkHttpClient,
    keyword: String,
    limit: Int = LX_CROSS_PLATFORM_SEARCH_LIMIT,
    page: Int = 1
): List<LxCrossPlatformHit> {
    val url = buildString {
        append("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=")
        append(java.net.URLEncoder.encode(keyword, "UTF-8"))
        append("&format=json&n=")
        append(limit)
        append("&p=${page.coerceAtLeast(1)}&cr=1&t=0&new_json=1")
    }
    val body = fetchText(
        client = client,
        url = url,
        referer = "https://y.qq.com/",
        label = "QQMusic",
        keyword = keyword
    ) ?: return emptyList()
    return parseLxQqSearchBody(body)
}

private suspend fun fetchText(
    client: OkHttpClient,
    url: String,
    referer: String,
    label: String,
    keyword: String
): String? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        .header("Referer", referer)
        .header("Accept", "application/json")
        .get()
        .build()
    runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                NPLogger.w(TAG, "$label search http ${response.code}: keyword=$keyword")
                return@use null
            }
            response.body?.string()
        }
    }.getOrElse { error ->
        NPLogger.w(TAG, "$label search failed: keyword=$keyword, error=${error.message}")
        null
    }
}

/** 解析酷狗搜索响应 */
internal fun parseLxKugouSearchBody(body: String): List<LxCrossPlatformHit> {
    if (body.isBlank() || !body.contains("Audioid")) return emptyList()
    return runCatching {
        val lists = JSONObject(body)
            .optJSONObject("data")
            ?.optJSONArray("lists")
            ?: return emptyList()
        buildList {
            for (index in 0 until lists.length()) {
                val item = lists.optJSONObject(index) ?: continue
                val songMid = item.optString("Audioid").trim()
                if (songMid.isBlank()) continue
                val title = decodeLxHtmlEntities(item.optString("OriSongName")).trim()
                if (title.isBlank()) continue
                val suffix = decodeLxHtmlEntities(item.optString("Suffix")).trim()
                val qualityHashes = buildMap {
                    item.optString("FileHash").takeIf { it.isNotBlank() }?.let { put("128k", it) }
                    item.optString("HQFileHash").takeIf { it.isNotBlank() }?.let { put("320k", it) }
                    item.optString("SQFileHash").takeIf { it.isNotBlank() }?.let { put("flac", it) }
                    item.optString("ResFileHash").takeIf { it.isNotBlank() }?.let { put("flac24bit", it) }
                }
                if (qualityHashes.isEmpty()) continue
                val albumId = item.optString("AlbumID").trim()
                val coverUrl = sequenceOf("Image", "image", "Pic", "pic", "AlbumCover", "albumCover", "album_img")
                    .map { key -> item.optString(key) }
                    .firstOrNull { it.isNotBlank() }
                    ?.let { raw -> normalizeLxCoverUrl(raw) }
                    ?.takeIf { !isPlaceholderLxCoverUrl(it) }
                add(
                    LxCrossPlatformHit(
                        sourceId = LX_KUGOU_PLATFORM_ID,
                        songMid = songMid,
                        // 酷狗把版本写在 Suffix 里，拼回歌名才能让评分识别出 Live 等版本
                        name = if (suffix.isBlank()) title else "$title $suffix",
                        artist = parseLxKugouSingers(item.optJSONArray("Singers")),
                        durationSec = item.optString("Duration").trim().toIntOrNull() ?: 0,
                        albumName = decodeLxHtmlEntities(item.optString("AlbumName")).trim(),
                        albumId = albumId,
                        qualityHashes = qualityHashes,
                        coverUrl = coverUrl
                    )
                )
            }
        }.distinctBy { it.songMid }
    }.getOrElse { error ->
        NPLogger.w(TAG, "Kugou search parse failed: ${error.message}")
        emptyList()
    }
}

private fun parseLxKugouSingers(singers: org.json.JSONArray?): String {
    if (singers == null) return ""
    val names = buildList {
        for (index in 0 until singers.length()) {
            val singer = singers.optJSONObject(index) ?: continue
            decodeLxHtmlEntities(singer.optString("name")).trim()
                .takeIf { it.isNotBlank() }
                ?.let { add(it) }
        }
    }
    return names.joinToString("、")
}

/** 解析 QQ 音乐搜索响应（兼容 new_json=1 与旧字段） */
internal fun parseLxQqSearchBody(body: String): List<LxCrossPlatformHit> {
    if (body.isBlank()) return emptyList()
    return runCatching {
        val list = JSONObject(body)
            .optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: return emptyList()
        buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val songMid = sequenceOf("mid", "songmid", "strMediaMid")
                    .map { key -> item.optString(key).trim() }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                if (songMid.isBlank()) continue
                val title = decodeLxHtmlEntities(item.optString("title")).trim()
                    .ifBlank { decodeLxHtmlEntities(item.optString("name")).trim() }
                    .ifBlank { decodeLxHtmlEntities(item.optString("songname")).trim() }
                if (title.isBlank()) continue
                val albumObj = item.optJSONObject("album")
                val albumMid = sequenceOf("mid", "pmid")
                    .map { key -> albumObj?.optString(key).orEmpty() }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                val albumCover = sequenceOf("pic", "picUrl", "cover")
                    .map { key -> albumObj?.optString(key).orEmpty() }
                    .firstOrNull { it.isNotBlank() }
                    ?.let { normalizeLxCoverUrl(it) }
                    ?.takeIf { !isPlaceholderLxCoverUrl(it) }
                    ?: albumMid.takeIf { it.isNotBlank() && it.all { ch -> ch.isLetterOrDigit() } }
                        ?.let { "https://y.qq.com/music/photo_new/T002R300x300M000${it}.jpg" }
                add(
                    LxCrossPlatformHit(
                        sourceId = LX_QQ_PLATFORM_ID,
                        songMid = songMid,
                        name = title,
                        artist = parseLxQqSingers(item.optJSONArray("singer")),
                        durationSec = item.optInt("interval", 0),
                        albumName = decodeLxHtmlEntities(
                            albumObj?.optString("title")?.ifBlank { albumObj.optString("name") }
                                ?: albumObj?.optString("name").orEmpty()
                        ).trim(),
                        albumId = (albumObj?.optLong("id", 0L) ?: 0L)
                            .takeIf { it > 0L }
                            ?.toString()
                            .orEmpty()
                            .ifBlank { albumMid },
                        coverUrl = albumCover
                    )
                )
            }
        }.distinctBy { it.songMid }
    }.getOrElse { error ->
        NPLogger.w(TAG, "QQ search parse failed: ${error.message}")
        emptyList()
    }
}

private fun parseLxQqSingers(singers: org.json.JSONArray?): String {
    if (singers == null) return ""
    val names = buildList {
        for (index in 0 until singers.length()) {
            val singer = singers.optJSONObject(index) ?: continue
            decodeLxHtmlEntities(singer.optString("name")).trim()
                .takeIf { it.isNotBlank() }
                ?.let { add(it) }
        }
    }
    return names.joinToString("、")
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

/**
 * 跨平台取流用的 musicInfo。
 *
 * 除了通用字段，酷狗还需要按音质档位传文件 hash——音源站的酷狗通道实际是按 hash 取流的
 * （只传 Audioid 会得到 502），因此 hash 同时写进 `hash` 与 `_types[quality]`。
 */
internal fun buildLxCrossPlatformMusicInfoJson(
    song: SongItem,
    hit: LxCrossPlatformHit,
    quality: String
): String {
    val base = buildLxOldMusicInfoJson(
        song = song,
        platformSourceId = hit.sourceId,
        songMid = hit.songMid
    )
    if (hit.qualityHashes.isEmpty()) return base
    return runCatching {
        JSONObject(base).apply {
            hit.qualityHashes[quality]?.let { put("hash", it) }
            val types = JSONArray()
            val typesByQuality = JSONObject()
            hit.qualityHashes.forEach { (qualityKey, hash) ->
                types.put(
                    JSONObject().apply {
                        put("type", qualityKey)
                        put("hash", hash)
                    }
                )
                typesByQuality.put(
                    qualityKey,
                    JSONObject().apply { put("hash", hash) }
                )
            }
            put("types", types)
            put("_types", typesByQuality)
        }.toString()
    }.getOrElse { base }
}

/**
 * 平台通道探测用的固定曲目（同一首歌在各平台都存在）。
 * 用于「音源通道自检」：请求能出流说明该平台通道是活的。
 */
internal val LX_CHANNEL_PROBE_TARGETS: List<LxCrossPlatformHit> = listOf(
    LxCrossPlatformHit(
        sourceId = LX_KUWO_PLATFORM_ID,
        songMid = "228908",
        name = "晴天",
        artist = "周杰伦",
        durationSec = 269,
        albumName = "叶惠美"
    ),
    LxCrossPlatformHit(
        sourceId = LX_KUGOU_PLATFORM_ID,
        songMid = "20505418",
        name = "晴天",
        artist = "周杰伦",
        durationSec = 269,
        albumName = "叶惠美",
        qualityHashes = mapOf(
            "128k" to "B3A52A7A958BF0AED0EBFBA2E9A818B7",
            "320k" to "1B56126A8A03924F1DD066259C095CBC",
            "flac" to "0A69169202DE95AAF24A9944CCF0730D"
        )
    ),
    LxCrossPlatformHit(
        sourceId = LX_QQ_PLATFORM_ID,
        songMid = "0039MnYb0qxYhV",
        name = "晴天",
        artist = "周杰伦",
        durationSec = 269,
        albumName = "叶惠美"
    )
)

/** 供单测构造酷我风格的搜索响应 */
internal fun lxKuwoSearchBodyForTesting(entries: List<LxCrossPlatformHit>): String {
    val list = entries.joinToString(",") { hit ->
        "{'DC_TARGETID':'${hit.songMid}','NAME':'${hit.name}'," +
            "'ARTIST':'${hit.artist}','DURATION':'${hit.durationSec}'," +
            "'ALBUM':'${hit.albumName}','MUSICRID':'MUSIC_${hit.songMid}'}"
    }
    return "{'ARTISTPIC':'','HIT':'1','abslist':[$list]}"
}

/** 单个平台通道的探测结果 */
data class LxChannelProbeResult(
    val sourceId: String,
    val healthy: Boolean,
    val detail: String
)

/** 通道探测用的固定曲目（各平台都有这首，用来验证通道而不是验证匹配） */
internal val LX_CHANNEL_PROBE_SONG = SongItem(
    id = 186016L,
    name = "晴天",
    artist = "周杰伦",
    album = "叶惠美",
    albumId = 1293L,
    durationMs = 269_000L,
    coverUrl = null
)

/**
 * 探测在线音源各平台通道是否可用（音源健康自检）。
 *
 * 用《晴天》这首各平台都存在的歌各请求一次取流：
 * 能拿到 URL 说明该平台通道是活的，502 之类说明服务端那条通道故障。
 */
internal suspend fun probeLxSourceChannels(
    context: android.content.Context,
    source: LxImportedSource
): List<LxChannelProbeResult> {
    if (!source.isJsSource) return emptyList()
    val repository = runCatching { AppContainer.lxMusicSourceRepository }.getOrNull()
        ?: return emptyList()
    val script = repository.readJsScript(source) ?: return emptyList()
    val engine = LxJsSourceEngine
    if (!engine.ensureLoaded(
            context = context,
            okHttpClient = AppContainer.sharedOkHttpClient,
            sourceId = source.id,
            sourceName = source.name,
            script = script,
            description = source.description,
            version = source.version,
            author = source.author
        )
    ) {
        return emptyList()
    }
    val runtime = engine.runtime(source.id) ?: return emptyList()
    val supportedSources = runtime.supportedSources
    val quality = selectLxQualityOrder(
        preferredNeteaseQuality = "exhigh",
        supportedQualities = runtime.supportedQualities,
        maxAttempts = 1
    ).firstOrNull() ?: "320k"

    return LX_CHANNEL_PROBE_TARGETS
        .filter { supportedSources.isEmpty() || it.sourceId in supportedSources }
        .map { hit ->
            engine.clearFailureReasons()
            val url = runtime.getMusicUrl(
                sourceId = hit.sourceId,
                quality = quality,
                musicInfoJson = buildLxCrossPlatformMusicInfoJson(
                    song = LX_CHANNEL_PROBE_SONG,
                    hit = hit,
                    quality = quality
                ),
                timeoutMs = LX_PROBE_CALL_TIMEOUT_MS
            )
            LxChannelProbeResult(
                sourceId = hit.sourceId,
                healthy = url != null,
                detail = url ?: engine.activeFailureReason().orEmpty().ifBlank { "failed" }
            )
        }
}

private const val LX_PROBE_CALL_TIMEOUT_MS = 6_000L
