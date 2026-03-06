package com.example.litemediaplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val thumbnailCache = LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 20).toInt()
    )

    suspend fun loadThumbnail(
        uri: Uri,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        thumbnailCache.get(key)?.let { cached ->
            return@withContext cached
        }

        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(width, height), null)
            }.getOrNull()
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.let { frame ->
                    Bitmap.createScaledBitmap(frame, width, height, true)
                }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

        bitmap?.also { generated -> thumbnailCache.put(key, generated) }
    }
}
