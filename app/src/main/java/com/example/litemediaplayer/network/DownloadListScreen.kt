package com.example.litemediaplayer.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DownloadListScreen(
    downloadManager: NetworkDownloadManager = NetworkDownloadManager.shared
) {
    val downloads by downloadManager.downloads.collectAsStateWithLifecycle()
    val uploads by downloadManager.uploads.collectAsStateWithLifecycle()

    if (downloads.isEmpty() && uploads.isEmpty()) {
        Text(
            text = "転送タスクはありません",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
        return
    }

    val hasFinished = downloads.any { task ->
        task.status == NetworkDownloadManager.DownloadStatus.COMPLETED ||
            task.status == NetworkDownloadManager.DownloadStatus.FAILED
    } || uploads.any { task ->
        task.status == NetworkDownloadManager.DownloadStatus.COMPLETED ||
            task.status == NetworkDownloadManager.DownloadStatus.FAILED
    }

    if (hasFinished) {
        Button(
            onClick = {
                downloadManager.removeCompleted()
                downloadManager.removeCompletedUploads()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(text = "完了/失敗を削除")
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (downloads.isNotEmpty()) {
            item {
                Text(
                    text = "ダウンロード",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            items(downloads, key = { task -> task.id }) { task ->
                val progress = if (task.totalBytes > 0L) {
                    task.downloadedBytes.toFloat() / task.totalBytes.toFloat()
                } else {
                    0f
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = task.fileName, style = MaterialTheme.typography.titleSmall)
                        val ratioText = if (task.totalBytes > 0L) {
                            val percent = (task.downloadedBytes * 100 / task.totalBytes)
                                .coerceIn(0, 100)
                            " ($percent%)"
                        } else {
                            ""
                        }
                        Text(
                            text = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}$ratioText",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(text = statusLabel(task.status), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when (task.status) {
                                NetworkDownloadManager.DownloadStatus.DOWNLOADING -> {
                                    Button(onClick = { downloadManager.pause(task.id) }) {
                                        Text(text = "一時停止")
                                    }
                                }

                                NetworkDownloadManager.DownloadStatus.PAUSED,
                                NetworkDownloadManager.DownloadStatus.PENDING -> {
                                    Button(onClick = { downloadManager.resume(task.id) }) {
                                        Text(text = "再開")
                                    }
                                }

                                else -> Unit
                            }

                            Button(onClick = { downloadManager.cancel(task.id) }) {
                                Text(text = "キャンセル")
                            }
                        }
                    }
                }
            }
        }

        if (uploads.isNotEmpty()) {
            item {
                Text(
                    text = "アップロード",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            items(uploads, key = { task -> task.id }) { task ->
                val progress = if (task.totalBytes > 0L) {
                    task.uploadedBytes.toFloat() / task.totalBytes.toFloat()
                } else {
                    0f
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = task.fileName, style = MaterialTheme.typography.titleSmall)
                        val ratioText = if (task.totalBytes > 0L) {
                            val percent = (task.uploadedBytes * 100 / task.totalBytes)
                                .coerceIn(0, 100)
                            " ($percent%)"
                        } else {
                            ""
                        }
                        Text(
                            text = "${formatBytes(task.uploadedBytes)} / ${formatBytes(task.totalBytes)}$ratioText",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(text = statusLabel(task.status), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(onClick = { downloadManager.cancelUpload(task.id) }) {
                                Text(text = "キャンセル")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(value: Long): String {
    if (value <= 0L) {
        return "0 B"
    }

    val units = arrayOf("B", "KB", "MB", "GB")
    var size = value.toDouble()
    var index = 0
    while (size >= 1024.0 && index < units.lastIndex) {
        size /= 1024.0
        index += 1
    }

    val text = if (size >= 100.0 || index == 0) {
        "%.0f".format(size)
    } else {
        "%.1f".format(size)
    }
    return "$text ${units[index]}"
}

private fun statusLabel(status: NetworkDownloadManager.DownloadStatus): String {
    return when (status) {
        NetworkDownloadManager.DownloadStatus.PENDING -> "待機中"
        NetworkDownloadManager.DownloadStatus.DOWNLOADING -> "ダウンロード中"
        NetworkDownloadManager.DownloadStatus.PAUSED -> "一時停止中"
        NetworkDownloadManager.DownloadStatus.COMPLETED -> "完了"
        NetworkDownloadManager.DownloadStatus.FAILED -> "失敗"
        NetworkDownloadManager.DownloadStatus.CANCELED -> "キャンセル済み"
    }
}
