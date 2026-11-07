package moe.ouom.neriplayer.core.startup.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.storage.ImageCacheStorage

internal object AppImageLoaderInitializer {
    fun initialize(app: Application) {
        // 旧版缓存位于 cacheDir（可被系统/清除缓存删除），升级时搬到 filesDir 保证持久化。
        ImageCacheStorage.migrateLegacyCache(app)
        val imageLoader = ImageLoader.Builder(app)
            .okHttpClient { AppContainer.sharedOkHttpClient }
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(ImageCacheStorage.directory(app))
                    .maxSizePercent(0.02)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(app)
                    .maxSizePercent(0.12)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
