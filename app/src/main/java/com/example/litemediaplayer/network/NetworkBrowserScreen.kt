package com.example.litemediaplayer.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.litemediaplayer.core.ui.PageSettingsSheet
import com.example.litemediaplayer.settings.AppSettingsStore
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

data class NetworkFile(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isVideo: Boolean
        get() = extension in setOf("mp4", "mkv", "avi", "webm", "mov", "flv", "wmv", "m4v", "3gp")

    val isComic: Boolean
        get() = extension in setOf("zip", "cbz", "cbr", "rar") || isImage

    val isImage: Boolean
        get() = extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    val isArchive: Boolean
        get() = extension in setOf("zip", "rar", "7z", "cbz", "cbr")
}

data class NetworkBrowserUiState(
    val servers: List<NetworkServer> = emptyList(),
    val selectedServer: NetworkServer? = null,
    val currentPath: String = "/",
    val items: List<NetworkFile> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val actionTarget: NetworkFile? = null,
    val statusMessage: String? = null,
    val isTesting: Boolean = false,
    val isLoading: Boolean = false,
    val maxConcurrentDownloads: Int = 2,
    val wifiOnlyDownload: Boolean = false,
    val downloadLocationUri: String? = null,
    val autoAddToLibrary: Boolean = true,
    val downloadQueue: List<NetworkDownloadManager.DownloadTask> = emptyList()
) {
    val selectedCount: Int
        get() = selectedItems.size
}

