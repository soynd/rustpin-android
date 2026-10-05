package com.rustpin.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Shared Coil loader: capped caches, hardware bitmaps, crossfade - avoids OOM on big feeds. */
class RustpinApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.22).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("coil")).maxSizeBytes(256L * 1024 * 1024).build() }
        .crossfade(true)
        .allowHardware(true)
        .build()
}
