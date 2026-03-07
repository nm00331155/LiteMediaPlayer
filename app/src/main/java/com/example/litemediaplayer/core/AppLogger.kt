package com.example.litemediaplayer.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AppLogger {
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: String,
        val tag: String,
        val message: String
    ) {
        val timeString: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

        override fun toString(): String = "[$timeString] $level/$tag: $message"
    }

    private const val MAX_MEMORY_ENTRIES = 500
    private const val LOG_FILE_NAME = "litemedia_log.txt"
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var logFile: File? = null
    private var printWriter: PrintWriter? = null
    private val writeLock = Any()

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        logDir.mkdirs()

        val targetFile = File(logDir, LOG_FILE_NAME)
        if (targetFile.exists() && targetFile.length() > MAX_FILE_SIZE_BYTES) {
            val backup = File(logDir, "litemedia_log_prev.txt")
            runCatching { backup.delete() }
            runCatching { targetFile.renameTo(backup) }
        }

        logFile = targetFile

        synchronized(writeLock) {
            printWriter?.close()
            printWriter = PrintWriter(FileWriter(targetFile, true), true)
        }

        val recentLogs = runCatching {
            if (targetFile.exists()) {
                targetFile.readLines().takeLast(MAX_MEMORY_ENTRIES)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        _logs.value = recentLogs.mapNotNull { line -> parseLogLine(line) }

        i("AppLogger", "=== App started ===")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("CRASH", "Uncaught exception on ${thread.name}", throwable)
            flush()
            defaultHandler?.uncaughtException(thread, throwable)
        }
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
        addEntry(
            level = "E",
            tag = tag,
            message = if (throwable != null) {
                "$message | ${throwable::class.simpleName}: ${throwable.message}"
            } else {
                message
            }
        )

        if (throwable != null) {
            synchronized(writeLock) {
                throwable.printStackTrace(printWriter ?: return@synchronized)
                printWriter?.flush()
            }
        }
    }

    fun clear() {
        _logs.value = emptyList()

        synchronized(writeLock) {
            printWriter?.close()
            printWriter = null

            val file = logFile
            if (file != null) {
                runCatching { file.delete() }
                printWriter = PrintWriter(FileWriter(file, false), true)
            }
        }
    }

    fun flush() {
        synchronized(writeLock) {
            printWriter?.flush()
        }
    }

    fun export(): String {
        val file = logFile
        return if (file != null && file.exists()) {
            runCatching { file.readText() }
                .getOrElse { _logs.value.joinToString(separator = "\n") { entry -> entry.toString() } }
        } else {
            _logs.value.joinToString(separator = "\n") { entry -> entry.toString() }
        }
    }

    fun shareLogFile(context: Context) {
        flush()

        val file = logFile
        if (file == null || !file.exists()) {
            Log.w("AppLogger", "No log file to share")
            return
        }

        val authority = "${context.packageName}.logprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LiteMedia Player ログ")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            "ログを共有"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    }

    private fun addEntry(level: String, tag: String, message: String) {
        val entry = LogEntry(level = level, tag = tag, message = message)

        _logs.update { current ->
            (listOf(entry) + current).take(MAX_MEMORY_ENTRIES)
        }

        synchronized(writeLock) {
            printWriter?.println(entry.toString())
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        val regex = Regex("""\[(\d{2}:\d{2}:\d{2}\.\d{3})] (\w)/([^:]+): (.+)""")
        val match = regex.matchEntire(line) ?: return null
        return LogEntry(
            level = match.groupValues[2],
            tag = match.groupValues[3],
            message = match.groupValues[4]
        )
    }
}
