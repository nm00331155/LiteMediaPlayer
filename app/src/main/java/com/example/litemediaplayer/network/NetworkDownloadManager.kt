package com.example.litemediaplayer.network

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val httpClient = OkHttpClient.Builder().build()

    private var appContext: Context? = null
    @Volatile
    private var maxConcurrent = 2

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setMaxConcurrent(max: Int) {
        maxConcurrent = max.coerceIn(1, 5)
        processQueue()
    }

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
        processQueue()
        return task.id
    }

    fun pause(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        updateTask(taskId) { it.copy(status = DownloadStatus.PAUSED) }
        processQueue()
    }

    fun resume(taskId: String) {
        updateTask(taskId) { it.copy(status = DownloadStatus.PENDING) }
        processQueue()
    }

    fun cancel(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        _downloads.update { tasks -> tasks.filterNot { it.id == taskId } }
        processQueue()
    }

    fun removeCompleted() {
        _downloads.update { tasks ->
            tasks.filterNot {
                it.status == DownloadStatus.COMPLETED ||
                    it.status == DownloadStatus.CANCELED ||
                    it.status == DownloadStatus.FAILED
            }
        }
    }

    private fun processQueue() {
        val currentDownloading = _downloads.value.count { it.status == DownloadStatus.DOWNLOADING }
        if (currentDownloading >= maxConcurrent) {
            return
        }

        val slots = maxConcurrent - currentDownloading
        val pending = _downloads.value
            .filter { it.status == DownloadStatus.PENDING }
            .take(slots)

        pending.forEach { task -> startDownload(task) }
    }

    private fun startDownload(task: DownloadTask) {
        updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING) }

        val job = scope.launch {
            try {
                val protocol = runCatching { Protocol.valueOf(task.server.protocol) }
                    .getOrDefault(Protocol.SMB)

                val downloaded = when (protocol) {
                    Protocol.SMB -> downloadSmb(task)
                    Protocol.HTTP, Protocol.WEBDAV -> downloadHttp(task)
                }

                updateTask(task.id) { current ->
                    val resolvedTotal = if (current.totalBytes > 0L) current.totalBytes else downloaded
                    current.copy(
                        downloadedBytes = downloaded.coerceAtLeast(resolvedTotal),
                        status = DownloadStatus.COMPLETED
                    )
                }
            } catch (_: CancellationException) {
                // pause/cancel による中断
            } catch (e: Exception) {
                Log.e("DownloadManager", "Download failed: ${task.fileName}", e)
                updateTask(task.id) { it.copy(status = DownloadStatus.FAILED) }
            } finally {
                activeJobs.remove(task.id)
                processQueue()
            }
        }

        activeJobs[task.id] = job
    }

    private suspend fun downloadSmb(task: DownloadTask): Long {
        val ctx = appContext ?: throw IllegalStateException("Context not initialized")
        val shareName = task.server.shareName?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SMB共有名が未設定です")
        val remotePath = task.sourceUri
            .removePrefix("/")
            .replace('/', '\\')

        val client = SMBClient()
        val connection = client.connect(task.server.host, task.server.port ?: 445)
        try {
            val session = connection.authenticate(
                AuthenticationContext(
                    task.server.username.orEmpty(),
                    (task.server.password ?: "").toCharArray(),
                    ""
                )
            )

            session.use {
                val share = session.connectShare(shareName) as? DiskShare
                    ?: throw IllegalStateException("ディスク共有に接続できません")
                val smbFile = share.openFile(
                    remotePath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )

                smbFile.use { file ->
                    return writeToDestination(ctx, task, file.inputStream)
                }
            }
        } finally {
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }

    private suspend fun downloadHttp(task: DownloadTask): Long {
        val ctx = appContext ?: throw IllegalStateException("Context not initialized")
        val server = task.server
        val scheme = "http"
        val hostPort = if (server.port != null) {
            "${server.host}:${server.port}"
        } else {
            server.host
        }
        val base = server.basePath?.trim('/')?.takeIf { it.isNotBlank() }
        val remote = task.sourceUri.removePrefix("/")
        val url = if (base.isNullOrBlank()) {
            "$scheme://$hostPort/$remote"
        } else {
            "$scheme://$hostPort/$base/$remote"
        }

        val requestBuilder = Request.Builder().url(url).get()
        val user = server.username?.takeIf { it.isNotBlank() }
        val pass = server.password?.takeIf { it.isNotBlank() }
        if (user != null && pass != null) {
            val token = java.util.Base64.getEncoder()
                .encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
            requestBuilder.addHeader("Authorization", "Basic $token")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("レスポンス本文が空です")
            body.byteStream().use { input ->
                return writeToDestination(ctx, task, input)
            }
        }
    }

    private suspend fun writeToDestination(
        context: Context,
        task: DownloadTask,
        input: java.io.InputStream
    ): Long {
        val destinationDir = DocumentFile.fromTreeUri(context, task.destinationUri)
            ?: throw IllegalStateException("保存先フォルダにアクセスできません")

        val mimeType = when {
            task.fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            task.fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            task.fileName.endsWith(".zip", ignoreCase = true) -> "application/zip"
            task.fileName.endsWith(".cbz", ignoreCase = true) -> "application/zip"
            task.fileName.endsWith(".cbr", ignoreCase = true) -> "application/x-rar-compressed"
            else -> "application/octet-stream"
        }

        val destFile = destinationDir.createFile(mimeType, task.fileName)
            ?: throw IllegalStateException("保存先にファイルを作成できません")

        context.contentResolver.openOutputStream(destFile.uri, "w")?.use { output ->
            val buffer = ByteArray(8_192)
            var totalRead = 0L

            while (true) {
                coroutineContext.ensureActive()
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) {
                    break
                }

                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                updateTask(task.id) { current ->
                    current.copy(
                        downloadedBytes = totalRead,
                        status = DownloadStatus.DOWNLOADING
                    )
                }
            }

            output.flush()
            return totalRead
        } ?: throw IllegalStateException("保存先ストリームを開けません")
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _downloads.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId) transform(task) else task
            }
        }
    }

    companion object {
        val shared: NetworkDownloadManager by lazy { NetworkDownloadManager() }
    }
}
