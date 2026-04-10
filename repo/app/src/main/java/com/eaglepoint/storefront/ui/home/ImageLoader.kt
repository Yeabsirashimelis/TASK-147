package com.eaglepoint.storefront.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ImageLoader(
    maxMemoryBytes: Int = MAX_CACHE_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    private val cache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private val activeJobs = mutableMapOf<ImageView, Job>()

    fun loadInto(url: String?, imageView: ImageView, targetWidth: Int, targetHeight: Int) {
        // Cancel any previous load for this ImageView
        activeJobs[imageView]?.cancel()
        imageView.setImageBitmap(null)

        if (url.isNullOrBlank()) return

        // Check cache first
        val cacheKey = "$url:${targetWidth}x$targetHeight"
        cache.get(cacheKey)?.let { bitmap ->
            imageView.setImageBitmap(bitmap)
            return
        }

        // Load off main thread
        val job = CoroutineScope(mainDispatcher).launch {
            val bitmap = withContext(ioDispatcher) {
                decodeSampledBitmap(url, targetWidth, targetHeight)
            }
            if (bitmap != null) {
                cache.put(cacheKey, bitmap)
                imageView.setImageBitmap(bitmap)
            }
        }
        activeJobs[imageView] = job
    }

    fun cancel(imageView: ImageView) {
        activeJobs.remove(imageView)?.cancel()
    }

    fun clearCache() {
        cache.evictAll()
    }

    fun cacheSize(): Int = cache.size()

    private fun decodeSampledBitmap(urlString: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doInput = true

            try {
                val bytes = connection.inputStream.use { it.readBytes() }

                // First pass: decode bounds only
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                // Second pass: decode with downsampling
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val MAX_CACHE_BYTES = 20 * 1024 * 1024 // 20MB

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val (height, width) = options.outHeight to options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }
    }
}
