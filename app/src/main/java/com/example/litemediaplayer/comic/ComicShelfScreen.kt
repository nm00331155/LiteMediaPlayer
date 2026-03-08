package com.example.litemediaplayer.comic

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.litemediaplayer.R
import com.example.litemediaplayer.core.ui.PageSettingsSheet
import android.os.SystemClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicShelfScreen(
    onOpenBook: (Long) -> Unit = {},
    onOpenFolderManager: () -> Unit = {},
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var titleTapCount by remember { mutableStateOf(0) }
    var lastTitleTapMs by remember { mutableLongStateOf(0L) }
    var showHiddenComics by remember { mutableStateOf(false) }

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
            onTouchZoneChange = viewModel::updateTouchZoneConfig
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

                                if (!showHiddenComics) {
                                    if (uiState.fiveTapAuthRequired) {
                                        val targetActivity = fragmentActivity
                                        if (targetActivity != null) {
                                            val executor = ContextCompat.getMainExecutor(targetActivity)
                                            val prompt = BiometricPrompt(
                                                targetActivity,
                                                executor,
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(
                                                        result: BiometricPrompt.AuthenticationResult
                                                    ) {
                                                        showHiddenComics = true
                                                        viewModel.toggleHiddenComicVisibility(true)
                                                    }
                                                }
                                            )
                                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                .setTitle("認証")
                                                .setSubtitle("隠しコミックを表示するには認証してください")
                                                .setAllowedAuthenticators(
                                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                                )
                                                .build()
                                            prompt.authenticate(promptInfo)
                                        }
                                    } else {
                                        showHiddenComics = true
                                        viewModel.toggleHiddenComicVisibility(true)
                                    }
                                } else {
                                    showHiddenComics = false
                                    viewModel.toggleHiddenComicVisibility(false)
                                }
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
                    uiState.books.filter { it.folderId == selectedFolderId }
                } else {
                    emptyList()
                }
            }
            val gridMinSize = uiState.settings.gridSize.minDp.dp

            if (selectedFolderId == null) {
                val visibleFolders = remember(uiState.folders, uiState.showHiddenLocked) {
                    uiState.folders.filter { folderUi ->
                        if (folderUi.isLockEnabled && folderUi.isHidden) {
                            uiState.showHiddenLocked
                        } else {
                            true
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = gridMinSize),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleFolders, key = { it.id }) { folder ->
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
    onTouchZoneChange: (TouchZoneConfig) -> Unit
) {
    Text("めくり方向")
    OptionChips(
        options = ReadingDirection.entries,
        selected = settings.readingDirection,
        label = { if (it == ReadingDirection.RTL) "右→左" else "左→右" },
        onSelect = onDirectionChange
    )

    Text("閲覧モード")
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

    Text("表示サイズ")
    OptionChips(
        options = GridSize.entries,
        selected = settings.gridSize,
        label = { it.label },
        onSelect = onGridSizeChange
    )

    Text("アニメーション")
    OptionChips(
        options = PageAnimation.entries,
        selected = settings.animation,
        label = {
            when (it) {
                PageAnimation.SLIDE -> "スライド"
                PageAnimation.CURL -> "カール"
                PageAnimation.FADE -> "フェード"
                PageAnimation.NONE -> "なし"
            }
        },
        onSelect = onAnimationChange
    )

    Text("アニメーション速度: ${settings.animationSpeedMs}ms")
    Slider(
        value = settings.animationSpeedMs.toFloat(),
        onValueChange = { onAnimationSpeedChange(it.toInt()) },
        valueRange = 100f..1000f
    )

    SettingSwitchRow(
        label = "ブルーライトフィルタ",
        checked = settings.blueLightFilterEnabled,
        onChange = onBlueLightChange
    )

    Text("ズーム上限: ${"%.1f".format(settings.zoomMax)}x")
    Slider(
        value = settings.zoomMax,
        onValueChange = onZoomMaxChange,
        valueRange = 2f..5f
    )

    SettingSwitchRow(
        label = "自動分割",
        checked = settings.autoSplitEnabled,
        onChange = onAutoSplitChange
    )

    Text("見開き判定閾値: ${"%.2f".format(settings.splitThreshold)}")
    Slider(
        value = settings.splitThreshold,
        onValueChange = onSplitThresholdChange,
        valueRange = 1f..2f
    )

    SettingSwitchRow(
        label = "スマート分割線検出",
        checked = settings.smartSplitEnabled,
        onChange = onSmartSplitChange
    )

    Text("分割位置微調整: ${"%.2f".format(settings.splitOffsetPercent)}")
    Slider(
        value = settings.splitOffsetPercent,
        onValueChange = onSplitOffsetChange,
        valueRange = -0.05f..0.05f
    )

    SettingSwitchRow(
        label = "自動トリミング",
        checked = settings.autoTrimEnabled,
        onChange = onAutoTrimChange
    )

    Text("背景色許容差: ${settings.trimTolerance}")
    Slider(
        value = settings.trimTolerance.toFloat(),
        onValueChange = { onTrimToleranceChange(it.toInt()) },
        valueRange = 10f..60f
    )

    Text("安全マージン: ${settings.trimSafetyMargin}px")
    Slider(
        value = settings.trimSafetyMargin.toFloat(),
        onValueChange = { onTrimSafetyMarginChange(it.toInt()) },
        valueRange = 0f..20f
    )

    Text("コンテンツ検出感度")
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

    Text("タッチレイアウト")
    OptionChips(
        options = TouchZoneLayout.entries,
        selected = settings.touchZone.layout,
        label = { it.label },
        onSelect = { layout ->
            onTouchZoneChange(settings.touchZone.copy(layout = layout))
        }
    )

    Text("長押し判定時間: ${settings.touchZone.longPressMs}ms")
    Slider(
        value = settings.touchZone.longPressMs.toFloat(),
        onValueChange = {
            onTouchZoneChange(settings.touchZone.copy(longPressMs = it.toInt()))
        },
        valueRange = 200f..1500f
    )

    Text("スキップページ数: ${settings.touchZone.skipPageCount}")
    Slider(
        value = settings.touchZone.skipPageCount.toFloat(),
        onValueChange = {
            onTouchZoneChange(settings.touchZone.copy(skipPageCount = it.toInt()))
        },
        valueRange = 1f..50f
    )

    Text("音量上ボタン")
    TouchActionSelector(
        current = settings.touchZone.volumeUpAction,
        onSelect = { onTouchZoneChange(settings.touchZone.copy(volumeUpAction = it)) }
    )

    Text("音量下ボタン")
    TouchActionSelector(
        current = settings.touchZone.volumeDownAction,
        onSelect = { onTouchZoneChange(settings.touchZone.copy(volumeDownAction = it)) }
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
