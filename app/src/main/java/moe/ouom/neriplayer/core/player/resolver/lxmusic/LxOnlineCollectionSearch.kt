package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.util.network.awaitResponse
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val TAG = "NERI-LxCollectionSearch"
private const val PAGE_SIZE = 20
private const val DETAIL_PAGE_SIZE = 100

enum class LxOnlineCollectionType { ARTIST, PLAYLIST }

data class LxOnlineCollection(
    val type: LxOnlineCollectionType,
    val sourceId: String,
    val id: String,
    val name: String,
    val coverUrl: String?,
    val creator: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0L
)

internal data class LxOnlineCollectionPage(
    val items: List<LxOnlineCollection>,
    val hasMore: Boolean
)

internal data class LxOnlineCollectionSongsPage(
    val songs: List<SongItem>,
    val hasMore: Boolean
)

internal suspend fun searchLxOnlineCollections(
    keyword: String,
    page: Int,
    type: LxOnlineCollectionType
): LxOnlineCollectionPage = coroutineScope {
    val engines = lxOnlineSearchEngines()
    val platforms = LX_ONLINE_SEARCH_PLATFORM_ORDER.filter { it in engines }
    val client = AppContainer.sharedOkHttpClient
    val pages = platforms.map { platform ->
        async {
            try {
                val body = client.lxCollectionGet(searchUrl(platform, keyword, page, type))
                parseLxOnlineCollectionPage(platform, type, body, page)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                NPLogger.w(TAG, "$platform $type search failed: ${error.message}")
                LxOnlineCollectionPage(emptyList(), false)
            }
        }
    }.awaitAll()
    LxOnlineCollectionPage(
        items = pages.flatMap { it.items }.distinctBy { "${it.sourceId}|${it.type}|${it.id}" },
        hasMore = pages.any { it.hasMore }
    )
}

internal suspend fun loadLxOnlineCollectionSongs(
    collection: LxOnlineCollection,
    page: Int
): LxOnlineCollectionSongsPage {
    val client = AppContainer.sharedOkHttpClient
    if (collection.type == LxOnlineCollectionType.ARTIST) {
        if (collection.sourceId == LX_KUGOU_PLATFORM_ID) {
            val url = HttpUrl.Builder().scheme("http").host("mobiles.kugou.com")
                .addPathSegments("api/v5/singer/song")
                .addQueryParameter("singerid", collection.id)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pagesize", DETAIL_PAGE_SIZE.toString())
                .build()
            return parseKugouPlaylistSongs(client.lxCollectionGet(url), page)
        }
        val hits = if (collection.sourceId == LX_NETEASE_PLATFORM_ID) {
            val body = client.lxCollectionGet(neteaseSearchUrl(collection.name, page, 1))
            parseNeteaseSongHits(body)
        } else {
            fetchLxCrossPlatformHits(
                client = client,
                sourceId = collection.sourceId,
                keyword = collection.name,
                limit = PAGE_SIZE,
                page = page
            )
        }
        val name = collection.name.trim()
        val songs = hits.filter { hit ->
            hit.artist.split('、', '/', '&', '，').any { artist ->
                artist.trim().equals(name, ignoreCase = true)
            }
        }.map { toLxSongItem(it) }
        return LxOnlineCollectionSongsPage(songs, hits.size >= PAGE_SIZE)
    }
    val body = client.lxCollectionGet(playlistDetailUrl(collection, page))
    return when (collection.sourceId) {
        LX_QQ_PLATFORM_ID -> parseQqPlaylistSongs(body)
        LX_KUWO_PLATFORM_ID -> parseKuwoPlaylistSongs(body)
        LX_KUGOU_PLATFORM_ID -> parseKugouPlaylistSongs(body, page)
        LX_NETEASE_PLATFORM_ID -> {
            val ids = JSONObject(body).optJSONObject("playlist")?.optJSONArray("trackIds")
                ?: return LxOnlineCollectionSongsPage(emptyList(), false)
            val offset = (page - 1).coerceAtLeast(0) * DETAIL_PAGE_SIZE
            val pageIds = (offset until minOf(ids.length(), offset + DETAIL_PAGE_SIZE))
                .mapNotNull { index -> ids.optJSONObject(index)?.optLong("id")?.takeIf { it > 0L } }
            if (pageIds.isEmpty()) return LxOnlineCollectionSongsPage(emptyList(), false)
            val detailUrl = HttpUrl.Builder().scheme("https").host("music.163.com")
                .addPathSegments("api/song/detail")
                .addQueryParameter("ids", pageIds.joinToString(prefix = "[", postfix = "]"))
                .build()
            val songsBody = client.lxCollectionGet(detailUrl)
            LxOnlineCollectionSongsPage(
                songs = parseNeteaseSongHits(songsBody, isDetail = true).map(::toLxSongItem),
                hasMore = offset + DETAIL_PAGE_SIZE < ids.length()
            )
        }
        else -> LxOnlineCollectionSongsPage(emptyList(), false)
    }
}

