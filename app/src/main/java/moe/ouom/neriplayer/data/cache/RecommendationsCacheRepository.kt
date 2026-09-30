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
 * File: moe.ouom.neriplayer.data.cache/RecommendationsCacheRepository
 * Created: 2026/9/22
 */

import android.content.Context
import com.google.gson.Gson
import moe.ouom.neriplayer.core.logging.NPLogger
import java.io.File

/**
 * 首页 / 探索推荐的磁盘缓存（SWR：先展示后刷新）。
 * 三个独立文件，互不影响：
 * - home_feed.json
 * - explore_grid.json
 * - explore_yt_library.json
 */
class RecommendationsCacheRepository internal constructor(
    private val cacheDir: File
) {
    private val gson = Gson()

    constructor(context: Context) : this(
        cacheDir = File(context.applicationContext.filesDir, CACHE_DIR_NAME)
    )

    fun readHomeFeed(): HomeFeedCacheSnapshot? {
        return readSnapshot(HOME_FEED_FILE_NAME, HomeFeedCacheSnapshot::class.java)
    }

    fun saveHomeFeed(snapshot: HomeFeedCacheSnapshot) {
        writeSnapshot(HOME_FEED_FILE_NAME, snapshot)
    }

    fun readExploreGrid(): ExploreGridCacheSnapshot? {
        return readSnapshot(EXPLORE_GRID_FILE_NAME, ExploreGridCacheSnapshot::class.java)
    }

    fun saveExploreGrid(snapshot: ExploreGridCacheSnapshot) {
        writeSnapshot(EXPLORE_GRID_FILE_NAME, snapshot)
    }

    fun readExploreYtLibrary(): ExploreYtLibraryCacheSnapshot? {
        return readSnapshot(EXPLORE_YT_FILE_NAME, ExploreYtLibraryCacheSnapshot::class.java)
    }

    fun saveExploreYtLibrary(snapshot: ExploreYtLibraryCacheSnapshot) {
        writeSnapshot(EXPLORE_YT_FILE_NAME, snapshot)
    }

    private fun <T> readSnapshot(fileName: String, type: Class<T>): T? {
        val file = File(cacheDir, fileName)
        if (!file.exists()) return null
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, type)
            }
        }.onFailure { error ->
            NPLogger.w(TAG, "Failed to read recommendations cache: $fileName", error)
        }.getOrNull()
    }

    private fun writeSnapshot(fileName: String, snapshot: Any) {
        runCatching {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = File(cacheDir, fileName)
            val tmp = File(cacheDir, "$fileName.tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                gson.toJson(snapshot, writer)
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { error ->
            NPLogger.w(TAG, "Failed to write recommendations cache: $fileName", error)
        }
    }

    companion object {
        private const val TAG = "NERI-RecsCache"
        const val CACHE_DIR_NAME = "recommendations_cache"
        const val HOME_FEED_FILE_NAME = "home_feed.json"
        const val EXPLORE_GRID_FILE_NAME = "explore_grid.json"
        const val EXPLORE_YT_FILE_NAME = "explore_yt_library.json"
    }
}
