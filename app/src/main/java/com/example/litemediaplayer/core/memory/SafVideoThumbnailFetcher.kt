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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
            } catch (_: Exception) {
                // 次の方法にフォールバック
            }
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(pfd.fileDescriptor)
                    return retriever.getFrameAtTime(
                        1_000_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                } finally {
                    runCatching { retriever.release() }
                }
            }
        } catch (_: Exception) {
            // 次の方法にフォールバック
        }

        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                return retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            } finally {
                runCatching { retriever.release() }
            }
        } catch (_: Exception) {
            // 全方法失敗
        }

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
