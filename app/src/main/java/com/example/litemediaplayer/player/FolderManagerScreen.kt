package com.example.litemediaplayer.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderManagerScreen(
    onBack: () -> Unit,
    onOpenLockSettings: () -> Unit,
    viewModel: FolderManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.addFolder(it) }
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current)
        viewModel.consumeMessage()
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("フォルダを削除しますか？") },
            text = { Text(deleteTarget?.displayName.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFolder(deleteTarget!!) }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("フォルダ管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { folderPicker.launch(null) }) {
                Icon(Icons.Default.Add, contentDescription = "フォルダを追加")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "長押しで削除できます。ロック設定は設定タブのグローバルロックを継承します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            items(folders, key = { it.folder.id }) { folderState ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { viewModel.showDeleteConfirm(folderState.folder) }
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderState.folder.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = folderState.folder.treeUri,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.showDeleteConfirm(folderState.folder) }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = if (folderState.isLocked) {
                                        Icons.Default.Lock
                                    } else {
                                        Icons.Default.LockOpen
                                    },
                                    contentDescription = null,
                                    tint = if (folderState.isLocked) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = if (folderState.isLocked) "ロック有効" else "ロック無効",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = "動画 ${folderState.videoCount} 件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Switch(
                                checked = folderState.isLocked,
                                onCheckedChange = { newValue ->
                                    if (!newValue) {
                                        val targetActivity = context as? FragmentActivity
                                        if (targetActivity != null) {
                                            val executor = ContextCompat.getMainExecutor(targetActivity)
                                            val biometricPrompt = BiometricPrompt(
                                                targetActivity,
                                                executor,
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(
                                                        result: BiometricPrompt.AuthenticationResult
                                                    ) {
                                                        viewModel.toggleLock(folderState.folder)
                                                    }

                                                    override fun onAuthenticationError(
                                                        errorCode: Int,
                                                        errString: CharSequence
                                                    ) {
                                                        // 認証キャンセル時は状態を変更しない
                                                    }
                                                }
                                            )
                                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                .setTitle("ロック解除の認証")
                                                .setSubtitle("フォルダのロックを解除するには認証が必要です")
                                                .setAllowedAuthenticators(
                                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                                )
                                                .build()
                                            biometricPrompt.authenticate(promptInfo)
                                        }
                                    } else {
                                        viewModel.toggleLock(folderState.folder)
                                    }
                                }
                            )
                            IconButton(onClick = {
                                viewModel.openLockSettings()
                                onOpenLockSettings()
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "ロック設定")
                            }
                        }
                    }
                }
            }
        }
    }
}
