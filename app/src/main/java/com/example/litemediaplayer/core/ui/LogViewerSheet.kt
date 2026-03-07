package com.example.litemediaplayer.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.litemediaplayer.core.AppLogger

@Composable
fun LogViewerSheet(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) {
        return
    }

    val context = LocalContext.current
    val logs by AppLogger.logs.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("動作ログ (${logs.size}件)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "LiteMedia Logs",
                                AppLogger.export()
                            )
                        )
                    }) {
                        Text("コピー")
                    }
                    Button(onClick = AppLogger::clear) {
                        Text("クリア")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = logs, key = { entry -> "${entry.timestamp}-${entry.tag}-${entry.level}" }) { entry ->
                        val color = when (entry.level) {
                            "E" -> Color.Red
                            "W" -> Color(0xFFFF9800)
                            "I" -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = entry.toString(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 1.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