private fun searchUrl(
    sourceId: String,
    keyword: String,
    page: Int,
    type: LxOnlineCollectionType
): HttpUrl = when (sourceId) {
    LX_QQ_PLATFORM_ID -> if (type == LxOnlineCollectionType.ARTIST) {
        HttpUrl.Builder().scheme("https").host("c.y.qq.com")
            .addPathSegments("soso/fcgi-bin/client_search_cp")
            .addQueryParameter("p", page.toString())
            .addQueryParameter("n", PAGE_SIZE.toString())
            .addQueryParameter("w", keyword)
            .addQueryParameter("t", "9")
            .addQueryParameter("format", "json")
            .build()
    } else {
        HttpUrl.Builder().scheme("https").host("c.y.qq.com")
            .addPathSegments("soso/fcgi-bin/client_music_search_songlist")
            .addQueryParameter("page_no", (page - 1).coerceAtLeast(0).toString())
            .addQueryParameter("num_per_page", PAGE_SIZE.toString())
            .addQueryParameter("query", keyword)
            .addQueryParameter("remoteplace", "txt.yqq.playlist")
            .addQueryParameter("format", "json")
            .build()
    }
    LX_KUWO_PLATFORM_ID -> HttpUrl.Builder().scheme("https").host("search.kuwo.cn")
        .addPathSegment("r.s")
        .addQueryParameter("all", keyword)
        .addQueryParameter("pn", (page - 1).coerceAtLeast(0).toString())
        .addQueryParameter("rn", PAGE_SIZE.toString())
        .addQueryParameter("rformat", "json")
        .addQueryParameter("encoding", "utf8")
        .addQueryParameter("ver", "mbox")
        .addQueryParameter("vipver", "MUSIC_8.7.7.0_BCS37")
        .addQueryParameter("plat", "pc")
        .addQueryParameter("devid", "28156413")
        .addQueryParameter("ft", if (type == LxOnlineCollectionType.ARTIST) "artist" else "playlist")
        .addQueryParameter("pay", "0")
        .addQueryParameter("needliveshow", "0")
        .build()
    LX_KUGOU_PLATFORM_ID -> HttpUrl.Builder().scheme("http").host("msearchretry.kugou.com")
        .addPathSegments("api/v3/search/${if (type == LxOnlineCollectionType.ARTIST) "singer" else "special"}")
        .addQueryParameter("keyword", keyword)
        .addQueryParameter("page", page.toString())
        .addQueryParameter("pagesize", PAGE_SIZE.toString())
        .addQueryParameter("version", "7910")
        .addQueryParameter("showtype", "10")
        .addQueryParameter("filter", "0")
        .addQueryParameter("sver", "2")
        .build()
    LX_NETEASE_PLATFORM_ID -> neteaseSearchUrl(
        keyword,
        page,
        if (type == LxOnlineCollectionType.ARTIST) 100 else 1000
    )
    else -> error("Unsupported online collection source: $sourceId")
}

private fun neteaseSearchUrl(keyword: String, page: Int, type: Int): HttpUrl =
    HttpUrl.Builder().scheme("https").host("music.163.com")
        .addPathSegments("api/search/get/web")
        .addQueryParameter("s", keyword)
        .addQueryParameter("type", type.toString())
        .addQueryParameter("limit", PAGE_SIZE.toString())
        .addQueryParameter("offset", ((page - 1).coerceAtLeast(0) * PAGE_SIZE).toString())
        .build()

