package com.example.litemediaplayer.core.memory

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import coil.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DEFAULT_MEMORY_THRESHOLD = 0.7f

data class MemorySnapshot(
    val usedBytes: Long,
    val maxBytes: Long
) {
    val usedMb: Long
        get() = usedBytes / (1024L * 1024L)

    val maxMb: Long
        get() = maxBytes / (1024L * 1024L)

    val usageRatio: Float
        get() = if (maxBytes > 0) usedBytes.toFloat() / maxBytes.toFloat() else 0f
}

@Singleton
class MemoryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver, ComponentCallbacks2 {

    private val runtime: Runtime = Runtime.getRuntime()
    private val _snapshot = MutableStateFlow(readSnapshot())
    private val _thresholdRatio = MutableStateFlow(DEFAULT_MEMORY_THRESHOLD)

    val snapshot: StateFlow<MemorySnapshot> = _snapshot.asStateFlow()

    fun updateThresholdRatio(ratio: Float) {
        _thresholdRatio.value = ratio.coerceIn(0.5f, 0.9f)
    }

    fun currentThresholdRatio(): Float {
        return _thresholdRatio.value
    }

    fun estimateModuleUsageMb(): Map<String, Long> {
        val imageCacheBytes = context.imageLoader.memoryCache?.size?.toLong() ?: 0L
        return mapOf(
            "ImageCache" to imageCacheBytes / (1024L * 1024L),
            "PlayerBuffer" to 0L,
            "RuntimeHeap" to snapshot.value.usedMb
        )
    }

    fun triggerManualCleanup() {
        val imageLoader = context.imageLoader
        imageLoader.memoryCache?.clear()
        Runtime.getRuntime().gc()
        refresh()
    }

    override fun onStart(owner: LifecycleOwner) {
        refresh()
        if (snapshot.value.usageRatio >= _thresholdRatio.value) {
            triggerManualCleanup()
        }
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                trimImageCacheByHalf()
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                context.imageLoader.memoryCache?.clear()
            }
        }
        refresh()
    }

    override fun onLowMemory() {
        context.imageLoader.memoryCache?.clear()
        Runtime.getRuntime().gc()
        refresh()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // no-op
    }

    fun refresh() {
        _snapshot.value = readSnapshot()
    }

    private fun readSnapshot(): MemorySnapshot {
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory()
        return MemorySnapshot(usedBytes = used, maxBytes = max)
    }

    private fun trimImageCacheByHalf() {
        context.imageLoader.memoryCache?.clear()
    }
}
