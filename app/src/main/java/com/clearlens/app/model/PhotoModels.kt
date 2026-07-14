package com.clearlens.app.model

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val dateTaken: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val isFavorite: Boolean = false,
    val perceptualHash: Long = 0L,
    val sharpness: Double = 0.0,
    val brightness: Double = 0.5,
    val variance: Double = 1.0,
    val exactHash: String? = null
) {
    val qualityScore: Double
        get() {
            val resolution = (width.toLong() * height.toLong()).coerceAtLeast(1L)
            val resolutionScore = kotlin.math.ln(resolution.toDouble())
            val exposureScore = 1.0 - kotlin.math.abs(brightness - 0.5)
            return sharpness * 2.0 + resolutionScore + exposureScore * 4.0 + if (isFavorite) 10_000.0 else 0.0
        }
}

enum class FindingType(val label: String) {
    EXACT_DUPLICATE("Duplicates"),
    SIMILAR("Similar"),
    BLURRY("Blurry"),
    DARK("Very dark"),
    BLANK("Blank / accidental")
}

data class FindingGroup(
    val id: String,
    val type: FindingType,
    val title: String,
    val explanation: String,
    val photos: List<PhotoItem>,
    val recommendedDeleteIds: Set<Long>
) {
    val recoverableBytes: Long
        get() = photos.filter { it.id in recommendedDeleteIds }.sumOf { it.sizeBytes }
}

data class ScanProgress(
    val current: Int = 0,
    val total: Int = 0,
    val message: String = "Preparing scan…"
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
}
