package com.clearlens.app.data

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest

data class ImageMetrics(
    val perceptualHash: Long,
    val sharpness: Double,
    val brightness: Double,
    val variance: Double
)

object ImageAnalyzer {
    /**
     * A 64-bit difference hash. It is intentionally small and fast enough to
     * run locally over a large gallery thumbnail collection.
     */
    fun metrics(source: Bitmap): ImageMetrics {
        val sample = Bitmap.createScaledBitmap(source, 64, 64, true)
        val gray = DoubleArray(64 * 64)
        var sum = 0.0
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val value = luminance(sample.getPixel(x, y))
                gray[y * 64 + x] = value
                sum += value
            }
        }
        if (sample !== source) sample.recycle()

        val mean = sum / gray.size
        var varianceSum = 0.0
        var laplacianSum = 0.0
        var laplacianSquaredSum = 0.0
        var laplacianCount = 0
        for (y in 1 until 63) {
            for (x in 1 until 63) {
                val center = gray[y * 64 + x]
                val laplacian =
                    gray[(y - 1) * 64 + x] + gray[(y + 1) * 64 + x] +
                        gray[y * 64 + x - 1] + gray[y * 64 + x + 1] - 4.0 * center
                laplacianSum += laplacian
                laplacianSquaredSum += laplacian * laplacian
                laplacianCount++
            }
        }
        for (value in gray) varianceSum += (value - mean) * (value - mean)
        val laplacianMean = laplacianSum / laplacianCount.coerceAtLeast(1)
        val sharpness = laplacianSquaredSum / laplacianCount.coerceAtLeast(1) -
            laplacianMean * laplacianMean

        val tiny = Bitmap.createScaledBitmap(source, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luminance(tiny.getPixel(x, y)) > luminance(tiny.getPixel(x + 1, y))) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        if (tiny !== source) tiny.recycle()

        return ImageMetrics(
            perceptualHash = hash,
            sharpness = sharpness,
            brightness = mean,
            variance = varianceSum / gray.size
        )
    }

    fun hammingDistance(first: Long, second: Long): Int =
        java.lang.Long.bitCount(first xor second)

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun luminance(color: Int): Double {
        val red = Color.red(color) / 255.0
        val green = Color.green(color) / 255.0
        val blue = Color.blue(color) / 255.0
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }
}
