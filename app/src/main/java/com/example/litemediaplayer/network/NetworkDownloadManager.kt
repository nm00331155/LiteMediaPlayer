package com.example.litemediaplayer.network

import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NetworkDownloadManager {
    data class DownloadTask(
        val id: String = UUID.randomUUID().toString(),
        val sourceUri: String,
        val server: NetworkServer,
        val destinationUri: Uri,
        val fileName: String,
        val totalBytes: Long,
        val downloadedBytes: Long = 0L,
        val status: DownloadStatus = DownloadStatus.PENDING
    )

    enum class DownloadStatus {
        PENDING,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELED
    }

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    fun enqueueDownload(
        sourceUri: String,
        server: NetworkServer,
        destinationUri: Uri,
        fileName: String,
        totalBytes: Long
    ): String {
        val task = DownloadTask(
            sourceUri = sourceUri,
            server = server,
            destinationUri = destinationUri,
            fileName = fileName,
            totalBytes = totalBytes
        )
        _downloads.update { tasks -> listOf(task) + tasks }
        return task.id
    }

    fun updateProgress(taskId: String, downloadedBytes: Long, status: DownloadStatus = DownloadStatus.DOWNLOADING) {
        _downloads.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId) {
                    task.copy(
                        downloadedBytes = downloadedBytes.coerceAtLeast(0L),
                        status = status
                    )
                } else {
                    task
                }
            }
        }
    }

    fun pause(taskId: String) {
        updateStatus(taskId, DownloadStatus.PAUSED)
    }

    fun resume(taskId: String) {
        updateStatus(taskId, DownloadStatus.DOWNLOADING)
    }

    fun complete(taskId: String) {
        _downloads.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId) {
                    task.copy(downloadedBytes = task.totalBytes, status = DownloadStatus.COMPLETED)
                } else {
                    task
                }
            }
        }
    }

    fun fail(taskId: String) {
        updateStatus(taskId, DownloadStatus.FAILED)
    }

    fun cancel(taskId: String) {
        updateStatus(taskId, DownloadStatus.CANCELED)
    }

    private fun updateStatus(taskId: String, status: DownloadStatus) {
        _downloads.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId) {
                    task.copy(status = status)
                } else {
                    task
                }
            }
        }
    }

    companion object {
        val shared: NetworkDownloadManager by lazy { NetworkDownloadManager() }
    }
}
