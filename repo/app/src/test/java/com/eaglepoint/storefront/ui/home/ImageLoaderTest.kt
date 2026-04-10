package com.eaglepoint.storefront.ui.home

import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ImageLoaderTest {

    @Test
    fun `calculateInSampleSize returns 1 for image smaller than target`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 100
            outHeight = 100
        }

        val result = ImageLoader.calculateInSampleSize(options, 200, 200)

        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `calculateInSampleSize returns 2 for image double the target`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 400
            outHeight = 400
        }

        val result = ImageLoader.calculateInSampleSize(options, 200, 200)

        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `calculateInSampleSize returns 4 for image 4x the target`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 800
            outHeight = 800
        }

        val result = ImageLoader.calculateInSampleSize(options, 200, 200)

        assertThat(result).isEqualTo(4)
    }

    @Test
    fun `calculateInSampleSize returns power of 2`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 3000
            outHeight = 2000
        }

        val result = ImageLoader.calculateInSampleSize(options, 160, 120)

        // Should be power of 2
        assertThat(result).isAnyOf(1, 2, 4, 8, 16)
        assertThat(result).isAtLeast(4)
    }

    @Test
    fun `MAX_CACHE_BYTES is 20MB`() {
        assertThat(ImageLoader.MAX_CACHE_BYTES).isEqualTo(20 * 1024 * 1024)
    }
}
