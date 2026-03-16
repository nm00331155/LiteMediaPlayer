package com.example.litemediaplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val thumbnailCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 20).toInt().coerceAtLeast(1)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    suspend fun loadThumbnail(
        uri: Uri,
        positionMs: Long = 1_000L,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val bucketedPositionMs = (safePositionMs / 500L) * 500L
        val key = buildString {
            append(uri)
            append('#')
            append(bucketedPositionMs)
            append('#')
            append(width)
            append('x')
            append(height)
        }
        thumbnailCache.get(key)?.let { cached ->
            return@withContext cached
        }

        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                bucketedPositionMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(
                bucketedPositionMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            )

            frame?.let { source ->
                if (source.width == width && source.height == height) {
                    source
                } else {
                    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
                    if (scaled != source) {
                        source.recycle()
                    }
                    scaled
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }

        bitmap?.also { generated -> thumbnailCache.put(key, generated) }
    }
}