private data class NetworkBrowserInternalState(
    val selectedServerId: Long? = null,
    val currentPath: String = "/",
    val items: List<NetworkFile> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val actionTarget: NetworkFile? = null,
    val statusMessage: String? = null,
    val isTesting: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class NetworkBrowserViewModel @Inject constructor(
    private val networkServerDao: NetworkServerDao,
    private val appSettingsStore: AppSettingsStore,
    private val smbClientProvider: SmbClientProvider,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val connectionTester = NetworkConnectionTester()
    private val httpClient = OkHttpClient.Builder().build()
    private val downloadManager = NetworkDownloadManager.shared

    private val internalState = MutableStateFlow(NetworkBrowserInternalState())

    init {
        viewModelScope.launch {
            appSettingsStore.settingsFlow.collect { settings ->
                downloadManager.setMaxConcurrent(settings.maxConcurrentDownloads)
            }
        }
    }

    val uiState: StateFlow<NetworkBrowserUiState> = combine(
        networkServerDao.observeAll(),
        appSettingsStore.settingsFlow,
        downloadManager.downloads,
        internalState
    ) { servers, settings, downloads, internal ->
        NetworkBrowserUiState(
            servers = servers,
            selectedServer = servers.firstOrNull { it.id == internal.selectedServerId },
            currentPath = internal.currentPath,
            items = internal.items,
            selectedItems = internal.selectedItems,
            actionTarget = internal.actionTarget,
            statusMessage = internal.statusMessage,
            isTesting = internal.isTesting,
            isLoading = internal.isLoading,
            maxConcurrentDownloads = settings.maxConcurrentDownloads,
            wifiOnlyDownload = settings.wifiOnlyDownload,
            downloadLocationUri = settings.downloadLocationUri,
            autoAddToLibrary = settings.autoAddToLibrary,
            downloadQueue = downloads
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NetworkBrowserUiState()
    )

    fun saveServer(input: NetworkServerInput) {
        viewModelScope.launch(Dispatchers.IO) {
            if (input.name.isBlank() || input.host.isBlank()) {
                postStatus("サーバー名とホストは必須です")
                return@launch
            }

            networkServerDao.upsert(input.toEntity())
            postStatus("サーバーを保存しました")
        }
    }

    fun updateServer(serverId: Long, input: NetworkServerInput) {
        viewModelScope.launch(Dispatchers.IO) {
            if (input.name.isBlank() || input.host.isBlank()) {
                postStatus("サーバー名とホストは必須です")
                return@launch
            }

            networkServerDao.upsert(input.toEntity().copy(id = serverId))
            postStatus("接続先を更新しました")
        }
    }

    fun deleteServer(server: NetworkServer) {
        viewModelScope.launch(Dispatchers.IO) {
            networkServerDao.delete(server)
            if (internalState.value.selectedServerId == server.id) {
                internalState.update {
                    it.copy(
                        selectedServerId = null,
                        currentPath = "/",
                        items = emptyList(),
                        selectedItems = emptySet(),
                        actionTarget = null
                    )
                }
            }
            postStatus("サーバーを削除しました")
        }
    }

    fun testConnection(input: NetworkServerInput) {
        viewModelScope.launch(Dispatchers.IO) {
            if (input.host.isBlank()) {
                postStatus("ホストを入力してください")
                return@launch
            }

            internalState.update { it.copy(isTesting = true) }
            val result = connectionTester.testConnection(input)
            postStatus(
                result.getOrElse { error ->
                    "接続失敗: ${error.message ?: "不明なエラー"}"
                }
            )
            internalState.update { it.copy(isTesting = false) }
        }
    }

    fun connectToServer(server: NetworkServer) {
        internalState.update {
            it.copy(
                selectedServerId = server.id,
                currentPath = "/",
                selectedItems = emptySet(),
                actionTarget = null
            )
        }
        navigateTo("/")
    }

    fun closeServerExplorer() {
        internalState.update {
            it.copy(
                selectedServerId = null,
                currentPath = "/",
                items = emptyList(),
                selectedItems = emptySet(),
                actionTarget = null,
                isLoading = false
            )
        }
    }

    fun canGoUp(): Boolean {
        return internalState.value.currentPath != "/"
    }

    fun navigateUp() {
        val parent = internalState.value.currentPath
            .trimEnd('/')
            .substringBeforeLast('/', "/")
            .ifBlank { "/" }
        navigateTo(parent)
    }

    fun navigateTo(path: String) {
        val server = uiState.value.selectedServer ?: return
        val normalizedPath = normalizePath(path)

        viewModelScope.launch {
            internalState.update {
                it.copy(
                    currentPath = normalizedPath,
                    isLoading = true,
                    selectedItems = emptySet(),
                    actionTarget = null
                )
            }

            val files = runCatching {
                listFiles(server, normalizedPath)
            }.onFailure { error ->
                postStatus("読み込み失敗: ${error.message ?: "不明なエラー"}")
            }.getOrElse { emptyList() }

            internalState.update {
                it.copy(
                    items = files.sortedWith(
                        compareByDescending<NetworkFile> { it.isDirectory }
                            .thenBy { file -> file.name.lowercase() }
                    ),
                    isLoading = false
                )
            }
        }
    }

    fun refresh() {
        navigateTo(internalState.value.currentPath)
    }

    fun toggleSelection(item: NetworkFile) {
        internalState.update { state ->
            val updated = state.selectedItems.toMutableSet()
            if (!updated.add(item.path)) {
                updated.remove(item.path)
            }
            state.copy(selectedItems = updated)
        }
    }

    fun isSelected(item: NetworkFile): Boolean {
        return internalState.value.selectedItems.contains(item.path)
    }

    fun downloadFile(item: NetworkFile) {
        if (item.isDirectory) {
            return
        }

        val server = uiState.value.selectedServer ?: return
        val locationUri = uiState.value.downloadLocationUri
        if (locationUri == null) {
            postStatus("先に設定からダウンロード保存先を指定してください")
            return
        }

        downloadManager.enqueueDownload(
            sourceUri = item.path,
            server = server,
            destinationUri = locationUri.toUri(),
            fileName = item.name,
            totalBytes = item.size
        )
        postStatus("キューに追加: ${item.name}")
    }

    fun downloadSelected() {
        val selectedPaths = internalState.value.selectedItems
        val items = internalState.value.items.filter { it.path in selectedPaths }
        items.forEach { item -> downloadFile(item) }
        internalState.update { it.copy(selectedItems = emptySet()) }
    }

    fun showFileAction(item: NetworkFile) {
        internalState.update { it.copy(actionTarget = item) }
    }

    fun dismissAction() {
        internalState.update { it.copy(actionTarget = null) }
    }

    fun buildStreamUri(item: NetworkFile): String? {
        val server = uiState.value.selectedServer ?: return null
        return when (server.protocolEnum()) {
            Protocol.SMB -> {
                val share = server.shareName?.trim('/').orEmpty()
                val remote = item.path.removePrefix("/")
                "smb://${server.host}/$share/$remote"
            }

            Protocol.HTTP,
            Protocol.WEBDAV -> {
                val scheme = "http"
                val hostPort = if (server.port != null) {
                    "${server.host}:${server.port}"
                } else {
                    server.host
                }
                val base = server.basePath?.trim('/')?.takeIf { it.isNotBlank() }
                val remote = item.path.removePrefix("/")
                if (base.isNullOrBlank()) {
                    "$scheme://$hostPort/$remote"
                } else {
                    "$scheme://$hostPort/$base/$remote"
                }
            }
        }
    }

    fun updateMaxConcurrentDownloads(value: Int) {
        viewModelScope.launch {
            appSettingsStore.updateMaxConcurrentDownloads(value)
            downloadManager.setMaxConcurrent(value)
        }
    }

    fun updateWifiOnlyDownload(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateWifiOnlyDownload(enabled)
        }
    }

    fun updateAutoAddToLibrary(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateAutoAddToLibrary(enabled)
        }
    }

    fun updateDownloadLocation(uri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val uriText = uri?.toString()
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            appSettingsStore.updateDownloadLocationUri(uriText)
        }
    }

    fun clearDownloadLocation() {
        viewModelScope.launch {
            appSettingsStore.updateDownloadLocationUri(null)
        }
    }

    fun consumeStatusMessage() {
        internalState.update { it.copy(statusMessage = null) }
    }

    private suspend fun listFiles(server: NetworkServer, path: String): List<NetworkFile> {
        return when (server.protocolEnum()) {
            Protocol.SMB -> listSmbFiles(server, path)
            Protocol.HTTP -> listHttpFiles(server, path)
            Protocol.WEBDAV -> listWebDavFiles(server, path)
        }
    }

    private suspend fun listSmbFiles(server: NetworkServer, path: String): List<NetworkFile> {
        return withContext(Dispatchers.IO) {
            val shareName = server.shareName?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("SMB共有名を設定してください")

            val client = smbClientProvider.createClient()
            val connection = client.connect(server.host, server.port ?: 445)
            try {
                val session = connection.authenticate(
                    AuthenticationContext(
                        server.username.orEmpty(),
                        (server.password ?: "").toCharArray(),
                        ""
                    )
                )

                session.use {
                    val share = session.connectShare(shareName)
                    if (share !is DiskShare) {
                        throw IllegalStateException("ディスク共有に接続できません")
                    }

                    val normalized = path.removePrefix("/").trim('/')
                    val targetPath = normalized.ifBlank { "" }

                    val entries = share.list(targetPath)
                    entries
                        .filterNot { info -> info.fileName == "." || info.fileName == ".." }
                        .map { info ->
                            val isDir = EnumWithValue.EnumUtils.isSet(
                                info.fileAttributes,
                                FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                            )

                            NetworkFile(
                                name = info.fileName,
                                path = combinePath(path, info.fileName),
                                size = if (isDir) 0L else info.endOfFile,
                                isDirectory = isDir,
                                lastModified = runCatching {
                                    info.changeTime.toEpochMillis()
                                }.getOrDefault(0L)
                            )
                        }
                }
            } finally {
                runCatching { connection.close() }
                runCatching { client.close() }
            }
        }
    }

    private suspend fun listHttpFiles(server: NetworkServer, path: String): List<NetworkFile> {
        return withContext(Dispatchers.IO) {
            val directoryUrl = buildDirectoryUrl(server, path)
            val requestBuilder = Request.Builder()
                .url(directoryUrl)
                .get()
            applyAuthHeader(server, requestBuilder)

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTPステータス: ${response.code}")
                }

                val body = response.body?.string().orEmpty()
                parseHtmlDirectoryListing(body, path)
            }
        }
    }

    private suspend fun listWebDavFiles(server: NetworkServer, path: String): List<NetworkFile> {
        return withContext(Dispatchers.IO) {
            val directoryUrl = buildDirectoryUrl(server, path)
            val requestBody = """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop>
                        <d:displayname />
                        <d:getcontentlength />
                        <d:resourcetype />
                        <d:getlastmodified />
                    </d:prop>
                </d:propfind>
            """.trimIndent().toRequestBody("text/xml; charset=utf-8".toMediaType())

            val requestBuilder = Request.Builder()
                .url(directoryUrl)
                .method("PROPFIND", requestBody)
                .addHeader("Depth", "1")
            applyAuthHeader(server, requestBuilder)

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("WebDAVステータス: ${response.code}")
                }

                val xml = response.body?.string().orEmpty()
                parseWebDavListing(xml, path)
            }
        }
    }

    private fun parseHtmlDirectoryListing(body: String, currentPath: String): List<NetworkFile> {
        val regex = Regex(
            pattern = """<a\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>""",
            options = setOf(RegexOption.IGNORE_CASE)
        )

        return regex.findAll(body)
            .mapNotNull { match ->
                val href = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (href.isBlank() || href.startsWith("#") || href.startsWith("?")) {
                    return@mapNotNull null
                }

                val resolvedPath = resolveHrefToPath(currentPath, href)
                if (resolvedPath == currentPath || resolvedPath == "/" && currentPath == "/") {
                    return@mapNotNull null
                }

                val isDirectory = href.endsWith("/")
                val name = decodeNameFromPath(resolvedPath)
                    .ifBlank { if (isDirectory) "folder" else "file" }
                if (name == "..") {
                    return@mapNotNull null
                }

                NetworkFile(
                    name = name,
                    path = resolvedPath,
                    size = 0L,
                    isDirectory = isDirectory,
                    lastModified = 0L
                )
            }
            .distinctBy { it.path }
            .toList()
    }

    private fun parseWebDavListing(xml: String, currentPath: String): List<NetworkFile> {
        if (xml.isBlank()) {
            return emptyList()
        }

        val docBuilder = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
        val document = docBuilder.parse(xml.byteInputStream())
        val responseNodes = document.getElementsByTagNameNS("*", "response")
        val normalizedCurrentPath = normalizePath(currentPath)

        val files = mutableListOf<NetworkFile>()
        for (index in 0 until responseNodes.length) {
            val node = responseNodes.item(index) as? org.w3c.dom.Element ?: continue
            val href = node.getElementsByTagNameNS("*", "href")
                .item(0)
                ?.textContent
                ?.trim()
                ?: continue

            val hrefPath = normalizePath(Uri.parse(href).path ?: "/")
            if (hrefPath == normalizedCurrentPath) {
                continue
            }

            val isDirectory = node.getElementsByTagNameNS("*", "collection").length > 0
            val displayName = node.getElementsByTagNameNS("*", "displayname")
                .item(0)
                ?.textContent
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: decodeNameFromPath(hrefPath)
            if (displayName == "..") {
                continue
            }

            val size = node.getElementsByTagNameNS("*", "getcontentlength")
                .item(0)
                ?.textContent
                ?.trim()
                ?.toLongOrNull()
                ?: 0L

            files += NetworkFile(
                name = displayName,
                path = hrefPath,
                size = if (isDirectory) 0L else size,
                isDirectory = isDirectory,
                lastModified = 0L
            )
        }

        return files.distinctBy { it.path }
    }

    private fun applyAuthHeader(server: NetworkServer, requestBuilder: Request.Builder) {
        val user = server.username?.takeIf { it.isNotBlank() } ?: return
        val pass = server.password?.takeIf { it.isNotBlank() } ?: return
        val token = Base64.getEncoder()
            .encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
        requestBuilder.addHeader("Authorization", "Basic $token")
    }

    private fun buildDirectoryUrl(server: NetworkServer, path: String): String {
        val scheme = "http"
        val hostPort = if (server.port != null) {
            "${server.host}:${server.port}"
        } else {
            server.host
        }
        val base = server.basePath?.trim('/')?.takeIf { it.isNotBlank() }
        val normalizedPath = path.trim('/').takeIf { it.isNotBlank() }

        val joinedPath = listOfNotNull(base, normalizedPath).joinToString("/")
        val raw = if (joinedPath.isBlank()) {
            "$scheme://$hostPort/"
        } else {
            "$scheme://$hostPort/$joinedPath"
        }

        return if (raw.endsWith("/")) raw else "$raw/"
    }

    private fun resolveHrefToPath(currentPath: String, href: String): String {
        return when {
            href == "../" || href == ".." -> {
                currentPath
                    .trimEnd('/')
                    .substringBeforeLast('/', "/")
                    .ifBlank { "/" }
            }

            href == "./" || href == "." -> normalizePath(currentPath)

            href.startsWith("http://") || href.startsWith("https://") -> {
                normalizePath(Uri.parse(href).path ?: "/")
            }

            href.startsWith("/") -> normalizePath(href)
            else -> normalizePath(combinePath(currentPath, href.trim('/')))
        }
    }

    private fun decodeNameFromPath(path: String): String {
        val raw = path.trimEnd('/').substringAfterLast('/', "")
        return URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
    }

    private fun combinePath(parent: String, child: String): String {
        return if (parent == "/") {
            "/$child"
        } else {
            "$parent/$child"
        }.replace("//", "/")
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank() || path == "/") {
            return "/"
        }
        return "/" + path.trim('/').replace("//", "/")
    }

    private fun postStatus(message: String) {
        internalState.update { it.copy(statusMessage = message) }
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

    private fun NetworkServer.protocolEnum(): Protocol {
        return runCatching { Protocol.valueOf(protocol) }
            .getOrDefault(Protocol.SMB)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkBrowserScreen(
    onPlayVideo: (String) -> Unit = {},
    onOpenComic: (String) -> Unit = {},
    viewModel: NetworkBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDownloadList by rememberSaveable { mutableStateOf(false) }
    var editingServerId by rememberSaveable { mutableStateOf<Long?>(null) }

    var name by rememberSaveable { mutableStateOf("") }
    var protocol by rememberSaveable { mutableStateOf(Protocol.SMB) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var shareName by rememberSaveable { mutableStateOf("") }
    var basePath by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val downloadLocationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.updateDownloadLocation(uri)
    }

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

    PageSettingsSheet(
        visible = showSettings,
        onDismiss = { showSettings = false },
        title = "ネットワーク設定"
    ) {
        NetworkSettingsContent(
            maxConcurrentDownloads = uiState.maxConcurrentDownloads,
            wifiOnlyDownload = uiState.wifiOnlyDownload,
            downloadLocationUri = uiState.downloadLocationUri,
            autoAddToLibrary = uiState.autoAddToLibrary,
            onConcurrentChange = viewModel::updateMaxConcurrentDownloads,
            onWifiOnlyChange = viewModel::updateWifiOnlyDownload,
            onPickLocation = { downloadLocationPicker.launch(null) },
            onClearLocation = viewModel::clearDownloadLocation,
            onAutoAddChange = viewModel::updateAutoAddToLibrary
        )
    }

    val selectedServer = uiState.selectedServer

    Scaffold(
        topBar = {
            if (selectedServer == null) {
                TopAppBar(
                    title = { Text("ネットワーク") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "ネットワーク設定")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = selectedServer.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (viewModel.canGoUp()) {
                                viewModel.navigateUp()
                            } else {
                                viewModel.closeServerExplorer()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "更新")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "ネットワーク設定")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            val activeCount = uiState.downloadQueue.count {
                it.status == NetworkDownloadManager.DownloadStatus.DOWNLOADING
            }
            val pendingCount = uiState.downloadQueue.count {
                it.status == NetworkDownloadManager.DownloadStatus.PENDING
            }
            val totalActive = activeCount + pendingCount

            if (uiState.downloadQueue.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showDownloadList = true },
                    containerColor = if (activeCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Download, contentDescription = "ダウンロード一覧")
                        Text(
                            text = if (activeCount > 0) "↓$activeCount" else "$totalActive",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { viewModel.consumeStatusMessage() }
                )
            }

            if (selectedServer == null) {
                NetworkServerEditor(
                    input = input,
                    isTesting = uiState.isTesting,
                    servers = uiState.servers,
                    editingServerId = editingServerId,
                    onProtocolChange = { protocol = it },
                    onNameChange = { name = it },
                    onHostChange = { host = it },
                    onPortChange = { port = it.filter(Char::isDigit) },
                    onShareNameChange = { shareName = it },
                    onBasePathChange = { basePath = it },
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    onTestConnection = { viewModel.testConnection(input) },
                    onSave = {
                        val targetId = editingServerId
                        if (targetId != null) {
                            viewModel.updateServer(targetId, input)
                        } else {
                            viewModel.saveServer(input)
                        }

                        if (name.isNotBlank() && host.isNotBlank()) {
                            editingServerId = null
                            name = ""
                            host = ""
                            port = ""
                            shareName = ""
                            basePath = ""
                            username = ""
                            password = ""
                        }
                    },
                    onConnect = viewModel::connectToServer,
                    onDelete = viewModel::deleteServer,
                    onEdit = { server ->
                        editingServerId = server.id
                        protocol = runCatching { Protocol.valueOf(server.protocol) }
                            .getOrDefault(Protocol.SMB)
                        name = server.name
                        host = server.host
                        port = server.port?.toString().orEmpty()
                        shareName = server.shareName.orEmpty()
                        basePath = server.basePath.orEmpty()
                        username = server.username.orEmpty()
                        password = server.password.orEmpty()
                    },
                    onCancelEdit = {
                        editingServerId = null
                        name = ""
                        host = ""
                        port = ""
                        shareName = ""
                        basePath = ""
                        username = ""
                        password = ""
                    }
                )

                Text(
                    text = "ダウンロード",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                DownloadListScreen()
            } else {
                BreadcrumbBar(
                    path = uiState.currentPath,
                    onNavigate = viewModel::navigateTo
                )

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "フォルダは空です",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items, key = { it.path }) { item ->
                            NetworkFileItem(
                                item = item,
                                isSelected = uiState.selectedItems.contains(item.path),
                                onTap = {
                                    if (item.isDirectory) {
                                        viewModel.navigateTo(item.path)
                                    } else {
                                        viewModel.showFileAction(item)
                                    }
                                },
                                onLongPress = { viewModel.toggleSelection(item) },
                                onDownload = { viewModel.downloadFile(item) }
                            )
                        }

                        if (uiState.selectedCount > 0) {
                            item {
                                Button(
                                    onClick = viewModel::downloadSelected,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Text("選択中 ${uiState.selectedCount} 件をダウンロード")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadList) {
        AlertDialog(
            onDismissRequest = { showDownloadList = false },
            title = { Text("ダウンロード状況") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    DownloadListScreen()
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadList = false }) {
                    Text("閉じる")
                }
            }
        )
    }

    uiState.actionTarget?.let { file ->
        FileActionDialog(
            file = file,
            onStream = {
                val streamUri = viewModel.buildStreamUri(file)
                if (streamUri != null) {
                    if (file.isVideo) {
                        onPlayVideo(streamUri)
                    } else if (file.isComic) {
                        onOpenComic(streamUri)
                    }
                }
                viewModel.dismissAction()
            },
            onDownload = {
                viewModel.downloadFile(file)
                viewModel.dismissAction()
            },
            onDismiss = viewModel::dismissAction
        )
    }
}

@Composable
private fun NetworkServerEditor(
    input: NetworkServerInput,
    isTesting: Boolean,
    servers: List<NetworkServer>,
    editingServerId: Long?,
    onProtocolChange: (Protocol) -> Unit,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onShareNameChange: (String) -> Unit,
    onBasePathChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onConnect: (NetworkServer) -> Unit,
    onDelete: (NetworkServer) -> Unit,
    onEdit: (NetworkServer) -> Unit,
    onCancelEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (editingServerId != null) "接続先を編集" else "接続先を追加",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = input.name,
                    onValueChange = onNameChange,
                    label = { Text("表示名") },
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Protocol.entries) { candidate ->
                        androidx.compose.material3.FilterChip(
                            selected = input.protocol == candidate,
                            onClick = { onProtocolChange(candidate) },
                            label = { Text(candidate.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = input.host,
                    onValueChange = onHostChange,
                    label = { Text("ホスト名 / IP") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = input.port?.toString() ?: "",
                    onValueChange = onPortChange,
                    label = { Text("ポート (任意)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = input.shareName.orEmpty(),
                    onValueChange = onShareNameChange,
                    label = { Text("共有名 (SMB)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = input.basePath.orEmpty(),
                    onValueChange = onBasePathChange,
                    label = { Text("ベースパス") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = input.username.orEmpty(),
                    onValueChange = onUsernameChange,
                    label = { Text("ユーザー名") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = input.password.orEmpty(),
                    onValueChange = onPasswordChange,
                    label = { Text("パスワード") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTestConnection, enabled = !isTesting) {
                        Text(if (isTesting) "テスト中..." else "接続テスト")
                    }
                    Button(onClick = onSave) {
                        Text(if (editingServerId != null) "更新" else "保存")
                    }
                    if (editingServerId != null) {
                        OutlinedButton(onClick = onCancelEdit) {
                            Text("キャンセル")
                        }
                    }
                }
            }
        }

        Text("登録サーバー", style = MaterialTheme.typography.titleMedium)

        if (servers.isEmpty()) {
            Text("登録されたサーバーはありません")
        } else {
            servers.forEach { server ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(server.name, style = MaterialTheme.typography.titleSmall)
                        Text("${server.protocol}://${server.host}${server.port?.let { ":$it" } ?: ""}")
                        if (!server.shareName.isNullOrBlank()) {
                            Text("共有: ${server.shareName}")
                        }
                        if (!server.basePath.isNullOrBlank()) {
                            Text("パス: ${server.basePath}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConnect(server) }) {
                                Text("接続")
                            }
                            Button(onClick = { onEdit(server) }) {
                                Text("編集")
                            }
                            Button(onClick = { onDelete(server) }) {
                                Text("削除")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    onNavigate: (String) -> Unit
) {
    val segments = remember(path) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        parts.mapIndexed { index, name ->
            val fullPath = "/" + parts.take(index + 1).joinToString("/")
            name to fullPath
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        item {
            Text(
                text = "Root",
                modifier = Modifier.clickable { onNavigate("/") },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(segments) { (name, fullPath) ->
            Text(" / ", style = MaterialTheme.typography.bodySmall)
            Text(
                text = name,
                modifier = Modifier.clickable { onNavigate(fullPath) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NetworkFileItem(
    item: NetworkFile,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDownload: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                item.isDirectory -> Icons.Default.Folder
                item.isVideo -> Icons.Default.VideoFile
                item.isComic -> Icons.Default.Image
                item.isArchive -> Icons.AutoMirrored.Filled.InsertDriveFile
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            },
            contentDescription = null,
            tint = when {
                item.isDirectory -> MaterialTheme.colorScheme.primary
                item.isVideo -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                item.isComic -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!item.isDirectory) {
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!item.isDirectory) {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "ダウンロード")
            }
        }
    }
}

@Composable
private fun FileActionDialog(
    file: NetworkFile,
    onStream: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "サイズ: ${formatFileSize(file.size)}",
                    style = MaterialTheme.typography.bodySmall
                )

                if (file.isVideo || file.isComic) {
                    Button(onClick = onStream, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("ストリーミング")
                    }
                }

                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("ダウンロード")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
private fun NetworkSettingsContent(
    maxConcurrentDownloads: Int,
    wifiOnlyDownload: Boolean,
    downloadLocationUri: String?,
    autoAddToLibrary: Boolean,
    onConcurrentChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onPickLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onAutoAddChange: (Boolean) -> Unit
) {
    Text("同時ダウンロード数: $maxConcurrentDownloads")
    Slider(
        value = maxConcurrentDownloads.toFloat(),
        onValueChange = { onConcurrentChange(it.toInt()) },
        valueRange = 1f..5f,
        steps = 3
    )

    SettingSwitchRow(
        label = "Wi-Fi接続時のみダウンロード",
        checked = wifiOnlyDownload,
        onChange = onWifiOnlyChange
    )

    SettingSwitchRow(
        label = "ダウンロード後にライブラリへ追加",
        checked = autoAddToLibrary,
        onChange = onAutoAddChange
    )

    Text("保存先")
    Text(
        text = downloadLocationUri ?: "未設定",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPickLocation) {
            Text("保存先を選択")
        }
        Button(onClick = onClearLocation) {
            Text("クリア")
        }
    }
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

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) {
        return "0 B"
    }

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    val formatted = if (value >= 100.0 || unitIndex == 0) {
        "%.0f".format(value)
    } else {
        "%.1f".format(value)
    }

    return "$formatted ${units[unitIndex]}"
}
