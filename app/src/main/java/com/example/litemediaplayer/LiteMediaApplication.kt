package com.example.litemediaplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.litemediaplayer.core.AppLogger
import com.example.litemediaplayer.core.memory.CleanupWorker
import com.example.litemediaplayer.core.memory.CoilConfig
import com.example.litemediaplayer.core.memory.MemoryMonitor
import com.example.litemediaplayer.comic.ComicProgressLanSyncManager
import com.example.litemediaplayer.network.NetworkDownloadManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LiteMediaApplication : Application(), ImageLoaderFactory {
	@Inject
	lateinit var comicProgressLanSyncManager: ComicProgressLanSyncManager

	private lateinit var memoryMonitor: MemoryMonitor

	override fun onCreate() {
		super.onCreate()
		AppLogger.init(this)
		NetworkDownloadManager.shared.init(this)
		comicProgressLanSyncManager.start()
		memoryMonitor = MemoryMonitor(this)
		registerComponentCallbacks(memoryMonitor)
		CleanupWorker.schedule(this, intervalMinutes = 15)
	}

	override fun onTerminate() {
		comicProgressLanSyncManager.stop()
		unregisterComponentCallbacks(memoryMonitor)
		super.onTerminate()
	}

	override fun newImageLoader(): ImageLoader {
		return CoilConfig.buildImageLoader(this)
	}
}
