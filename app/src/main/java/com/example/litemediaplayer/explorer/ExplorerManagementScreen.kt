package com.example.litemediaplayer.explorer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerManagementScreen(
    onOpenNetworkExplorer: () -> Unit,
    viewModel: ExplorerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<ExplorerItemUi?>(null) }
    var renameInput by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ExplorerItemUi?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionState()
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeStatus()
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("名前変更") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("新しい名前") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameItem(target.path, renameInput)
                        renameTarget = null
                    }
                ) {
                    Text("変更")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("削除") },
            text = { Text("「${target.name}」を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(target.path)
                        deleteTarget = null
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.currentPath
                            ?.let { path -> File(path).name.ifBlank { path } }
                            ?: "エクスプローラー"
                    )
                },
                navigationIcon = {
                    if (uiState.currentPath != null) {
                        IconButton(onClick = viewModel::goUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上へ"
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.currentPath != null) {
                        IconButton(onClick = viewModel::goHome) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = "ルート")
                        }
                    }
                    IconButton(onClick = viewModel::refreshCurrentDirectory) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "再読み込み")
                    }
                    if (uiState.currentPath != null && uiState.clipboard != null && uiState.hasAllFilesAccess) {
                        IconButton(onClick = viewModel::pasteIntoCurrentDirectory) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "貼り付け"
                            )
                        }
                    }
                    IconButton(onClick = onOpenNetworkExplorer) {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = "ネットワーク")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        when {
            !uiState.hasAllFilesAccess -> {
                PermissionRequestContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onRequestAccess = {
                        permissionLauncher.launch(buildAllFilesAccessIntent(context))
                    }
                )
            }

            uiState.currentPath == null -> {
                RootListContent(
                    roots = uiState.roots,
                    isLoading = uiState.isLoading,
                    onOpenRoot = viewModel::openRoot,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                val currentPath = uiState.currentPath ?: return@Scaffold
                DirectoryContent(
                    path = currentPath,
                    items = uiState.items,
                    clipboard = uiState.clipboard,
                    isLoading = uiState.isLoading,
                    onOpenDirectory = viewModel::openDirectory,
                    onCopy = viewModel::queueCopy,
                    onMove = viewModel::queueMove,
                    onClearClipboard = viewModel::clearClipboard,
                    onRename = { item ->
                        renameTarget = item
                        renameInput = item.name
                    },
                    onDelete = { deleteTarget = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestContent(
    modifier: Modifier = Modifier,
    onRequestAccess: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "このエクスプローラーは端末内のファイルを直接管理します。Android 11 以降では全ファイルアクセスの許可が必要です。",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onRequestAccess) {
            Text("全ファイルアクセスを許可")
        }
    }
}

@Composable
private fun RootListContent(
    roots: List<ExplorerRootUi>,
    isLoading: Boolean,
    onOpenRoot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "共有ストレージのルートを開き、フォルダやファイルを直接管理できます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(roots, key = { it.path }) { root ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRoot(root.path) }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = root.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = root.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (roots.isEmpty() && !isLoading) {
            item {
                Text(
                    text = "利用可能なストレージが見つかりません。権限状態を確認してください。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DirectoryContent(
    path: String,
    items: List<ExplorerItemUi>,
    clipboard: ExplorerClipboard?,
    isLoading: Boolean,
    onOpenDirectory: (String) -> Unit,
    onCopy: (String) -> Unit,
    onMove: (String) -> Unit,
    onClearClipboard: () -> Unit,
    onRename: (ExplorerItemUi) -> Unit,
    onDelete: (ExplorerItemUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = remember {
        DateFormat.getMediumDateFormat(context)
    }

    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (clipboard != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (clipboard.mode) {
                            ExplorerClipboardMode.COPY -> "コピー待ち: ${File(clipboard.sourcePath).name}"
                            ExplorerClipboardMode.MOVE -> "移動待ち: ${File(clipboard.sourcePath).name}"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearClipboard) {
                        Text("解除")
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.path }) { item ->
                    ExplorerItemRow(
                        item = item,
                        dateLabel = dateFormat.format(Date(item.lastModified)),
                        onOpenDirectory = onOpenDirectory,
                        onCopy = { onCopy(item.path) },
                        onMove = { onMove(item.path) },
                        onRename = { onRename(item) },
                        onDelete = { onDelete(item) }
                    )
                }

                if (items.isEmpty() && !isLoading) {
                    item {
                        Text(
                            text = "このフォルダは空です。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerItemRow(
    item: ExplorerItemUi,
    dateLabel: String,
    onOpenDirectory: (String) -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isDirectory) {
                if (item.isDirectory) {
                    onOpenDirectory(item.path)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.isDirectory) {
                        Icons.Default.Folder
                    } else {
                        Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(dateLabel)
                            if (!item.isDirectory) {
                                append(" | ")
                                append(Formatter.formatShortFileSize(context, item.sizeBytes))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("コピー") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移動") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("名前変更") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("削除") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun buildAllFilesAccessIntent(context: android.content.Context): Intent {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}