private fun playlistDetailUrl(collection: LxOnlineCollection, page: Int): HttpUrl = when (collection.sourceId) {
    LX_QQ_PLATFORM_ID -> HttpUrl.Builder().scheme("https").host("c.y.qq.com")
        .addPathSegments("qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg")
        .addQueryParameter("type", "1")
        .addQueryParameter("json", "1")
        .addQueryParameter("utf8", "1")
        .addQueryParameter("onlysong", "0")
        .addQueryParameter("new_format", "1")
        .addQueryParameter("disstid", collection.id)
        .addQueryParameter("loginUin", "0")
        .addQueryParameter("hostUin", "0")
        .addQueryParameter("format", "json")
        .addQueryParameter("platform", "yqq.json")
        .addQueryParameter("song_begin", ((page - 1).coerceAtLeast(0) * DETAIL_PAGE_SIZE).toString())
        .addQueryParameter("song_num", DETAIL_PAGE_SIZE.toString())
        .build()
    LX_KUWO_PLATFORM_ID -> HttpUrl.Builder().scheme("https").host("nplserver.kuwo.cn")
        .addPathSegment("pl.svc")
        .addQueryParameter("op", "getlistinfo")
        .addQueryParameter("pid", collection.id)
        .addQueryParameter("pn", ((page - 1).coerceAtLeast(0) * DETAIL_PAGE_SIZE).toString())
        .addQueryParameter("rn", DETAIL_PAGE_SIZE.toString())
        .addQueryParameter("encode", "utf-8")
        .addQueryParameter("keyset", "pl2012")
        .addQueryParameter("vipver", "MUSIC_9.1.1.2_BCS2")
        .build()
    LX_KUGOU_PLATFORM_ID -> HttpUrl.Builder().scheme("http").host("mobilecdn.kugou.com")
        .addPathSegments("api/v3/special/song")
        .addQueryParameter("specialid", collection.id)
        .addQueryParameter("page", page.toString())
        .addQueryParameter("pagesize", DETAIL_PAGE_SIZE.toString())
        .addQueryParameter("plat", "2")
        .addQueryParameter("version", "8400")
        .build()
    LX_NETEASE_PLATFORM_ID -> HttpUrl.Builder().scheme("https").host("music.163.com")
        .addPathSegments("api/v3/playlist/detail")
        .addQueryParameter("id", collection.id)
        .addQueryParameter("n", "0")
        .addQueryParameter("s", "8")
        .build()
    else -> error("Unsupported online playlist source: ${collection.sourceId}")
}

private suspend fun OkHttpClient.lxCollectionGet(url: HttpUrl): String {
    val request = Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0")
        .header("Referer", if (url.host == "music.163.com") "https://music.163.com/" else "https://y.qq.com/")
        .build()
    return newCall(request).awaitResponse { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        response.body.string().ifBlank { throw IOException("Empty online search response") }
    }
}

internal fun parseLxOnlineCollectionPage(
    sourceId: String,
    type: LxOnlineCollectionType,
    body: String,
    page: Int
): LxOnlineCollectionPage {
    val root = JSONObject(body)
    val data: JSONObject?
    val list: JSONArray?
    val total: Int
    when (sourceId) {
        LX_QQ_PLATFORM_ID -> {
            if (root.optInt("code", -1) != 0) return LxOnlineCollectionPage(emptyList(), false)
            data = if (type == LxOnlineCollectionType.ARTIST) {
                root.optJSONObject("data")?.optJSONObject("singer")
            } else root.optJSONObject("data")
            list = data?.optJSONArray("list")
            total = data?.optInt(if (type == LxOnlineCollectionType.ARTIST) "totalnum" else "sum") ?: 0
        }
        LX_KUWO_PLATFORM_ID -> {
            data = root
            list = root.optJSONArray("abslist")
            total = root.optInt("TOTAL", 0)
        }
        LX_KUGOU_PLATFORM_ID -> {
            if (root.optInt("errcode", -1) != 0) return LxOnlineCollectionPage(emptyList(), false)
            data = root.optJSONObject("data")
            list = if (type == LxOnlineCollectionType.ARTIST) root.optJSONArray("data")
                else data?.optJSONArray("info")
            total = if (type == LxOnlineCollectionType.ARTIST) 0 else data?.optInt("total", 0) ?: 0
        }
        LX_NETEASE_PLATFORM_ID -> {
            if (root.optInt("code", -1) != 200) return LxOnlineCollectionPage(emptyList(), false)
            data = root.optJSONObject("result")
            list = data?.optJSONArray(if (type == LxOnlineCollectionType.ARTIST) "artists" else "playlists")
            total = data?.optInt(if (type == LxOnlineCollectionType.ARTIST) "artistCount" else "playlistCount") ?: 0
        }
        else -> return LxOnlineCollectionPage(emptyList(), false)
    }
    val items = buildList {
        if (list == null) return@buildList
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val parsed = parseLxOnlineCollection(sourceId, type, item) ?: continue
            add(parsed)
        }
    }
    return LxOnlineCollectionPage(
        items = items,
        hasMore = total > page * PAGE_SIZE || (total == 0 && items.size >= PAGE_SIZE)
    )
}

