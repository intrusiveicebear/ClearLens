package com.clearlens.app.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Size
import com.clearlens.app.model.FindingGroup
import com.clearlens.app.model.FindingType
import com.clearlens.app.model.PhotoItem
import com.clearlens.app.model.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class PhotoRepository(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun scan(
        excludedFolders: Set<String>,
        onProgress: (ScanProgress) -> Unit
    ): List<FindingGroup> = withContext(Dispatchers.IO) {
        val basicPhotos = queryPhotos(excludedFolders)
        onProgress(ScanProgress(0, basicPhotos.size, "Analyzing photos on this device…"))

        val analyzed = basicPhotos.mapIndexedNotNull { index, photo ->
            coroutineContext.ensureActive()
            val result = analyzePhoto(photo)
            if (index % 5 == 0 || index == basicPhotos.lastIndex) {
                onProgress(ScanProgress(index + 1, basicPhotos.size, "Checking photo ${index + 1} of ${basicPhotos.size}"))
            }
            result
        }

        onProgress(ScanProgress(analyzed.size, analyzed.size, "Finding duplicate files…"))
        val exactGroups = findExactDuplicates(analyzed)
        val exactIds = exactGroups.flatMapTo(mutableSetOf()) { group -> group.photos.map { it.id } }

        onProgress(ScanProgress(analyzed.size, analyzed.size, "Grouping similar photos…"))
        val similarGroups = findSimilar(analyzed.filterNot { it.id in exactIds })
        val groupedIds = similarGroups.flatMapTo(exactIds) { group -> group.photos.map { it.id } }

        val singleFindings = analyzed
            .filterNot { it.id in groupedIds }
            .mapNotNull(::classifyLowQuality)

        exactGroups + similarGroups + singleFindings
    }

    private fun queryPhotos(excludedFolders: Set<String>): List<PhotoItem> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.IS_FAVORITE
        )
        val result = mutableListOf<PhotoItem>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.IS_PENDING} = 0",
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val favoriteColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn).orEmpty()
                if (excludedFolders.any { path.startsWith(it, ignoreCase = true) }) continue
                val id = cursor.getLong(idColumn)
                result += PhotoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameColumn).orEmpty(),
                    relativePath = path,
                    dateTaken = cursor.getLong(dateColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    mimeType = cursor.getString(mimeColumn).orEmpty(),
                    isFavorite = cursor.getInt(favoriteColumn) == 1
                )
            }
        }
        return result
    }

    private fun analyzePhoto(photo: PhotoItem): PhotoItem? = try {
        val thumbnail = resolver.loadThumbnail(photo.uri, Size(256, 256), null)
        val metrics = ImageAnalyzer.metrics(thumbnail)
        thumbnail.recycle()
        photo.copy(
            perceptualHash = metrics.perceptualHash,
            sharpness = metrics.sharpness,
            brightness = metrics.brightness,
            variance = metrics.variance
        )
    } catch (_: Exception) {
        null
    }

    private fun findExactDuplicates(photos: List<PhotoItem>): List<FindingGroup> {
        val possibleDuplicates = photos.groupBy { it.sizeBytes }.values.filter { it.size > 1 }
        val groups = mutableListOf<FindingGroup>()
        for (sameSize in possibleDuplicates) {
            val hashed = sameSize.mapNotNull { photo ->
                val hash = hashContent(photo) ?: return@mapNotNull null
                photo.copy(exactHash = hash)
            }
            for (sameHash in hashed.groupBy { it.exactHash }.values.filter { it.size > 1 }) {
                val keeper = sameHash.maxBy { it.qualityScore }
                groups += FindingGroup(
                    id = "exact-${keeper.exactHash}",
                    type = FindingType.EXACT_DUPLICATE,
                    title = "${sameHash.size} exact copies",
                    explanation = "These files contain exactly the same photo. ClearLens kept one selected as the best copy.",
                    photos = sameHash.sortedByDescending { it.qualityScore },
                    recommendedDeleteIds = sameHash.mapNotNullTo(mutableSetOf()) { if (it.id == keeper.id) null else it.id }
                )
            }
        }
        return groups
    }

    private fun hashContent(photo: PhotoItem): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(photo.uri)?.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    private fun findSimilar(photos: List<PhotoItem>): List<FindingGroup> {
        if (photos.size < 2) return emptyList()
        val parent = IntArray(photos.size) { it }
        fun root(value: Int): Int {
            var current = value
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        fun union(a: Int, b: Int) {
            val rootA = root(a)
            val rootB = root(b)
            if (rootA != rootB) parent[rootB] = rootA
        }

        // Four-band locality-sensitive buckets avoid an O(n²) gallery scan.
        val buckets = HashMap<Long, MutableList<Int>>()
        for (index in photos.indices) {
            val hash = photos[index].perceptualHash
            val candidates = mutableSetOf<Int>()
            for (band in 0 until 4) {
                val value = (hash ushr (band * 16)) and 0xffffL
                val key = (band.toLong() shl 16) or value
                buckets[key]?.let(candidates::addAll)
            }
            for (candidate in candidates) {
                val other = photos[candidate]
                val aspectA = photos[index].width.toDouble() / photos[index].height.coerceAtLeast(1)
                val aspectB = other.width.toDouble() / other.height.coerceAtLeast(1)
                if (kotlin.math.abs(aspectA - aspectB) < 0.08 &&
                    ImageAnalyzer.hammingDistance(hash, other.perceptualHash) <= 7
                ) {
                    union(index, candidate)
                }
            }
            for (band in 0 until 4) {
                val value = (hash ushr (band * 16)) and 0xffffL
                val key = (band.toLong() shl 16) or value
                buckets.getOrPut(key) { mutableListOf() }.add(index)
            }
        }

        return photos.indices
            .groupBy(::root)
            .values
            .filter { it.size > 1 }
            .map { indexes ->
                val group = indexes.map(photos::get).sortedByDescending { it.qualityScore }
                FindingGroup(
                    id = "similar-${group.first().id}",
                    type = FindingType.SIMILAR,
                    title = "${group.size} similar photos",
                    explanation = "These look alike. The sharpest, best-exposed photo is marked Keep.",
                    photos = group,
                    recommendedDeleteIds = group.drop(1).mapTo(mutableSetOf()) { it.id }
                )
            }
    }

    private fun classifyLowQuality(photo: PhotoItem): FindingGroup? {
        val type: FindingType
        val title: String
        val explanation: String
        when {
            photo.variance < 0.0012 -> {
                type = FindingType.BLANK
                title = "Possible blank or pocket photo"
                explanation = "This image has almost no visual detail. Review it before removing it."
            }
            photo.brightness < 0.055 -> {
                type = FindingType.DARK
                title = "Extremely dark photo"
                explanation = "Almost nothing is visible. ClearLens never decides whether a memory is meaningful."
            }
            photo.sharpness < 0.00042 && photo.variance > 0.003 -> {
                type = FindingType.BLURRY
                title = "Possibly blurry photo"
                explanation = "This photo has unusually soft detail. Zoom in before deciding."
            }
            else -> return null
        }
        return FindingGroup(
            id = "quality-${photo.id}",
            type = type,
            title = title,
            explanation = explanation,
            photos = listOf(photo),
            // Quality judgements are less certain, so they start unselected.
            recommendedDeleteIds = emptySet()
        )
    }
}
