package com.example.litemediaplayer.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.litemediaplayer.comic.PageAnimation
import com.example.litemediaplayer.comic.ReaderMode
import com.example.litemediaplayer.comic.ReadingDirection
import com.example.litemediaplayer.comic.TrimSensitivity
import com.example.litemediaplayer.lock.LockAuthMethod

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    var playerExpanded by rememberSaveable { mutableStateOf(true) }
    var comicExpanded by rememberSaveable { mutableStateOf(false) }
    var lockExpanded by rememberSaveable { mutableStateOf(false) }
    var memoryExpanded by rememberSaveable { mutableStateOf(false) }
    var generalExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard(
                title = "プレイヤー設定",
                expanded = playerExpanded,
                onToggle = { playerExpanded = !playerExpanded }
            ) {
                Text(text = "スワイプシーク間隔")
                OptionChips(
                    options = listOf(5, 10, 15, 30),
                    selected = uiState.appSettings.seekIntervalSeconds,
                    label = { "${it}秒" },
                    onSelect = viewModel::updateSeekInterval
                )

                Text(text = "音量感度")
                OptionChips(
                    options = Sensitivity.entries,
                    selected = uiState.appSettings.volumeSensitivity,
                    label = { sensitivityLabel(it) },
                    onSelect = viewModel::updateVolumeSensitivity
                )

                Text(text = "明るさ感度")
                OptionChips(
                    options = Sensitivity.entries,
                    selected = uiState.appSettings.brightnessSensitivity,
                    label = { sensitivityLabel(it) },
                    onSelect = viewModel::updateBrightnessSensitivity
                )

                Text(text = "デフォルト再生速度: ${"%.2f".format(uiState.appSettings.defaultPlaybackSpeed)}x")
                Slider(
                    value = uiState.appSettings.defaultPlaybackSpeed,
                    onValueChange = viewModel::updateDefaultPlaybackSpeed,
                    valueRange = 0.5f..2f
                )

                Text(text = "プレイヤーの画面回転")
                OptionChips(
                    options = PlayerRotation.entries,
                    selected = uiState.appSettings.playerRotation,
                    label = { playerRotationLabel(it) },
                    onSelect = viewModel::updatePlayerRotation
                )

                Text(text = "動画の表示モード")
                OptionChips(
                    options = PlayerResizeMode.entries,
                    selected = uiState.appSettings.playerResizeMode,
                    label = { playerResizeModeLabel(it) },
                    onSelect = viewModel::updatePlayerResizeMode
                )

                Text(text = "再開時の動作")
                OptionChips(
                    options = ResumeBehavior.entries,
                    selected = uiState.appSettings.resumeBehavior,
                    label = { resumeLabel(it) },
                    onSelect = viewModel::updateResumeBehavior
                )
            }
        }

        item {
            SectionCard(
                title = "コミック設定",
                expanded = comicExpanded,
                onToggle = { comicExpanded = !comicExpanded }
            ) {
                Text(text = "めくり方向")
                OptionChips(
                    options = ReadingDirection.entries,
                    selected = uiState.comicSettings.readingDirection,
                    label = { if (it == ReadingDirection.RTL) "右→左" else "左→右" },
                    onSelect = viewModel::updateComicDirection
                )

                Text(text = "閲覧モード")
                OptionChips(
                    options = ReaderMode.entries,
                    selected = uiState.comicSettings.mode,
                    label = {
                        when (it) {
                            ReaderMode.PAGE -> "ページ"
                            ReaderMode.VERTICAL -> "縦スクロール"
                            ReaderMode.SPREAD -> "見開き"
                        }
                    },
                    onSelect = viewModel::updateComicMode
                )

                Text(text = "めくりアニメーション")
                OptionChips(
                    options = PageAnimation.entries,
                    selected = uiState.comicSettings.animation,
                    label = {
                        when (it) {
                            PageAnimation.SLIDE -> "スライド"
                            PageAnimation.CURL -> "カール"
                            PageAnimation.FADE -> "フェード"
                            PageAnimation.NONE -> "なし"
                        }
                    },
                    onSelect = viewModel::updateComicAnimation
                )

                Text(text = "アニメーション速度: ${uiState.comicSettings.animationSpeedMs}ms")
                Slider(
                    value = uiState.comicSettings.animationSpeedMs.toFloat(),
                    onValueChange = { viewModel.updateComicAnimationSpeed(it.toInt()) },
                    valueRange = 100f..1000f
                )

                Text(text = "ズーム上限: ${"%.1f".format(uiState.comicSettings.zoomMax)}x")
                Slider(
                    value = uiState.comicSettings.zoomMax,
                    onValueChange = viewModel::updateComicZoomMax,
                    valueRange = 2f..5f
                )

                ToggleRow(
                    label = "ブルーライトフィルタ",
                    checked = uiState.comicSettings.blueLightFilterEnabled,
                    onCheckedChange = viewModel::updateComicBlueLightFilter
                )

                ToggleRow(
                    label = "自動分割",
                    checked = uiState.comicSettings.autoSplitEnabled,
                    onCheckedChange = viewModel::updateComicAutoSplit
                )

                Text(text = "見開き判定閾値: ${"%.2f".format(uiState.comicSettings.splitThreshold)}")
                Slider(
                    value = uiState.comicSettings.splitThreshold,
                    onValueChange = viewModel::updateComicSplitThreshold,
                    valueRange = 1f..2f
                )

                ToggleRow(
                    label = "スマート分割線検出",
                    checked = uiState.comicSettings.smartSplitEnabled,
                    onCheckedChange = viewModel::updateComicSmartSplit
                )

                Text(text = "分割位置微調整: ${"%.2f".format(uiState.comicSettings.splitOffsetPercent)}")
                Slider(
                    value = uiState.comicSettings.splitOffsetPercent,
                    onValueChange = viewModel::updateComicSplitOffset,
                    valueRange = -0.05f..0.05f
                )

                ToggleRow(
                    label = "自動トリミング",
                    checked = uiState.comicSettings.autoTrimEnabled,
                    onCheckedChange = viewModel::updateComicAutoTrim
                )

                Text(text = "背景色許容差: ${uiState.comicSettings.trimTolerance}")
                Slider(
                    value = uiState.comicSettings.trimTolerance.toFloat(),
                    onValueChange = { viewModel.updateComicTrimTolerance(it.toInt()) },
                    valueRange = 10f..60f
                )

                Text(text = "安全マージン: ${uiState.comicSettings.trimSafetyMargin}px")
                Slider(
                    value = uiState.comicSettings.trimSafetyMargin.toFloat(),
                    onValueChange = { viewModel.updateComicTrimSafetyMargin(it.toInt()) },
                    valueRange = 0f..20f
                )

                Text(text = "コンテンツ検出感度")
                OptionChips(
                    options = TrimSensitivity.entries,
                    selected = uiState.comicSettings.trimSensitivity,
                    label = {
                        when (it) {
                            TrimSensitivity.LOW -> "低"
                            TrimSensitivity.MEDIUM -> "中"
                            TrimSensitivity.HIGH -> "高"
                        }
                    },
                    onSelect = viewModel::updateComicTrimSensitivity
                )
            }
        }

        item {
            SectionCard(
                title = "ロック設定",
                expanded = lockExpanded,
                onToggle = { lockExpanded = !lockExpanded }
            ) {
                Text(text = "認証方式")
                OptionChips(
                    options = LockAuthMethod.entries,
                    selected = runCatching {
                        LockAuthMethod.valueOf(uiState.appSettings.lockAuthMethod)
                    }.getOrElse { LockAuthMethod.PIN },
                    label = {
                        when (it) {
                            LockAuthMethod.PIN -> "PIN"
                            LockAuthMethod.PATTERN -> "パターン"
                            LockAuthMethod.BIOMETRIC -> "生体"
                        }
                    },
                    onSelect = viewModel::updateLockAuthMethod
                )

                OutlinedTextField(
                    value = uiState.lockSecretDraft,
                    onValueChange = viewModel::updateLockSecretDraft,
                    label = { Text(text = "PIN / パターン") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "自動再ロック時間")
                OptionChips(
                    options = listOf(1, 5, 15, 30, -1),
                    selected = uiState.appSettings.relockTimeoutMinutes,
                    label = { if (it < 0) "無制限" else "${it}分" },
                    onSelect = viewModel::updateRelockTimeout
                )

                ToggleRow(
                    label = "隠しフォルダを表示",
                    checked = uiState.appSettings.hiddenLockContentVisible,
                    onCheckedChange = viewModel::updateHiddenLockVisibility
                )

                Button(onClick = viewModel::saveAppLockConfig) {
                    Text(text = "ロック設定を保存")
                }
            }
        }

        item {
            SectionCard(
                title = "メモリ設定",
                expanded = memoryExpanded,
                onToggle = { memoryExpanded = !memoryExpanded }
            ) {
                Text(text = "メモリ閾値: ${(uiState.appSettings.memoryThreshold * 100).toInt()}%")
                Slider(
                    value = uiState.appSettings.memoryThreshold,
                    onValueChange = viewModel::updateMemoryThreshold,
                    valueRange = 0.5f..0.9f
                )

                Text(text = "クリーンアップ間隔")
                OptionChips(
                    options = listOf(5, 15, 30, 60),
                    selected = uiState.appSettings.cleanupIntervalMinutes,
                    label = { "${it}分" },
                    onSelect = viewModel::updateCleanupInterval
                )

                MemoryDashboard(
                    snapshot = uiState.memorySnapshot,
                    thresholdRatio = uiState.appSettings.memoryThreshold,
                    moduleUsageMb = uiState.memoryModuleUsage,
                    onThresholdChanged = viewModel::updateMemoryThreshold,
                    onManualCleanup = viewModel::runManualCleanup
                )
            }
        }

        item {
            SectionCard(
                title = "一般",
                expanded = generalExpanded,
                onToggle = { generalExpanded = !generalExpanded }
            ) {
                Text(text = "画面回転")
                OptionChips(
                    options = RotationSetting.entries,
                    selected = uiState.appSettings.rotationSetting,
                    label = { it.displayName },
                    onSelect = viewModel::updateRotationSetting
                )

                Text(text = "テーマ")
                OptionChips(
                    options = ThemeMode.entries,
                    selected = uiState.appSettings.themeMode,
                    label = {
                        when (it) {
                            ThemeMode.LIGHT -> "ライト"
                            ThemeMode.DARK -> "ダーク"
                            ThemeMode.SYSTEM -> "システム"
                        }
                    },
                    onSelect = viewModel::updateThemeMode
                )

                Text(text = "言語")
                OptionChips(
                    options = AppLanguage.entries,
                    selected = uiState.appSettings.language,
                    label = { if (it == AppLanguage.JA) "日本語" else "English" },
                    onSelect = viewModel::updateLanguage
                )
            }
        }

        uiState.statusMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Button(onClick = onToggle) {
                    Text(text = if (expanded) "折りたたむ" else "開く")
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
                label = { Text(text = label(option)) }
            )
        }
    }
}

private fun sensitivityLabel(value: Sensitivity): String {
    return when (value) {
        Sensitivity.LOW -> "低"
        Sensitivity.MEDIUM -> "中"
        Sensitivity.HIGH -> "高"
    }
}

private fun playerRotationLabel(value: PlayerRotation): String {
    return when (value) {
        PlayerRotation.FORCE_LANDSCAPE -> "常に横画面"
        PlayerRotation.FOLLOW_GLOBAL -> "一般設定に従う"
    }
}

private fun playerResizeModeLabel(value: PlayerResizeMode): String {
    return when (value) {
        PlayerResizeMode.FIT -> "アスペクト比維持"
        PlayerResizeMode.ZOOM -> "画面いっぱいに拡大"
    }
}

private fun resumeLabel(value: ResumeBehavior): String {
    return when (value) {
        ResumeBehavior.START_FROM_BEGINNING -> "最初から"
        ResumeBehavior.CONTINUE_FROM_LAST -> "前回位置から"
    }
}