private fun parseLxOnlineCollection(
    sourceId: String,
    type: LxOnlineCollectionType,
    item: JSONObject
): LxOnlineCollection? {
    val id: String
    val name: String
    val cover: String?
    val creator: String
    val trackCount: Int
    val playCount: Long
    when (sourceId) {
        LX_QQ_PLATFORM_ID -> if (type == LxOnlineCollectionType.ARTIST) {
            id = item.optString("singerMID")
            name = item.optString("singerName")
            cover = item.optString("singerPic")
            creator = ""
            trackCount = item.optInt("songNum")
            playCount = 0L
        } else {
            id = item.optString("dissid")
            name = item.optString("dissname")
            cover = item.optString("imgurl")
            creator = item.optJSONObject("creator")?.optString("name").orEmpty()
            trackCount = item.optInt("song_count")
            playCount = item.optLong("listennum")
        }
        LX_KUWO_PLATFORM_ID -> if (type == LxOnlineCollectionType.ARTIST) {
            id = item.optString("ARTISTID")
            name = item.optString("ARTIST")
            cover = item.optString("PICPATH").takeIf { it.isNotBlank() }
                ?.let { "https://img1.kuwo.cn/star/starheads/$it" }
            creator = ""
            trackCount = item.optInt("SONGNUM")
            playCount = 0L
        } else {
            id = item.optString("playlistid")
            name = item.optString("name")
            cover = item.optString("pic")
            creator = item.optString("nickname")
            trackCount = item.optInt("songnum")
            playCount = item.optLong("playcnt")
        }
        LX_KUGOU_PLATFORM_ID -> if (type == LxOnlineCollectionType.ARTIST) {
            id = item.optString("singerid")
            name = item.optString("singername")
            cover = null
            creator = ""
            trackCount = 0
            playCount = 0L
        } else {
            id = item.optString("specialid")
            name = item.optString("specialname")
            cover = item.optString("imgurl").replace("{size}", "240")
            creator = item.optString("nickname")
            trackCount = item.optInt("songcount")
            playCount = item.optLong("playcount")
        }
        LX_NETEASE_PLATFORM_ID -> if (type == LxOnlineCollectionType.ARTIST) {
            id = item.optString("id")
            name = item.optString("name")
            cover = item.optString("picUrl").ifBlank { item.optString("img1v1Url") }
            creator = ""
            trackCount = item.optInt("musicSize")
            playCount = 0L
        } else {
            id = item.optString("id")
            name = item.optString("name")
            cover = item.optString("coverImgUrl").ifBlank { item.optString("picUrl") }
            creator = item.optJSONObject("creator")?.optString("nickname").orEmpty()
            trackCount = item.optInt("trackCount")
            playCount = item.optLong("playCount")
        }
        else -> return null
    }
    if (id.isBlank() || name.isBlank()) return null
    return LxOnlineCollection(
        type = type,
        sourceId = sourceId,
        id = id,
        name = name,
        coverUrl = normalizeLxCoverUrl(cover),
        creator = creator,
        trackCount = trackCount,
        playCount = playCount
    )
}

