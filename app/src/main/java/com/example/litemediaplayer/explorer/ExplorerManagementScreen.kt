package com.example.litemediaplayer.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerManagementScreen(
    onOpenComicFolders: () -> Unit,
    onOpenPlayerFolders: () -> Unit,
    onOpenNetworkExplorer: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("エクスプローラー") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "コミック、動画、ネットワーク参照をここに統合しました。各画面からは戻る操作でこの一覧に戻れます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExplorerEntryCard(
                title = "コミックフォルダ管理",
                description = "登録済みのコミックフォルダを追加、削除、ロック管理します。",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onOpenComicFolders
            )
            ExplorerEntryCard(
                title = "動画フォルダ管理",
                description = "動画フォルダを即時表示し、件数はバックグラウンドで更新します。",
                icon = Icons.Default.PlayCircle,
                onClick = onOpenPlayerFolders
            )
            ExplorerEntryCard(
                title = "ネットワークエクスプローラー",
                description = "SMB / WebDAV / HTTP の参照、ダウンロード、アップロードを行います。",
                icon = Icons.Default.Wifi,
                onClick = onOpenNetworkExplorer
            )
        }
    }
}

@Composable
private fun ExplorerEntryCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}