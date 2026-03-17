package com.example.litemediaplayer.comic

import com.example.litemediaplayer.data.ComicBook
import com.example.litemediaplayer.data.ComicBookDao
import com.example.litemediaplayer.data.ComicProgressEntry
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal const val COMIC_PROGRESS_PAYLOAD_TYPE = "comic_progress_bundle"
internal const val COMIC_PROGRESS_PAYLOAD_VERSION = 1

private val COMIC_PROGRESS_TITLE_SANITIZER = Regex("[^\\p{L}\\p{N}]")

internal data class ComicProgressTransferItem(
    val title: String,
    val sourceType: String,
    val totalPages: Int,
    val lastReadPage: Int,
    val readStatus: String
)

internal data class ComicProgressImportResult(
    val updatedCount: Int,
    val skippedCount: Int
)

internal data class MergedComicProgress(
    val lastReadPage: Int,
    val totalPages: Int,
    val readStatus: String
)

internal suspend fun buildComicProgressPayload(
    comicBookDao: ComicBookDao,
    sourceDeviceId: String? = null,
    sourceDeviceName: String? = null
): JSONObject? {
    val items = comicBookDao.findAll()
        .mapNotNull { book -> book.toProgressTransferItemOrNull() }
    if (items.isEmpty()) {
        return null
    }

    return buildComicProgressPayload(
        items = items,
        sourceDeviceId = sourceDeviceId,
        sourceDeviceName = sourceDeviceName
    )
}

internal fun buildComicProgressPayload(
    items: List<ComicProgressTransferItem>,
    sourceDeviceId: String? = null,
    sourceDeviceName: String? = null
): JSONObject {
    return JSONObject().apply {
        put("type", COMIC_PROGRESS_PAYLOAD_TYPE)
        put("version", COMIC_PROGRESS_PAYLOAD_VERSION)
        put("exportedAt", System.currentTimeMillis())
        if (!sourceDeviceId.isNullOrBlank()) {
            put("sourceDeviceId", sourceDeviceId)
        }
        if (!sourceDeviceName.isNullOrBlank()) {
            put("sourceDeviceName", sourceDeviceName)
        }
        put(
            "items",
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("title", item.title)
                            put("sourceType", item.sourceType)
                            put("totalPages", item.totalPages)
                            put("lastReadPage", item.lastReadPage)
                            put("readStatus", item.readStatus)
                        }
                    )
                }
            }
        )
    }
}

internal fun parseComicProgressPayload(jsonText: String): List<ComicProgressTransferItem> {
    return parseComicProgressPayload(JSONObject(jsonText))
}

internal fun parseComicProgressPayload(payload: JSONObject): List<ComicProgressTransferItem> {
    if (payload.optString("type") != COMIC_PROGRESS_PAYLOAD_TYPE) {
        error("対応していない進捗ファイルです")
    }

    val items = payload.optJSONArray("items")
        ?.toProgressTransferItems()
        .orEmpty()
    if (items.isEmpty()) {
        error("取り込める進捗がありません")
    }

    return items
}

internal suspend fun importComicProgressPayload(
    comicBookDao: ComicBookDao,
    payloadText: String
): ComicProgressImportResult {
    return importComicProgressItems(
        comicBookDao = comicBookDao,
        items = parseComicProgressPayload(payloadText)
    )
}

internal suspend fun importComicProgressItems(
    comicBookDao: ComicBookDao,
    items: List<ComicProgressTransferItem>
): ComicProgressImportResult {
    val remainingBooks = comicBookDao.findAll().toMutableList()
    var updatedCount = 0
    var skippedCount = 0

    items.forEach { item ->
        val target = findBestProgressTarget(remainingBooks, item)
        if (target == null) {
            skippedCount += 1
            return@forEach
        }

        remainingBooks.remove(target)

        val merged = mergeProgress(target, item)
        if (merged == null) {
            skippedCount += 1
            return@forEach
        }

        comicBookDao.updateProgress(
            bookId = target.id,
            lastReadPage = merged.lastReadPage,
            totalPages = merged.totalPages,
            readStatus = merged.readStatus
        )
        updatedCount += 1
    }

    return ComicProgressImportResult(
        updatedCount = updatedCount,
        skippedCount = skippedCount
    )
}

internal fun ComicProgressTransferItem.hasProgress(): Boolean {
    return lastReadPage > 0 || readStatus != "UNREAD"
}

internal fun ComicBook.toProgressTransferItemOrNull(): ComicProgressTransferItem? {
    if (!hasProgress(lastReadPage, readStatus)) {
        return null
    }

    return ComicProgressTransferItem(
        title = title,
        sourceType = sourceType,
        totalPages = totalPages.coerceAtLeast(0),
        lastReadPage = lastReadPage.coerceAtLeast(0),
        readStatus = readStatus.ifBlank { "UNREAD" }
    )
}

internal fun ComicProgressEntry.toProgressTransferItemOrNull(): ComicProgressTransferItem? {
    if (!hasProgress(lastReadPage, readStatus)) {
        return null
    }

    return ComicProgressTransferItem(
        title = title,
        sourceType = sourceType,
        totalPages = totalPages.coerceAtLeast(0),
        lastReadPage = lastReadPage.coerceAtLeast(0),
        readStatus = readStatus.ifBlank { "UNREAD" }
    )
}