internal fun parseQqPlaylistSongs(body: String): LxOnlineCollectionSongsPage {
    val data = JSONObject(body).optJSONArray("cdlist")?.optJSONObject(0)
        ?: return LxOnlineCollectionSongsPage(emptyList(), false)
    val list = data.optJSONArray("songlist") ?: JSONArray()
    val wrapped = JSONObject().put("data", JSONObject().put("song", JSONObject().put("list", list)))
    return LxOnlineCollectionSongsPage(
        songs = parseLxQqSearchBody(wrapped.toString()).map(::toLxSongItem),
        hasMore = data.optInt("song_begin") + list.length() < data.optInt("total_song_num")
    )
}

internal fun parseKuwoPlaylistSongs(body: String): LxOnlineCollectionSongsPage {
    val root = JSONObject(body)
    val list = root.optJSONArray("musiclist") ?: JSONArray()
    val songs = buildList {
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val id = item.optString("id").ifBlank { item.optString("rid") }
            val name = item.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            add(toLxSongItem(LxCrossPlatformHit(
                sourceId = LX_KUWO_PLATFORM_ID,
                songMid = id,
                name = name,
                artist = item.optString("artist"),
                durationSec = item.optInt("duration"),
                albumName = item.optString("album"),
                albumId = item.optString("albumid"),
                coverUrl = item.optString("albumpic")
            )))
        }
    }
    return LxOnlineCollectionSongsPage(
        songs = songs,
        hasMore = root.optInt("pn") + list.length() < root.optInt("total")
    )
}

internal fun parseKugouPlaylistSongs(body: String, page: Int): LxOnlineCollectionSongsPage {
    val root = JSONObject(body)
    val data = root.optJSONObject("data") ?: return LxOnlineCollectionSongsPage(emptyList(), false)
    val list = data.optJSONArray("info") ?: JSONArray()
    val songs = buildList {
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val hash = item.optString("hash")
            val id = item.optString("audio_id")
            val filename = item.optString("filename")
            val name = item.optString("songname").ifBlank { filename.substringAfter(" - ", filename) }
            if (id.isBlank() || hash.isBlank() || name.isBlank()) continue
            add(toLxSongItem(LxCrossPlatformHit(
                sourceId = LX_KUGOU_PLATFORM_ID,
                songMid = id,
                name = name,
                artist = item.optString("singername").ifBlank { filename.substringBefore(" - ", "") },
                durationSec = item.optInt("duration"),
                albumName = item.optString("albumname"),
                albumId = item.optString("album_id"),
                qualityHashes = buildMap {
                    put("128k", hash)
                    item.optString("320hash").takeIf { it.isNotBlank() }?.let { put("320k", it) }
                    item.optString("sqhash").takeIf { it.isNotBlank() }?.let { put("flac", it) }
                }
            )))
        }
    }
    return LxOnlineCollectionSongsPage(songs, page * DETAIL_PAGE_SIZE < data.optInt("total"))
}

internal fun parseNeteaseSongHits(body: String, isDetail: Boolean = false): List<LxCrossPlatformHit> {
    val root = JSONObject(body)
    if (root.optInt("code", -1) != 200) return emptyList()
    val list = (if (isDetail) root.optJSONArray("songs")
        else root.optJSONObject("result")?.optJSONArray("songs"))
        ?: return emptyList()
    return buildList {
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val name = item.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            val artists = item.optJSONArray("ar") ?: item.optJSONArray("artists") ?: JSONArray()
            val artistNames = (0 until artists.length()).mapNotNull { artistIndex ->
                artists.optJSONObject(artistIndex)?.optString("name")?.takeIf { it.isNotBlank() }
            }
            val album = item.optJSONObject("al") ?: item.optJSONObject("album")
            add(LxCrossPlatformHit(
                sourceId = LX_NETEASE_PLATFORM_ID,
                songMid = id,
                name = name,
                artist = artistNames.joinToString("、"),
                durationSec = item.optInt("dt", item.optInt("duration")) / 1000,
                albumName = album?.optString("name").orEmpty(),
                albumId = album?.optString("id").orEmpty(),
                coverUrl = album?.optString("picUrl")
            ))
        }
    }
}
