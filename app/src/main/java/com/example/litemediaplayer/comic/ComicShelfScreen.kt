package com.example.litemediaplayer.comic

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.litemediaplayer.core.ui.PageSettingsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicShelfScreen(
    onOpenBook: (Long) -> Unit = {},
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

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
            onTrimSensitivityChange = viewModel::updateTrimSensitivity
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("コミック") },
                actions = {
                    Button(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("フォルダ")
                    }
                    Button(
                        onClick = {
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
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("ZIP/CBZ/CBR")
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

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.books, key = { it.id }) { book ->
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
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
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
                                text = statusLabel(book.readStatus),
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

@Composable
private fun ComicSettingsContent(
    settings: ComicReaderSettings,
    onDirectionChange: (ReadingDirection) -> Unit,
    onModeChange: (ReaderMode) -> Unit,
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
    onTrimSensitivityChange: (TrimSensitivity) -> Unit
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

private fun statusLabel(status: String): String {
    return when (status) {
        "READ" -> "既読"
        "IN_PROGRESS" -> "途中"
        else -> "未読"
    }
}
