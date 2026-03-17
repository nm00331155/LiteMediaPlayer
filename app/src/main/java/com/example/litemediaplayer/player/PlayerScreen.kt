package com.example.litemediaplayer.player

import android.content.res.Configuration
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.litemediaplayer.R
import com.example.litemediaplayer.core.AppLogger
import com.example.litemediaplayer.core.ui.PageSettingsSheet
import com.example.litemediaplayer.settings.PlayerResizeMode
import com.example.litemediaplayer.settings.PlayerRotation
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onPlayVideo: (String) -> Unit,
    onOpenFolderManager: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var titleTapCount by remember { mutableStateOf(0) }
    var lastTitleTapMs by remember { mutableLongStateOf(0L) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(viewModel::addVideoFolder)
    }

    val selectedFolder = uiState.folders.find { it.id == uiState.selectedFolderId }

    val deleteTarget = uiState.deleteConfirmTarget

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("フォルダ登録を解除") },
            text = {
                Text("「${deleteTarget.displayName}」の登録を解除しますか？")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteFolder(deleteTarget) }
                ) {
                    Text("解除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text("キャンセル")
                }
            }
        )
    }

    PageSettingsSheet(
        visible = showSettings,
        onDismiss = { showSettings = false },
        title = "プレイヤー設定"
    ) {
        PlayerSettingsContent(
            uiState = uiState,
            onSeekIntervalChange = viewModel::setSeekInterval,
            onResizeModeChange = viewModel::setPlayerResizeMode,
            onRotationChange = viewModel::setPlayerRotation,
            onTogglePanelLock = viewModel::togglePlayerPanelLock,
            onToggleOrientationLock = viewModel::togglePlayerOrientationLock,
            onToggleSubtitleAutoLoad = viewModel::toggleSubtitleAutoLoad,
            onClearFolderLocks = viewModel::clearAllFolderLocks,
            onGestureSeekEnabledChange = viewModel::updateGestureSeekEnabled,
            onGestureVolumeEnabledChange = viewModel::updateGestureVolumeEnabled,
            onGestureBrightnessEnabledChange = viewModel::updateGestureBrightnessEnabled,
            onGestureDoubleTapPlayPauseChange = viewModel::updateGestureDoubleTapPlayPause,
            onGestureBrightnessZoneEndChange = viewModel::updateGestureBrightnessZoneEnd,
            onGestureVolumeZoneStartChange = viewModel::updateGestureVolumeZoneStart
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "プレイヤー",
                        modifier = Modifier.clickable {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTitleTapMs > 2_000L) {
                                titleTapCount = 1
                            } else {
                                titleTapCount += 1
                            }
                            lastTitleTapMs = now

                            if (titleTapCount >= 5) {
                                titleTapCount = 0
                                if (!uiState.fiveTapEnabled) {
                                    return@clickable
                                }
                                viewModel.toggleHiddenFolderVisibility(!uiState.showHiddenLocked)
                            }
                        }
                    )
                },
                actions = {
                    IconButton(onClick = onOpenFolderManager) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "フォルダ管理")
                    }
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "フォルダ追加")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                        .clickable { viewModel.consumeError() }
                )
            }

            if (uiState.folders.isEmpty()) {
                EmptyFolderContent(onAddFolder = { folderPicker.launch(null) })
                return@Column
            }

            Text(
                text = "フォルダ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.folders, key = { it.id }) { folder ->
                    FilterChip(
                        selected = folder.id == uiState.selectedFolderId,
                        onClick = {
                            if (folder.id != uiState.selectedFolderId) {
                                viewModel.selectFolder(folder.id)
                            }
                        },
                        label = {
                            Text(
                                text = "${folder.displayName} (${folder.videoCount})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            if (folder.isLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.width(16.dp)
                                )
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedFolder?.displayName ?: "動画",
                    style = MaterialTheme.typography.titleMedium
                )
                if (selectedFolder != null) {
                    IconButton(onClick = { viewModel.showDeleteConfirm(selectedFolder) }) {
                        Icon(Icons.Default.Delete, contentDescription = "登録解除")
                    }
                }
            }

            if (uiState.videos.isEmpty()) {
                Text(
                    text = "動画ファイルが見つかりません",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.videos, key = { it.uri.toString() }) { video ->
                        PlayerVideoItem(
                            video = video,
                            onClick = {
                                val currentFolder = uiState.folders.find {
                                    it.id == uiState.selectedFolderId
                                }

                                val shouldOpen = if (
                                    currentFolder != null &&
                                    currentFolder.isLocked &&
                                    !uiState.showHiddenLocked
                                ) {
                                    viewModel.requestFolderUnlock(currentFolder.id)
                                } else {
                                    true
                                }

                                if (shouldOpen) {
                                    viewModel.selectVideo(video.uri)
                                    onPlayVideo(video.uri.toString())
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPlaybackScreen(
    videoUri: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val componentActivity = remember(activity) { activity as? ComponentActivity }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOrientation = LocalConfiguration.current.orientation
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val parsedVideoUri = remember(videoUri) {
        runCatching { Uri.parse(videoUri) }
            .getOrElse { videoUri.toUri() }
    }
    val videoThumbnailLoader = remember(context.applicationContext) {
        VideoThumbnailLoader(context.applicationContext)
    }

    var showSettings by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isSliderDragging by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.6f) }
    var volumeAccumulator by remember { mutableFloatStateOf(0f) }
    var brightnessAccumulator by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var hasStartedPlayback by remember { mutableStateOf(false) }
    var lastInteractionAtMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var sliderWidthPx by remember { mutableStateOf(0) }
    var resumePlaybackAfterSliderDrag by remember { mutableStateOf(false) }
    var gestureSeekBasePositionMs by remember(videoUri) { mutableLongStateOf(0L) }
    var resumePlaybackAfterGestureSeek by remember(videoUri) { mutableStateOf(false) }
    var seekPreviewBitmap by remember(videoUri) { mutableStateOf<Bitmap?>(null) }
    var seekPreviewVisible by remember(videoUri) { mutableStateOf(false) }
    var previewRequestPositionMs by remember(videoUri) { mutableLongStateOf(-1L) }
    var isSeekPreviewLoading by remember(videoUri) { mutableStateOf(false) }
    var previewDismissRequestId by remember(videoUri) { mutableIntStateOf(0) }
    var hasLoggedPreviewResult by remember(videoUri) { mutableStateOf(false) }

    fun registerInteraction() {
        controlsVisible = true
        lastInteractionAtMs = SystemClock.elapsedRealtime()
    }

    fun applyGestureSeekDelta(deltaMs: Long) {
        val targetPositionMs = (gestureSeekBasePositionMs + deltaMs)
            .coerceAtLeast(0L)
            .coerceAtMost(durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        currentPositionMs = targetPositionMs
        sliderValue = targetPositionMs.toFloat()
        exoPlayer.seekTo(targetPositionMs)
    }

    PageSettingsSheet(
        visible = showSettings,
        onDismiss = { showSettings = false },
        title = "再生設定"
    ) {
        PlayerSettingsContent(
            uiState = uiState,
            onSeekIntervalChange = viewModel::setSeekInterval,
            onResizeModeChange = viewModel::setPlayerResizeMode,
            onRotationChange = viewModel::setPlayerRotation,
            onTogglePanelLock = viewModel::togglePlayerPanelLock,
            onToggleOrientationLock = viewModel::togglePlayerOrientationLock,
            onToggleSubtitleAutoLoad = viewModel::toggleSubtitleAutoLoad,
            onClearFolderLocks = viewModel::clearAllFolderLocks,
            onGestureSeekEnabledChange = viewModel::updateGestureSeekEnabled,
            onGestureVolumeEnabledChange = viewModel::updateGestureVolumeEnabled,
            onGestureBrightnessEnabledChange = viewModel::updateGestureBrightnessEnabled,
            onGestureDoubleTapPlayPauseChange = viewModel::updateGestureDoubleTapPlayPause,
            onGestureBrightnessZoneEndChange = viewModel::updateGestureBrightnessZoneEnd,
            onGestureVolumeZoneStartChange = viewModel::updateGestureVolumeZoneStart
        )
    }

    val resolvedRotation = when {
        uiState.playerRotation == PlayerRotation.FORCE_PORTRAIT -> {
            PlayerRotation.FORCE_PORTRAIT
        }

        uiState.playerRotation == PlayerRotation.FORCE_LANDSCAPE -> {
            PlayerRotation.FORCE_LANDSCAPE
        }

        uiState.isPlayerOrientationLocked &&
            currentOrientation == Configuration.ORIENTATION_PORTRAIT -> {
            PlayerRotation.FORCE_PORTRAIT
        }

        uiState.isPlayerOrientationLocked -> PlayerRotation.FORCE_LANDSCAPE
        else -> PlayerRotation.FOLLOW_GLOBAL
    }

    val displayedOrientation = when (resolvedRotation) {
        PlayerRotation.FORCE_PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
        PlayerRotation.FORCE_LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
        PlayerRotation.FOLLOW_GLOBAL -> currentOrientation
    }

    val quickRotationTarget = if (displayedOrientation == Configuration.ORIENTATION_PORTRAIT) {
        PlayerRotation.FORCE_LANDSCAPE
    } else {
        PlayerRotation.FORCE_PORTRAIT
    }

    LaunchedEffect(videoUri, previewRequestPositionMs) {
        val requestedPositionMs = previewRequestPositionMs
        if (requestedPositionMs < 0L) {
            isSeekPreviewLoading = false
            seekPreviewBitmap = null
            return@LaunchedEffect
        }

        isSeekPreviewLoading = true
        delay(80)

        val bitmap = videoThumbnailLoader.loadThumbnail(
            uri = parsedVideoUri,
            positionMs = requestedPositionMs,
            width = 360,
            height = 202
        )

        if (previewRequestPositionMs == requestedPositionMs) {
            seekPreviewBitmap = bitmap
            isSeekPreviewLoading = false
            if (!hasLoggedPreviewResult) {
                if (bitmap != null) {
                    AppLogger.d(
                        "PlayerSeekPreview",
                        "Preview loaded at ${requestedPositionMs}ms"
                    )
                } else {
                    AppLogger.w(
                        "PlayerSeekPreview",
                        "Preview unavailable at ${requestedPositionMs}ms"
                    )
                }
                hasLoggedPreviewResult = true
            }
        }
    }

    LaunchedEffect(previewDismissRequestId, isSliderDragging, isSeekPreviewLoading) {
        val requestId = previewDismissRequestId
        if (requestId == 0 || isSliderDragging || isSeekPreviewLoading) {
            return@LaunchedEffect
        }

        delay(350)

        if (!isSliderDragging && !isSeekPreviewLoading && previewDismissRequestId == requestId) {
            seekPreviewVisible = false
            seekPreviewBitmap = null
            previewRequestPositionMs = -1L
        }
    }

    DisposableEffect(
        componentActivity,
        resolvedRotation
    ) {
        val targetActivity = componentActivity ?: return@DisposableEffect onDispose { }
        val restoreState = FullScreenUtil.enter(targetActivity, resolvedRotation)

        onDispose {
            FullScreenUtil.exit(targetActivity, restoreState)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.e(
                    "PlayerPlayback",
                    "Playback error: code=${error.errorCode} name=${error.errorCodeName} " +
                        "cause=${error.cause?.javaClass?.simpleName}: ${error.cause?.message}",
                    error
                )
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(activity) {
        val window = activity?.window ?: return@LaunchedEffect
        val current = window.attributes.screenBrightness
        if (current in 0f..1f) {
            brightnessLevel = current
        }
    }

    LaunchedEffect(Unit) {
        if (hasStartedPlayback) {
            return@LaunchedEffect
        }
        hasStartedPlayback = true

        val target = parsedVideoUri

        AppLogger.d("PlayerPlayback", "Playing URI: $target (scheme=${target.scheme})")

        try {
            if (target.scheme == "content") {
                val canRead = runCatching {
                    context.contentResolver.openFileDescriptor(target, "r")?.use { true } ?: false
                }.getOrDefault(false)

                AppLogger.d("PlayerPlayback", "canRead=$canRead")

                if (canRead) {
                    val dataSourceFactory = DefaultDataSource.Factory(context)
                    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(target))
                    exoPlayer.setMediaSource(mediaSource)
                } else {
                    AppLogger.w(
                        "PlayerPlayback",
                        "Cannot open FD, fallback to MediaItem.fromUri"
                    )
                    exoPlayer.setMediaItem(MediaItem.fromUri(target))
                }
            } else {
                exoPlayer.setMediaItem(MediaItem.fromUri(target))
            }

            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            AppLogger.e("PlayerPlayback", "Failed to play URI: $target", e)
        }

        registerInteraction()
    }

    LaunchedEffect(controlsVisible, lastInteractionAtMs, uiState.isPlayerPanelLocked) {
        if (!controlsVisible || uiState.isPlayerPanelLocked) {
            return@LaunchedEffect
        }

        delay(3_000)
        val elapsed = SystemClock.elapsedRealtime() - lastInteractionAtMs
        if (elapsed >= 3_000L) {
            controlsVisible = false
        }
    }

    LaunchedEffect(exoPlayer, isSliderDragging) {
        while (true) {
            if (!isSliderDragging) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                sliderValue = currentPositionMs.toFloat()
            }
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    val videoTitle = parsedVideoUri.lastPathSegment
        ?: videoUri.substringAfterLast('/')

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val playerView = @Composable {
            AndroidView(
                factory = { contextValue ->
                    PlayerView(contextValue).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = if (uiState.playerResizeMode == PlayerResizeMode.ZOOM) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = if (uiState.playerResizeMode == PlayerResizeMode.ZOOM) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (uiState.isPlayerPanelLocked) {
            Box(modifier = Modifier.fillMaxSize()) {
                playerView()
            }
        } else {
            GestureOverlay(
                seekIntervalSeconds = uiState.seekIntervalSeconds,
                onSeekStart = {
                    registerInteraction()
                    volumeAccumulator = 0f
                    brightnessAccumulator = 0f
                    gestureSeekBasePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    resumePlaybackAfterGestureSeek = exoPlayer.isPlaying
                    if (resumePlaybackAfterGestureSeek) {
                        exoPlayer.pause()
                    }
                },
                onSeekPreview = { deltaMs ->
                    applyGestureSeekDelta(deltaMs)
                },
                onSeekCommit = { deltaMs ->
                    registerInteraction()
                    applyGestureSeekDelta(deltaMs)
                    if (resumePlaybackAfterGestureSeek) {
                        exoPlayer.play()
                    }
                    resumePlaybackAfterGestureSeek = false
                },
                onVolumeDelta = { normalizedDelta ->
                    registerInteraction()
                    volumeAccumulator += normalizedDelta
                    val steps = (volumeAccumulator * maxVolume * 1.5f).roundToInt()
                    if (steps != 0) {
                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val updated = (currentVolume + steps).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, updated, 0)
                        volumeAccumulator = 0f
                        ((updated.toFloat() / maxVolume) * 100f).roundToInt()
                    } else {
                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        ((currentVolume.toFloat() / maxVolume) * 100f).roundToInt()
                    }
                },
                onBrightnessDelta = { normalizedDelta ->
                    registerInteraction()
                    brightnessAccumulator += normalizedDelta
                    val delta = brightnessAccumulator
                    if (kotlin.math.abs(delta) > 0.005f) {
                        val updated = (brightnessLevel + delta).coerceIn(0.05f, 1f)
                        brightnessLevel = updated
                        brightnessAccumulator = 0f
                        val window = activity?.window
                        if (window != null) {
                            val params = window.attributes
                            params.screenBrightness = updated
                            window.attributes = params
                        }
                        updated
                    } else {
                        brightnessLevel
                    }
                },
                onTogglePlayPause = {
                    registerInteraction()
                    volumeAccumulator = 0f
                    brightnessAccumulator = 0f
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    } else if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
                onSingleTap = {
                    if (controlsVisible) {
                        controlsVisible = false
                    } else {
                        registerInteraction()
                    }
                },
                zoneConfig = GestureZoneConfig(
                    brightnessZoneEnd = uiState.gestureBrightnessZoneEnd,
                    volumeZoneStart = uiState.gestureVolumeZoneStart
                ),
                enableSeek = uiState.gestureSeekEnabled,
                enableVolume = uiState.gestureVolumeEnabled,
                enableBrightness = uiState.gestureBrightnessEnabled,
                enableDoubleTapPlayPause = uiState.gestureDoubleTapPlayPause,
                modifier = Modifier.fillMaxSize()
            ) {
                playerView()
            }
        }

        if (controlsVisible && !uiState.isPlayerPanelLocked) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(top = 10.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "戻る",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = videoTitle,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "設定",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = viewModel::togglePlayerPanelLock) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "パネルロック",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        if (controlsVisible && !uiState.isPlayerPanelLocked) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            registerInteraction()
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0)
                                exoPlayer.prepare()
                                exoPlayer.play()
                            } else if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    val remaining = (durationMs - currentPositionMs).coerceAtLeast(0L)
                    Text(
                        text = "${formatDuration(currentPositionMs)} / -${formatDuration(remaining)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            sliderWidthPx = coordinates.size.width
                        }
                ) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { value ->
                            registerInteraction()
                            val previewPositionMs = value.toLong().coerceAtLeast(0L)
                            if (!isSliderDragging) {
                                resumePlaybackAfterSliderDrag = exoPlayer.isPlaying
                                if (resumePlaybackAfterSliderDrag) {
                                    exoPlayer.pause()
                                }
                                isSliderDragging = true
                                previewDismissRequestId = 0
                                hasLoggedPreviewResult = false
                                seekPreviewBitmap = null
                                AppLogger.d(
                                    "PlayerSeekPreview",
                                    "Preview interaction start at ${previewPositionMs}ms"
                                )
                            }
                            seekPreviewVisible = true
                            previewRequestPositionMs = previewPositionMs
                            sliderValue = value
                            currentPositionMs = previewPositionMs
                            exoPlayer.seekTo(previewPositionMs)
                        },
                        onValueChangeFinished = {
                            registerInteraction()
                            exoPlayer.seekTo(sliderValue.toLong())
                            currentPositionMs = sliderValue.toLong()
                            isSliderDragging = false
                            previewDismissRequestId += 1
                            AppLogger.d(
                                "PlayerSeekPreview",
                                "Preview interaction end at ${sliderValue.toLong()}ms"
                            )
                            if (resumePlaybackAfterSliderDrag) {
                                exoPlayer.play()
                            }
                            resumePlaybackAfterSliderDrag = false
                        },
                        valueRange = 0f..(durationMs.takeIf { it > 0L }?.toFloat() ?: 1f)
                    )

                    if (seekPreviewVisible && durationMs > 0L) {
                        val density = LocalDensity.current
                        val fraction = (sliderValue / durationMs.toFloat()).coerceIn(0f, 1f)
                        val previewWidth = 168.dp
                        val previewWidthPx = with(density) { previewWidth.toPx() }
                        val offsetXPx = (fraction * sliderWidthPx - previewWidthPx / 2f)
                            .coerceIn(0f, (sliderWidthPx - previewWidthPx).coerceAtLeast(0f))

                        Column(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        offsetXPx.toInt(),
                                        -with(density) { 144.dp.toPx() }.toInt()
                                    )
                                }
                                .width(previewWidth)
                                .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(94.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                val previewBitmap = seekPreviewBitmap
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (isSeekPreviewLoading) {
                                    Text(
                                        text = "読込中",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                } else {
                                    Text(
                                        text = "プレビューなし",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Text(
                                text = formatDuration(sliderValue.toLong()),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "中央シーク: ${uiState.seekIntervalSeconds}秒",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )

                    TextButton(
                        onClick = {
                            registerInteraction()
                            viewModel.setPlayerRotation(quickRotationTarget)
                        }
                    ) {
                        Text(
                            text = if (quickRotationTarget == PlayerRotation.FORCE_PORTRAIT) {
                                "縦に切替"
                            } else {
                                "横に切替"
                            },
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (uiState.isPlayerPanelLocked) {
            IconButton(
                onClick = {
                    controlsVisible = false
                    viewModel.togglePlayerPanelLock()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "ロック解除",
                    tint = Color.White
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
private fun EmptyFolderContent(onAddFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "動画フォルダが未登録です",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "フォルダを追加して一覧を表示できます",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
        )
        Button(onClick = onAddFolder) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(text = "フォルダ追加", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun PlayerVideoItem(
    video: VideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailModel = remember(video.uri) {
        ImageRequest.Builder(context)
            .data(video.uri)
            .size(320, 180)
            .memoryCacheKey("video_thumb_${video.uri}")
            .diskCacheKey("video_thumb_${video.uri}")
            .crossfade(true)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = thumbnailModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_video_placeholder),
                    error = painterResource(id = R.drawable.ic_video_placeholder)
                )

                if (video.durationMs > 0L) {
                    Text(
                        text = formatDuration(video.durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatFileSize(video.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!video.resolution.isNullOrBlank()) {
                        Text(
                            text = video.resolution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsContent(
    uiState: PlayerUiState,
    onSeekIntervalChange: (Int) -> Unit,
    onResizeModeChange: (PlayerResizeMode) -> Unit,
    onRotationChange: (PlayerRotation) -> Unit,
    onTogglePanelLock: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onToggleSubtitleAutoLoad: () -> Unit,
    onClearFolderLocks: () -> Unit,
    onGestureSeekEnabledChange: (Boolean) -> Unit,
    onGestureVolumeEnabledChange: (Boolean) -> Unit,
    onGestureBrightnessEnabledChange: (Boolean) -> Unit,
    onGestureDoubleTapPlayPauseChange: (Boolean) -> Unit,
    onGestureBrightnessZoneEndChange: (Float) -> Unit,
    onGestureVolumeZoneStartChange: (Float) -> Unit
) {
    SeekIntervalSetting(
        currentValue = uiState.seekIntervalSeconds,
        onValueChange = onSeekIntervalChange
    )

    Text(text = "画面サイズ", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onResizeModeChange(PlayerResizeMode.FIT) },
            enabled = uiState.playerResizeMode != PlayerResizeMode.FIT
        ) {
            Text("FIT")
        }
        Button(
            onClick = { onResizeModeChange(PlayerResizeMode.ZOOM) },
            enabled = uiState.playerResizeMode != PlayerResizeMode.ZOOM
        ) {
            Text("ZOOM")
        }
    }

    Text(text = "回転", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onRotationChange(PlayerRotation.FOLLOW_GLOBAL) },
            enabled = uiState.playerRotation != PlayerRotation.FOLLOW_GLOBAL
        ) {
            Text("GLOBAL")
        }
        Button(
            onClick = { onRotationChange(PlayerRotation.FORCE_LANDSCAPE) },
            enabled = uiState.playerRotation != PlayerRotation.FORCE_LANDSCAPE
        ) {
            Text("横固定")
        }
        Button(
            onClick = { onRotationChange(PlayerRotation.FORCE_PORTRAIT) },
            enabled = uiState.playerRotation != PlayerRotation.FORCE_PORTRAIT
        ) {
            Text("縦固定")
        }
    }

    SettingSwitchRow(
        title = "パネルロック",
        checked = uiState.isPlayerPanelLocked,
        onChange = { onTogglePanelLock() }
    )
    SettingSwitchRow(
        title = "画面回転ロック",
        checked = uiState.isPlayerOrientationLocked,
        onChange = { onToggleOrientationLock() }
    )
    SettingSwitchRow(
        title = "字幕自動読み込み",
        checked = uiState.subtitleAutoLoad,
        onChange = { onToggleSubtitleAutoLoad() }
    )

    Text(
        text = "タッチ操作設定",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp)
    )

    SettingSwitchRow(
        title = "シーク操作",
        checked = uiState.gestureSeekEnabled,
        onChange = onGestureSeekEnabledChange
    )
    SettingSwitchRow(
        title = "音量調整",
        checked = uiState.gestureVolumeEnabled,
        onChange = onGestureVolumeEnabledChange
    )
    SettingSwitchRow(
        title = "明るさ調整",
        checked = uiState.gestureBrightnessEnabled,
        onChange = onGestureBrightnessEnabledChange
    )
    SettingSwitchRow(
        title = "ダブルタップで再生/一時停止",
        checked = uiState.gestureDoubleTapPlayPause,
        onChange = onGestureDoubleTapPlayPauseChange
    )

    Text("明るさ調整エリア: 上半分の左端〜${(uiState.gestureBrightnessZoneEnd * 100).toInt()}%")
    Slider(
        value = uiState.gestureBrightnessZoneEnd,
        onValueChange = onGestureBrightnessZoneEndChange,
        valueRange = 0.1f..0.5f
    )

    Text("音量調整エリア: 上半分の${(uiState.gestureVolumeZoneStart * 100).toInt()}%〜右端")
    Slider(
        value = uiState.gestureVolumeZoneStart,
        onValueChange = onGestureVolumeZoneStartChange,
        valueRange = 0.5f..0.9f
    )

    Text(
        text = "下半分は常にシーク操作として扱います",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(
        onClick = onClearFolderLocks,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Text("フォルダロック解除状態をリセット")
    }
}

@Composable
private fun SeekIntervalSetting(
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val presets = listOf(5, 10, 15, 30)
    val isCustom = currentValue !in presets

    Text(text = "画面中央シーク間隔")
    Text(
        text = "現在: ${currentValue}秒",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { seconds ->
            FilterChip(
                selected = currentValue == seconds && !isCustom,
                onClick = { onValueChange(seconds) },
                label = { Text("${seconds}秒") }
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = { showCustomDialog = true },
            label = {
                if (isCustom) {
                    Text("カスタム(${currentValue}秒)")
                } else {
                    Text("カスタム")
                }
            }
        )
    }

    if (showCustomDialog) {
        CustomSeekIntervalDialog(
            initialValue = currentValue,
            onConfirm = { value ->
                onValueChange(value)
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false }
        )
    }
}

@Composable
private fun CustomSeekIntervalDialog(
    initialValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue.toString()) }
    var sliderValue by remember { mutableFloatStateOf(initialValue.coerceIn(1, 120).toFloat()) }
    val validValue = textValue.toIntOrNull()
    val isValid = validValue != null && validValue in 1..300

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("画面中央シーク間隔を設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("スライダーで選択")
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        textValue = it.toInt().toString()
                    },
                    valueRange = 1f..120f
                )

                Text("または直接入力（1〜300秒）")
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input.filter { it.isDigit() }
                        textValue.toIntOrNull()?.let {
                            sliderValue = it.coerceIn(1, 120).toFloat()
                        }
                    },
                    suffix = { Text("秒") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !isValid && textValue.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isValid && textValue.isNotEmpty()) {
                    Text(
                        text = "1〜300の数値を入力してください",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    validValue?.let(onConfirm)
                },
                enabled = isValid
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) {
        return "0 B"
    }

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    val formatted = if (value >= 100.0 || unitIndex == 0) {
        "%.0f".format(value)
    } else {
        "%.1f".format(value)
    }

    return "$formatted ${units[unitIndex]}"
}
