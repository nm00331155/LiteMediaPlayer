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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.litemediaplayer.lock.LockAuthMethod

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var lockExpanded by rememberSaveable { mutableStateOf(true) }
    var memoryExpanded by rememberSaveable { mutableStateOf(false) }
    var generalExpanded by rememberSaveable { mutableStateOf(true) }
    var appInfoExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                    label = {
                        when (it) {
                            AppLanguage.SYSTEM -> "システム"
                            AppLanguage.JA -> "日本語"
                            AppLanguage.EN -> "English"
                        }
                    },
                    onSelect = viewModel::updateLanguage
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
                title = "アプリ情報",
                expanded = appInfoExpanded,
                onToggle = { appInfoExpanded = !appInfoExpanded }
            ) {
                Text(text = "アプリ名: LiteMedia Player")
                Text(text = "バージョン: 1.0")
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
