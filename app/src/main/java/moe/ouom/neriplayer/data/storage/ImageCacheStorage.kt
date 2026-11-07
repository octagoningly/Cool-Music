package moe.ouom.neriplayer.data.storage

import android.content.Context
import java.io.File

/**
 * Coil 图片磁盘缓存目录的统一入口。
 *
 * 历史上放在 cacheDir/image_cache，系统在存储紧张或用户执行「清除缓存」时会整体删除，
 * 导致第二次冷启动封面全部重新联网下载。改为 filesDir 后与首页/探索页的 JSON 快照、
 * Room 数据库一样持久化，只有用户在 App 内显式清除图片缓存才会失效。
 */
object ImageCacheStorage {
    const val DIR_NAME = "image_cache"

    /** 当前生效的图片缓存目录（持久化，位于 filesDir）。 */
    fun directory(context: Context): File {
        return File(context.applicationContext.filesDir, DIR_NAME)
    }

    /**
     * 把旧版 cacheDir/image_cache 的内容迁移到 filesDir，避免升级后已有用户的封面全部重下。
     * 仅在新目录尚不存在时执行一次；逐文件 copy 而非 move，失败不影响新目录继续使用。
     */
    fun migrateLegacyCache(context: Context) {
        val appContext = context.applicationContext
        val target = directory(appContext)
        if (target.exists()) return
        val legacy = File(appContext.cacheDir, DIR_NAME)
        if (!legacy.isDirectory) return
        val files = legacy.listFiles() ?: return
        runCatching {
            if (!target.mkdirs() && !target.isDirectory) return
            files.forEach { file ->
                if (!file.isFile) return@forEach
                runCatching { file.copyTo(File(target, file.name), overwrite = true) }
            }
            // 迁移完成后删除旧目录，避免存储占用重复计算。
            runCatching { legacy.deleteRecursively() }
        }
    }

    /** 清空图片缓存（App 内「清除缓存」调用）。 */
    fun clear(context: Context): Boolean {
        val dir = directory(context)
        if (!dir.exists() || !dir.isDirectory) return true
        val deleted = runCatching { dir.deleteRecursively() }.getOrDefault(false)
        if (deleted) {
            runCatching { dir.mkdirs() }
        }
        // 同时清掉可能残留的旧版目录，避免统计里出现重复占用。
        runCatching { File(context.applicationContext.cacheDir, DIR_NAME).deleteRecursively() }
        return deleted
    }
}
