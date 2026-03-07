package com.example.litemediaplayer.core.memory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.example.litemediaplayer.core.AppLogger
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafVideoThumbnailFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val bitmap = tryLoadThumbnail()
            ?: throw IOException("Failed to load thumbnail for $uri")

        DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun tryLoadThumbnail(): Bitmap? {
        AppLogger.d("Thumbnail", "Resolve thumbnail: $uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                AppLogger.d("Thumbnail", "Loaded via ContentResolver.loadThumbnail")
                return bitmap
            } catch (error: Exception) {
                AppLogger.w("Thumbnail", "loadThumbnail fallback: $uri", error)
                // 次の方法にフォールバック
            }
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(pfd.fileDescriptor)
                    val frame = retriever.getFrameAtTime(
                        1_000_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frame != null) {
                        AppLogger.d("Thumbnail", "Loaded via file descriptor")
                    }
                    return frame
                } finally {
                    runCatching { retriever.release() }
                }
            }
        } catch (error: Exception) {
            AppLogger.w("Thumbnail", "File descriptor fallback: $uri", error)
            // 次の方法にフォールバック
        }

        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    AppLogger.d("Thumbnail", "Loaded via MediaMetadataRetriever")
                }
                return frame
            } finally {
                runCatching { retriever.release() }
            }
        } catch (error: Exception) {
            AppLogger.w("Thumbnail", "Retriever fallback failed: $uri", error)
            // 全方法失敗
        }

        AppLogger.w("Thumbnail", "Thumbnail load failed: $uri")
        return null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme == "content") {
                return SafVideoThumbnailFetcher(context, data)
            }
            return null
        }
    }
}
