package com.example.litemediaplayer.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkServerInput(
    val name: String,
    val protocol: Protocol,
    val host: String,
    val port: Int?,
    val shareName: String?,
    val basePath: String?,
    val username: String?,
    val password: String?
)

data class NetworkBrowserUiState(
    val servers: List<NetworkServer> = emptyList(),
    val statusMessage: String? = null,
    val isTesting: Boolean = false
)

@HiltViewModel
class NetworkBrowserViewModel @Inject constructor(
    private val networkServerDao: NetworkServerDao
) : ViewModel() {
    private val connectionTester = NetworkConnectionTester()
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isTesting = MutableStateFlow(false)

    val uiState: StateFlow<NetworkBrowserUiState> = combine(
        networkServerDao.observeAll(),
        statusMessage,
        isTesting
    ) { servers, status, testing ->
        NetworkBrowserUiState(
            servers = servers,
            statusMessage = status,
            isTesting = testing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NetworkBrowserUiState()
    )

    fun saveServer(input: NetworkServerInput) {
        viewModelScope.launch(Dispatchers.IO) {
            if (input.name.isBlank() || input.host.isBlank()) {
                statusMessage.update { "サーバー名とホストは必須です" }
                return@launch
            }

            networkServerDao.upsert(input.toEntity())
            statusMessage.update { "サーバーを保存しました" }
        }
    }

    fun deleteServer(server: NetworkServer) {
        viewModelScope.launch(Dispatchers.IO) {
            networkServerDao.delete(server)
            statusMessage.update { "サーバーを削除しました" }
        }
    }

    fun testConnection(input: NetworkServerInput) {
        viewModelScope.launch(Dispatchers.IO) {
            if (input.host.isBlank()) {
                statusMessage.update { "ホストを入力してください" }
                return@launch
            }

            isTesting.update { true }
            val result = connectionTester.testConnection(input)
            statusMessage.update {
                result.getOrElse { error -> "接続失敗: ${error.message ?: "不明なエラー"}" }
            }
            isTesting.update { false }
        }
    }

    fun clearStatusMessage() {
        statusMessage.update { null }
    }

    private fun NetworkServerInput.toEntity(): NetworkServer {
        return NetworkServer(
            name = name.trim(),
            protocol = protocol.name,
            host = host.trim(),
            port = port,
            shareName = shareName?.trim()?.takeIf { it.isNotBlank() },
            basePath = basePath?.trim()?.takeIf { it.isNotBlank() },
            username = username?.trim()?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotBlank() }
        )
    }
}

@Composable
fun NetworkBrowserScreen(
    viewModel: NetworkBrowserViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    var name by rememberSaveable { mutableStateOf("") }
    var protocol by rememberSaveable { mutableStateOf(Protocol.SMB) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var shareName by rememberSaveable { mutableStateOf("") }
    var basePath by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val input = NetworkServerInput(
        name = name,
        protocol = protocol,
        host = host,
        port = port.toIntOrNull(),
        shareName = shareName,
        basePath = basePath,
        username = username,
        password = password
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(text = "ローカルネットワーク", style = MaterialTheme.typography.titleLarge)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(text = "表示名") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(text = "プロトコル")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Protocol.entries.forEach { candidate ->
                            FilterChip(
                                selected = protocol == candidate,
                                onClick = { protocol = candidate },
                                label = { Text(text = candidate.name) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(text = "ホスト名 / IP") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit) },
                        label = { Text(text = "ポート (任意)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text(text = "共有名 (SMB)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = basePath,
                        onValueChange = { basePath = it },
                        label = { Text(text = "ベースパス") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(text = "ユーザー名") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = "パスワード") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.testConnection(input) },
                            enabled = !uiState.isTesting
                        ) {
                            Text(text = if (uiState.isTesting) "テスト中..." else "接続テスト")
                        }
                        Button(onClick = {
                            viewModel.saveServer(input)
                            if (input.name.isNotBlank() && input.host.isNotBlank()) {
                                name = ""
                                host = ""
                                port = ""
                                shareName = ""
                                basePath = ""
                                username = ""
                                password = ""
                            }
                        }) {
                            Text(text = "保存")
                        }
                    }
                }
            }
        }

        uiState.statusMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Text(text = "登録サーバー", style = MaterialTheme.typography.titleMedium)
        }

        if (uiState.servers.isEmpty()) {
            item {
                Text(text = "登録されたサーバーはありません")
            }
        } else {
            items(uiState.servers, key = { server -> server.id }) { server ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = server.name, style = MaterialTheme.typography.titleSmall)
                        Text(text = "${server.protocol}://${server.host}${server.port?.let { ":$it" } ?: ""}")
                        if (!server.shareName.isNullOrBlank()) {
                            Text(text = "共有: ${server.shareName}")
                        }
                        if (!server.basePath.isNullOrBlank()) {
                            Text(text = "パス: ${server.basePath}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.deleteServer(server) }) {
                                Text(text = "削除")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(text = "ダウンロード", style = MaterialTheme.typography.titleMedium)
        }

        item {
            DownloadListScreen()
        }
    }
}
