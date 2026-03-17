package com.example.litemediaplayer.comic

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.litemediaplayer.R
import com.example.litemediaplayer.core.ui.PageSettingsSheet
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicShelfScreen(
    onOpenBook: (Long) -> Unit = {},
    onOpenFolderManager: () -> Unit = {},
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var titleTapCount by remember { mutableIntStateOf(0) }
    var lastTitleTapMs by remember { mutableLongStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.registerComicFolder(uri, context.contentResolver)
        }
    }

    val archivePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.registerComicArchive(uri, context.contentResolver)
        }
    }

    val progressImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importProgress(uri)
        }
    }

    PageSettingsSheet(
        visible = showSettings,
        onDismiss = { showSettings = false },
        title = "コミック設定"
    ) {
        ComicSettingsContent(
            settings = uiState.settings,
            onDirectionChange = viewModel::updateReadingDirection,
            onModeChange = viewModel::updateReaderMode,
            onGridSizeChange = viewModel::updateGridSize,
            onAnimationChange = viewModel::updatePageAnimation,
            onAnimationSpeedChange = viewModel::updateAnimationSpeed,
            onBlueLightChange = viewModel::updateBlueLightFilter,
            onZoomMaxChange = viewModel::updateZoomMax,
            onAutoSplitChange = viewModel::updateAutoSplit,
            onSplitThresholdChange = viewModel::updateSplitThreshold,
            onSmartSplitChange = viewModel::updateSmartSplit,
            onSplitOffsetChange = viewModel::updateSplitOffset,
            onAutoTrimChange = viewModel::updateAutoTrim,
            onTrimToleranceChange = viewModel::updateTrimTolerance,
            onTrimSafetyMarginChange = viewModel::updateTrimSafetyMargin,
            onTrimSensitivityChange = viewModel::updateTrimSensitivity,
            onTouchZoneChange = viewModel::updateTouchZoneConfig,
            registeredSyncDeviceCount = uiState.registeredSyncDeviceCount,
            onShareProgress = {
                coroutineScope.launch {
                    val shareIntent = viewModel.buildProgressShareIntent() ?: return@launch
                    context.startActivity(
                        Intent.createChooser(shareIntent, "コミック進捗を共有")
                    )
                }
            },
            onImportProgress = {
                progressImportPicker.launch(arrayOf("application/json", "*/*"))
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.tab_comic),
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
                                viewModel.toggleHiddenComicVisibility(!uiState.showHiddenLocked)
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (uiState.selectedFolderId != null) {
                        IconButton(onClick = { viewModel.selectComicFolder(null) }) {
                            Icon(Icons.Default.Home, contentDescription = "本棚に戻る")
                        }
                    }
                    IconButton(onClick = onOpenFolderManager) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "フォルダ管理")
                    }
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "追加")
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("フォルダを追加") },
                                onClick = {
                                    showAddMenu = false
                                    folderPicker.launch(null)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ZIP/CBZ/CBRを追加") },
                                onClick = {
                                    showAddMenu = false
                                    archivePicker.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/vnd.comicbook+zip",
                                            "application/vnd.rar",
                                            "application/x-rar-compressed",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderZip, contentDescription = null)
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "コミック設定")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { viewModel.consumeStatus() }
                )
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { viewModel.consumeError() }
                )
            }

            val selectedFolderId = uiState.selectedFolderId
            val displayBooks = remember(uiState.books, selectedFolderId) {
                if (selectedFolderId != null) {
                    uiState.books
                        .filter { it.folderId == selectedFolderId }
                        .sortedWith(comicBookNaturalComparator)
                } else {
                    emptyList()
                }
            }
            val gridMinSize = uiState.settings.gridSize.minDp.dp

            if (selectedFolderId == null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = gridMinSize),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.folders, key = { it.id }) { folder ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectComicFolder(folder.id) }
                        ) {
                            AsyncImage(
                                model = folder.coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .background(Color.Black)
                            )
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (folder.isLockEnabled) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = " ロック中",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = folder.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2
                                )
                                Text(
                                    text = "${folder.bookCount} 冊",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                BackHandler { viewModel.selectComicFolder(null) }

                val selectedFolder = uiState.folders.firstOrNull { it.id == selectedFolderId }
                Text(
                    text = selectedFolder?.displayName ?: "書籍",
                    style = MaterialTheme.typography.titleMedium
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = gridMinSize),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayBooks, key = { it.id }) { book ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenBook(book.id) }
                        ) {
                            AsyncImage(
                                model = book.coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .background(Color.Black)
                            )
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2
                                )
                                Text(
                                    text = if (book.totalPages > 0) {
                                        "${book.lastReadPage + 1} / ${book.totalPages} ページ"
                                    } else {
                                        "未読"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicSettingsContent(
    settings: ComicReaderSettings,
    onDirectionChange: (ReadingDirection) -> Unit,
    onModeChange: (ReaderMode) -> Unit,
    onGridSizeChange: (GridSize) -> Unit,
    onAnimationChange: (PageAnimation) -> Unit,
    onAnimationSpeedChange: (Int) -> Unit,
    onBlueLightChange: (Boolean) -> Unit,
    onZoomMaxChange: (Float) -> Unit,
    onAutoSplitChange: (Boolean) -> Unit,
    onSplitThresholdChange: (Float) -> Unit,
    onSmartSplitChange: (Boolean) -> Unit,
    onSplitOffsetChange: (Float) -> Unit,
    onAutoTrimChange: (Boolean) -> Unit,
    onTrimToleranceChange: (Int) -> Unit,
    onTrimSafetyMarginChange: (Int) -> Unit,
    onTrimSensitivityChange: (TrimSensitivity) -> Unit,
    onTouchZoneChange: (TouchZoneConfig) -> Unit,
    registeredSyncDeviceCount: Int,
    onShareProgress: () -> Unit,
    onImportProgress: () -> Unit
) {
    val defaultSettings = ComicSettingsDefaults.values
    val defaultTouchZone = defaultSettings.touchZone

    Text(
        text = "進捗共有",
        style = MaterialTheme.typography.titleMedium
    )

    Text(
        text = if (registeredSyncDeviceCount > 0) {
            "共有ボタンで登録端末へ直接送信します。未到達時はJSONファイル共有に切り替えます。登録済み: ${registeredSyncDeviceCount}台"
        } else {
            "登録端末がないため、共有ボタンはJSONファイル共有を開きます。端末登録は設定タブの進捗共有から行えます。"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    OutlinedButton(
        onClick = onShareProgress,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("進捗を共有")
    }

    OutlinedButton(
        onClick = onImportProgress,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("進捗を取り込む")
    }

    ResettableSectionHeader(
        label = "めくり方向",
        onReset = { onDirectionChange(defaultSettings.readingDirection) }
    )
    OptionChips(
        options = ReadingDirection.entries,
        selected = settings.readingDirection,
        label = { if (it == ReadingDirection.RTL) "右→左" else "左→右" },
        onSelect = onDirectionChange
    )

    ResettableSectionHeader(
        label = "閲覧モード",
        onReset = { onModeChange(defaultSettings.mode) }
    )
    OptionChips(
        options = ReaderMode.entries,
        selected = settings.mode,
        label = {
            when (it) {
                ReaderMode.PAGE -> "ページ"
                ReaderMode.VERTICAL -> "縦スクロール"
                ReaderMode.SPREAD -> "見開き"
            }
        },
        onSelect = onModeChange
    )

    ResettableSectionHeader(
        label = "表示サイズ",
        onReset = { onGridSizeChange(defaultSettings.gridSize) }
    )
    OptionChips(
        options = GridSize.entries,
        selected = settings.gridSize,
        label = { it.label },
        onSelect = onGridSizeChange
    )

    ResettableSectionHeader(
        label = "アニメーション",
        onReset = { onAnimationChange(defaultSettings.animation) }
    )
    OptionChips(
        options = PageAnimation.entries.filterNot { it == PageAnimation.CURL },
        selected = settings.animation,
        label = {
            when (it) {
                PageAnimation.SLIDE -> "スライド"
                PageAnimation.FADE -> "フェード"
                PageAnimation.NONE -> "なし"
                PageAnimation.CURL -> "スライド"
            }
        },
        onSelect = onAnimationChange
    )

    IntSliderSetting(
        label = "アニメーション速度",
        value = settings.animationSpeedMs,
        min = ComicSettingsDefaults.ANIMATION_SPEED_MIN,
        max = ComicSettingsDefaults.ANIMATION_SPEED_MAX,
        suffix = "ms",
        onValueChange = onAnimationSpeedChange,
        onReset = { onAnimationSpeedChange(defaultSettings.animationSpeedMs) }
    )

    ResettableSectionHeader(
        label = "ブルーライトフィルタ",
        onReset = { onBlueLightChange(defaultSettings.blueLightFilterEnabled) }
    )
    SettingSwitchRow(
        label = "ブルーライトフィルタ",
        checked = settings.blueLightFilterEnabled,
        onChange = onBlueLightChange
    )

    FloatSliderSetting(
        label = "ズーム上限",
        value = settings.zoomMax,
        min = ComicSettingsDefaults.ZOOM_MAX_MIN,
        max = ComicSettingsDefaults.ZOOM_MAX_MAX,
        suffix = "x",
        decimals = 1,
        onValueChange = onZoomMaxChange,
        onReset = { onZoomMaxChange(defaultSettings.zoomMax) }
    )

    ResettableSectionHeader(
        label = "自動分割",
        onReset = { onAutoSplitChange(defaultSettings.autoSplitEnabled) }
    )
    SettingSwitchRow(
        label = "自動分割",
        checked = settings.autoSplitEnabled,
        onChange = onAutoSplitChange
    )

    FloatSliderSetting(
        label = "見開き判定閾値",
        value = settings.splitThreshold,
        min = ComicSettingsDefaults.SPLIT_THRESHOLD_MIN,
        max = ComicSettingsDefaults.SPLIT_THRESHOLD_MAX,
        decimals = 2,
        onValueChange = onSplitThresholdChange,
        onReset = { onSplitThresholdChange(defaultSettings.splitThreshold) }
    )

    ResettableSectionHeader(
        label = "スマート分割線検出",
        onReset = { onSmartSplitChange(defaultSettings.smartSplitEnabled) }
    )
    SettingSwitchRow(
        label = "スマート分割線検出",
        checked = settings.smartSplitEnabled,
        onChange = onSmartSplitChange
    )

    FloatSliderSetting(
        label = "分割位置微調整",
        value = settings.splitOffsetPercent,
        min = ComicSettingsDefaults.SPLIT_OFFSET_MIN,
        max = ComicSettingsDefaults.SPLIT_OFFSET_MAX,
        decimals = 2,
        allowNegative = true,
        onValueChange = onSplitOffsetChange,
        onReset = { onSplitOffsetChange(defaultSettings.splitOffsetPercent) }
    )

    ResettableSectionHeader(
        label = "自動トリミング",
        onReset = { onAutoTrimChange(defaultSettings.autoTrimEnabled) }
    )
    SettingSwitchRow(
        label = "自動トリミング",
        checked = settings.autoTrimEnabled,
        onChange = onAutoTrimChange
    )

    IntSliderSetting(
        label = "背景色許容差",
        value = settings.trimTolerance,
        min = ComicSettingsDefaults.TRIM_TOLERANCE_MIN,
        max = ComicSettingsDefaults.TRIM_TOLERANCE_MAX,
        onValueChange = onTrimToleranceChange,
        onReset = { onTrimToleranceChange(defaultSettings.trimTolerance) }
    )

    IntSliderSetting(
        label = "安全マージン",
        value = settings.trimSafetyMargin,
        min = ComicSettingsDefaults.TRIM_SAFETY_MARGIN_MIN,
        max = ComicSettingsDefaults.TRIM_SAFETY_MARGIN_MAX,
        suffix = "px",
        onValueChange = onTrimSafetyMarginChange,
        onReset = { onTrimSafetyMarginChange(defaultSettings.trimSafetyMargin) }
    )

    ResettableSectionHeader(
        label = "コンテンツ検出感度",
        onReset = { onTrimSensitivityChange(defaultSettings.trimSensitivity) }
    )
    OptionChips(
        options = TrimSensitivity.entries,
        selected = settings.trimSensitivity,
        label = {
            when (it) {
                TrimSensitivity.LOW -> "低"
                TrimSensitivity.MEDIUM -> "中"
                TrimSensitivity.HIGH -> "高"
            }
        },
        onSelect = onTrimSensitivityChange
    )

    Text(
        text = "操作設定",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp)
    )

    Text(
        text = "各ゾーンの操作割当は、コミック閲覧中にコントロールバーの指アイコンから設定できます",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    ResettableSectionHeader(
        label = "タッチレイアウト",
        onReset = { onTouchZoneChange(settings.touchZone.copy(layout = defaultTouchZone.layout)) }
    )
    OptionChips(
        options = TouchZoneLayout.entries,
        selected = settings.touchZone.layout,
        label = { it.label },
        onSelect = { layout ->
            onTouchZoneChange(settings.touchZone.copy(layout = layout))
        }
    )

    IntSliderSetting(
        label = "長押し判定時間",
        value = settings.touchZone.longPressMs,
        min = ComicSettingsDefaults.TOUCH_LONG_PRESS_MIN,
        max = ComicSettingsDefaults.TOUCH_LONG_PRESS_MAX,
        suffix = "ms",
        onValueChange = { onTouchZoneChange(settings.touchZone.copy(longPressMs = it)) },
        onReset = {
            onTouchZoneChange(settings.touchZone.copy(longPressMs = defaultTouchZone.longPressMs))
        }
    )

    IntSliderSetting(
        label = "スキップページ数",
        value = settings.touchZone.skipPageCount,
        min = ComicSettingsDefaults.TOUCH_SKIP_PAGE_MIN,
        max = ComicSettingsDefaults.TOUCH_SKIP_PAGE_MAX,
        onValueChange = { onTouchZoneChange(settings.touchZone.copy(skipPageCount = it)) },
        onReset = {
            onTouchZoneChange(settings.touchZone.copy(skipPageCount = defaultTouchZone.skipPageCount))
        }
    )

    ResettableSectionHeader(
        label = "音量上ボタン",
        onReset = {
            onTouchZoneChange(settings.touchZone.copy(volumeUpAction = defaultTouchZone.volumeUpAction))
        }
    )
    TouchActionSelector(
        current = settings.touchZone.volumeUpAction,
        onSelect = { onTouchZoneChange(settings.touchZone.copy(volumeUpAction = it)) }
    )

    ResettableSectionHeader(
        label = "音量下ボタン",
        onReset = {
            onTouchZoneChange(settings.touchZone.copy(volumeDownAction = defaultTouchZone.volumeDownAction))
        }
    )
    TouchActionSelector(
        current = settings.touchZone.volumeDownAction,
        onSelect = { onTouchZoneChange(settings.touchZone.copy(volumeDownAction = it)) }
    )
}

@Composable
private fun ResettableSectionHeader(
    label: String,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        TextButton(onClick = onReset) {
            Text("初期値")
        }
    }
}

@Composable
private fun IntSliderSetting(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    onReset: () -> Unit,
    suffix: String = ""
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    ResettableSectionHeader(
        label = "$label: ${value}$suffix",
        onReset = onReset
    )

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }
            textValue = filtered
            filtered.toIntOrNull()?.coerceIn(min, max)?.let(onValueChange)
        },
        label = { Text("直接入力 ($min〜$max$suffix)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(min, max)) },
        valueRange = min.toFloat()..max.toFloat()
    )
}

@Composable
private fun FloatSliderSetting(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    decimals: Int,
    suffix: String = "",
    allowNegative: Boolean = false
) {
    val format = when (decimals) {
        1 -> "%.1f"
        2 -> "%.2f"
        else -> "%f"
    }
    var textValue by remember(value) {
        mutableStateOf(String.format(Locale.US, format, value))
    }

    ResettableSectionHeader(
        label = "$label: ${String.format(Locale.US, format, value)}$suffix",
        onReset = onReset
    )

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            val filtered = buildString {
                input.forEachIndexed { index, ch ->
                    when {
                        ch.isDigit() -> append(ch)
                        ch == '.' && '.' !in this -> append(ch)
                        allowNegative && ch == '-' && index == 0 && '-' !in this -> append(ch)
                    }
                }
            }
            textValue = filtered
            filtered.toFloatOrNull()?.coerceIn(min, max)?.let(onValueChange)
        },
        label = {
            Text(
                "直接入力 (${String.format(Locale.US, format, min)}〜${String.format(Locale.US, format, max)}$suffix)"
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Slider(
        value = value,
        onValueChange = { onValueChange(it.coerceIn(min, max)) },
        valueRange = min..max
    )
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> OptionChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@Composable
private fun TouchActionSelector(
    current: TouchAction,
    onSelect: (TouchAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = current.label,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            TouchAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    },
                    leadingIcon = if (action == current) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun TouchZoneActionEditor(
    zoneLabel: String,
    tapAction: TouchAction,
    onTapSelect: (TouchAction) -> Unit,
    longPressAction: TouchAction,
    onLongPressSelect: (TouchAction) -> Unit
) {
    Text("$zoneLabel タップ")
    TouchActionSelector(current = tapAction, onSelect = onTapSelect)
    Text("$zoneLabel 長押し")
    TouchActionSelector(current = longPressAction, onSelect = onLongPressSelect)
}
