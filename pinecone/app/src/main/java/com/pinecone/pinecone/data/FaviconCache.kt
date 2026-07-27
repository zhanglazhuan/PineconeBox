package com.pinecone.pinecone.data

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import java.io.File

/**
 * Configures Coil with persistent disk cache at .pineconebox/cache/logos/.
 *
 * Cache strategy:
 * - On card display: load from cache (fast), fall back to Google favicon service
 * - On card click:  invalidate cache + re-fetch in background (keep icon fresh)
 * - On cache miss:  fetch from Google favicon service
 */
object FaviconCache {

    private const val CACHE_DIR = ".pineconebox/cache/logos"
    private const val MAX_DISK_SIZE = 20L * 1024 * 1024

    private var imageLoader: ImageLoader? = null
    private var appContext: Context? = null

    fun init(context: Context): ImageLoader {
        imageLoader?.let { return it }
        appContext = context.applicationContext

        val cacheDir = resolveCacheDir(context)

        val loader = ImageLoader.Builder(context)
            .memoryCache(MemoryCache.Builder(context).maxSizePercent(0.05).build())
            .diskCache(DiskCache.Builder().directory(cacheDir).maxSizeBytes(MAX_DISK_SIZE).build())
            .crossfade(true)
            .build()

        imageLoader = loader
        return loader
    }

    fun get(): ImageLoader {
        return imageLoader ?: error("FaviconCache not initialized. Call init(context) first.")
    }

    /**
     * Refresh a site's favicon: purge cached version and re-fetch in background.
     * Called when user clicks a web card — keeps logos fresh over time.
     */
    fun refresh(websiteUrl: String) {
        val faviconUrl = ResourceData.logoUrl(websiteUrl)
        if (faviconUrl.isEmpty()) return

        val loader = imageLoader ?: return
        val ctx = appContext ?: return

        // 1. Purge from memory
        loader.memoryCache?.remove(MemoryCache.Key(faviconUrl))

        // 2. Purge from disk
        loader.diskCache?.remove(faviconUrl)

        // 3. Re-fetch in background (low priority, won't block UI)
        val request = ImageRequest.Builder(ctx)
            .data(faviconUrl)
            .memoryCacheKey(MemoryCache.Key(faviconUrl))
            .diskCacheKey(faviconUrl)
            .build()
        loader.enqueue(request)
    }

    private fun resolveCacheDir(context: Context): File {
        // App-specific external storage — no permission needed on API 31+
        return try {
            val dir = File(context.getExternalFilesDir(null), "logos")
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (_: Exception) {
            // Fallback to internal cache
            File(context.cacheDir, "logos").also { if (!it.exists()) it.mkdirs() }
        }
    }
}