private fun JSONArray.toProgressTransferItems(): List<ComicProgressTransferItem> {
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            if (title.isEmpty()) {
                continue
            }

            add(
                ComicProgressTransferItem(
                    title = title,
                    sourceType = item.optString("sourceType").ifBlank { "ARCHIVE" },
                    totalPages = item.optInt("totalPages").coerceAtLeast(0),
                    lastReadPage = item.optInt("lastReadPage").coerceAtLeast(0),
                    readStatus = item.optString("readStatus").ifBlank { "UNREAD" }
                )
            )
        }
    }
}

internal fun findBestProgressTarget(
    localBooks: List<ComicBook>,
    incoming: ComicProgressTransferItem
): ComicBook? {
    val normalizedIncomingTitle = normalizeComicTitle(incoming.title)

    return localBooks.firstOrNull { book ->
        normalizeComicTitle(book.title) == normalizedIncomingTitle &&
            book.sourceType == incoming.sourceType &&
            pageCountsMatch(book.totalPages, incoming.totalPages)
    } ?: localBooks.firstOrNull { book ->
        normalizeComicTitle(book.title) == normalizedIncomingTitle &&
            book.sourceType == incoming.sourceType
    } ?: localBooks.firstOrNull { book ->
        normalizeComicTitle(book.title) == normalizedIncomingTitle
    }
}

internal fun mergeProgress(
    local: ComicBook,
    incoming: ComicProgressTransferItem
): MergedComicProgress? {
    return mergeProgress(
        localLastReadPage = local.lastReadPage,
        localTotalPages = local.totalPages,
        localReadStatus = local.readStatus,
        incoming = incoming
    )
}

internal fun mergeProgress(
    local: ComicProgressEntry,
    incoming: ComicProgressTransferItem
): MergedComicProgress? {
    return mergeProgress(
        localLastReadPage = local.lastReadPage,
        localTotalPages = local.totalPages,
        localReadStatus = local.readStatus,
        incoming = incoming
    )
}

internal fun mergeProgress(
    localLastReadPage: Int,
    localTotalPages: Int,
    localReadStatus: String,
    incoming: ComicProgressTransferItem
): MergedComicProgress? {
    val resolvedTotalPages = maxOf(localTotalPages, incoming.totalPages)
    val localPage = normalizeProgressPage(localLastReadPage, localTotalPages, localReadStatus)
    var mergedPage = maxOf(
        localPage,
        normalizeProgressPage(incoming.lastReadPage, incoming.totalPages, incoming.readStatus)
    )

    val shouldMarkRead = localReadStatus == "READ" || incoming.readStatus == "READ"
    if (shouldMarkRead && resolvedTotalPages > 0) {
        mergedPage = maxOf(mergedPage, resolvedTotalPages - 1)
    }

    val safePage = if (resolvedTotalPages > 0) {
        mergedPage.coerceIn(0, (resolvedTotalPages - 1).coerceAtLeast(0))
    } else {
        mergedPage.coerceAtLeast(0)
    }

    val mergedStatus = when {
        shouldMarkRead -> "READ"
        safePage > 0 -> "IN_PROGRESS"
        localReadStatus == "IN_PROGRESS" || incoming.readStatus == "IN_PROGRESS" -> {
            "IN_PROGRESS"
        }

        else -> "UNREAD"
    }

    if (
        safePage == localLastReadPage &&
        resolvedTotalPages == localTotalPages &&
        mergedStatus == localReadStatus
    ) {
        return null
    }

    return MergedComicProgress(
        lastReadPage = safePage,
        totalPages = resolvedTotalPages,
        readStatus = mergedStatus
    )
}

internal fun normalizeProgressPage(currentPage: Int, totalPages: Int, readStatus: String): Int {
    if (readStatus == "READ" && totalPages > 0) {
        return (totalPages - 1).coerceAtLeast(0)
    }

    return if (totalPages > 0) {
        currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    } else {
        currentPage.coerceAtLeast(0)
    }
}

internal fun normalizeComicTitle(title: String): String {
    val trimmed = when {
        title.endsWith(".zip", ignoreCase = true) -> title.dropLast(4)
        title.endsWith(".cbz", ignoreCase = true) -> title.dropLast(4)
        title.endsWith(".cbr", ignoreCase = true) -> title.dropLast(4)
        title.endsWith(".rar", ignoreCase = true) -> title.dropLast(4)
        title.endsWith(".jpg", ignoreCase = true) -> title.dropLast(4)
        title.endsWith(".png", ignoreCase = true) -> title.dropLast(4)
        title.endsWith(".webp", ignoreCase = true) -> title.dropLast(5)
        title.endsWith(".jpeg", ignoreCase = true) -> title.dropLast(5)
        else -> title
    }

    return trimmed
        .lowercase(Locale.ROOT)
        .replace(COMIC_PROGRESS_TITLE_SANITIZER, "")
}

internal fun pageCountsMatch(localPages: Int, incomingPages: Int): Boolean {
    return localPages <= 0 || incomingPages <= 0 || localPages == incomingPages
}

internal fun hasProgress(lastReadPage: Int, readStatus: String): Boolean {
    return lastReadPage > 0 || readStatus != "UNREAD"
}