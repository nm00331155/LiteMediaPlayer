package com.example.litemediaplayer.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.StringWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AppLogger {
    data class LogEntry(
        val id: Long,
        val timestamp: Long = System.currentTimeMillis(),
        val level: String,
        val tag: String,
        val message: String
    ) {
        val timeString: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

        val dateTimeString: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(Date(timestamp))

        override fun toString(): String = "[$dateTimeString] $level/$tag: $message"
    }

    private const val MAX_MEMORY_ENTRIES = 500
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L
    private const val LOG_FILE_NAME = "litemedia_log.txt"
    private const val LOG_BACKUP_NAME = "litemedia_log_prev.txt"

    private val idCounter = AtomicLong(0)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var logFile: File? = null
    private var logDir: File? = null
    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

    fun init(context: Context) {
        logDir = File(context.filesDir, "logs").also { it.mkdirs() }
        logFile = File(logDir, LOG_FILE_NAME)

        // クラッシュ時にスタックトレースをログへ残す
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                writeToFile(
                    level = "CRASH",
                    tag = "UncaughtException",
                    message = "UNCAUGHT EXCEPTION in ${thread.name}: $sw"
                )
            }
            originalHandler?.uncaughtException(thread, throwable)
        }

        i("AppLogger", "Logger initialized. logFile=${logFile?.absolutePath}")
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addEntry(level = "D", tag = tag, message = message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addEntry(level = "I", tag = tag, message = message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
        addEntry(
            level = "W",
            tag = tag,
            message = if (throwable != null) {
                "$message | ${throwable::class.simpleName}: ${throwable.message}"
            } else {
                message
            }
        )
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }

        val msg = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$message | $sw"
        } else {
            message
        }

        addEntry(level = "E", tag = tag, message = msg)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun export(): String {
        val file = logFile
        if (file != null && file.exists() && file.length() > 0L) {
            return runCatching { file.readText() }.getOrDefault("")
        }
        return _logs.value.joinToString(separator = "\n") { entry -> entry.toString() }
    }

    fun createShareIntent(context: Context): Intent? {
        val file = logFile ?: return null
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        val authority = "${context.packageName}.logprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "LiteMedia Player - Debug Log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    private fun addEntry(level: String, tag: String, message: String) {
        val entry = LogEntry(
            id = idCounter.incrementAndGet(),
            level = level,
            tag = tag,
            message = message
        )

        _logs.update { current ->
            (listOf(entry) + current).take(MAX_MEMORY_ENTRIES)
        }

        writeToFile(level = level, tag = tag, message = message)
    }

    private fun writeToFile(level: String, tag: String, message: String) {
        val dir = logDir ?: return
        val current = logFile ?: return

        runCatching {
            if (current.exists() && current.length() > MAX_FILE_SIZE_BYTES) {
                val backup = File(dir, LOG_BACKUP_NAME)
                if (backup.exists()) {
                    backup.delete()
                }
                current.renameTo(backup)
                logFile = File(dir, LOG_FILE_NAME)
            }

            val file = logFile ?: return@runCatching
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(Date())
            FileWriter(file, true).use { writer ->
                writer.appendLine("[$ts] $level/$tag: $message")
            }
        }
    }
}
