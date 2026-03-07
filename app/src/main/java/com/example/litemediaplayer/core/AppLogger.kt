package com.example.litemediaplayer.core

import android.util.Log
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

    private const val MAX_ENTRIES = 500
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

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
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun export(): String {
        return _logs.value.joinToString(separator = "\n") { it.toString() }
    }

    private fun addEntry(level: String, tag: String, message: String) {
        _logs.update { current ->
            (listOf(LogEntry(level = level, tag = tag, message = message)) + current)
                .take(MAX_ENTRIES)
        }
    }
}
