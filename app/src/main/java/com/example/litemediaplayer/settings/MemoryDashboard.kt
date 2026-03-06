package com.example.litemediaplayer.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.litemediaplayer.core.memory.MemorySnapshot

@Composable
fun MemoryDashboard(
    snapshot: MemorySnapshot,
    thresholdRatio: Float,
    moduleUsageMb: Map<String, Long>,
    onThresholdChanged: (Float) -> Unit,
    onManualCleanup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "メモリ使用量: ${snapshot.usedMb}MB / ${snapshot.maxMb}MB",
            style = MaterialTheme.typography.titleMedium
        )

        LinearProgressIndicator(
            progress = { snapshot.usageRatio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "自動クリーンアップ閾値: ${(thresholdRatio * 100).toInt()}%")
        Slider(
            value = thresholdRatio,
            valueRange = 0.5f..0.9f,
            onValueChange = onThresholdChanged
        )

        Text(text = "モジュール別概算")
        moduleUsageMb.forEach { (name, usageMb) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$name: ${usageMb}MB",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(onClick = onManualCleanup) {
            Text(text = "手動クリーンアップ")
        }
    }
}
