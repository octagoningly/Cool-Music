package moe.ouom.neriplayer.data.source.lxmusic.js

import android.content.Context
import moe.ouom.neriplayer.core.logging.NPLogger
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

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
 * File: moe.ouom.neriplayer.data.source.lxmusic.js/LxJsSourceEngine
 * Updated: 2026/3/23
 */

/** 进程内 JS 音源运行时缓存（按导入源 id） */
object LxJsSourceEngine {
    private const val TAG = "NERI-LxMusicSource"
    private val runtimes = ConcurrentHashMap<String, LxJsSourceRuntime>()

    fun ensureLoaded(
        context: Context,
        okHttpClient: OkHttpClient,
        sourceId: String,
        sourceName: String,
        script: String,
        description: String = "",
        version: String = "",
        author: String = "",
        homepage: String = ""
    ): Boolean {
        val existing = runtimes[sourceId]
        if (existing != null && existing.isReady()) return true
        val runtime = existing ?: LxJsSourceRuntime(
            context = context.applicationContext,
            okHttpClient = okHttpClient,
            scriptId = sourceId,
            scriptName = sourceName,
            script = script,
            scriptDescription = description,
            scriptVersion = version,
            scriptAuthor = author,
            scriptHomepage = homepage
        )
        val ok = runtime.load()
        if (ok) {
            runtimes[sourceId] = runtime
            NPLogger.i(
                TAG,
                "JS source loaded: id=$sourceId name=$sourceName sources=${runtime.supportedSources}"
            )
        } else {
            runtimes.remove(sourceId)
            NPLogger.e(TAG, "JS source load failed: id=$sourceId name=$sourceName")
        }
        return ok
    }

    fun runtime(sourceId: String): LxJsSourceRuntime? = runtimes[sourceId]

    fun peekSupportedSources(sourceId: String): Set<String> =
        runtimes[sourceId]?.supportedSources.orEmpty()

    fun destroy(sourceId: String) {
        runtimes.remove(sourceId)?.destroy()
    }

    fun destroyAll() {
        runtimes.keys.toList().forEach { destroy(it) }
    }
}
