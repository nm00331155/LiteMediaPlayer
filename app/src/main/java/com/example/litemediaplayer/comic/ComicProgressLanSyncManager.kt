package com.example.litemediaplayer.comic

import com.example.litemediaplayer.data.ComicBookDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class ComicSyncPushResult(
    val attemptedCount: Int,
    val successCount: Int,
    val failureCount: Int
)

@Singleton
class ComicProgressLanSyncManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val comicBookDao: ComicBookDao,
    private val syncStore: ComicProgressSyncStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
    private val started = AtomicBoolean(false)

    private val _discoveredDevices = MutableStateFlow<List<ComicSyncDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ComicSyncDevice>> = _discoveredDevices.asStateFlow()

    private val _localEndpoint = MutableStateFlow<ComicSyncLocalEndpoint?>(null)
    val localEndpoint: StateFlow<ComicSyncLocalEndpoint?> = _localEndpoint.asStateFlow()

    @Volatile
    private var syncServerSocket: ServerSocket? = null

    @Volatile
    private var discoveryServerSocket: DatagramSocket? = null

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        scope.launch {
            syncStore.ensureInitialized()
            refreshLocalEndpoint()
            scope.launch { runHttpServerLoop() }
            scope.launch { runDiscoveryResponderLoop() }
        }
    }

    fun stop() {
        started.set(false)
        runCatching { syncServerSocket?.close() }
        runCatching { discoveryServerSocket?.close() }
        syncServerSocket = null
        discoveryServerSocket = null
    }

    suspend fun discoverDevices(timeoutMs: Long = 1_200L): List<ComicSyncDevice> = withContext(Dispatchers.IO) {
        start()
        syncStore.ensureInitialized()
        refreshLocalEndpoint()
        val syncSettings = syncStore.settingsFlow.first()

        val payload = JSONObject().apply {
            put("type", DISCOVERY_REQUEST_TYPE)
            put("requesterId", syncSettings.localDeviceId)
        }.toString().toByteArray(Charsets.UTF_8)

        val discoveredById = linkedMapOf<String, ComicSyncDevice>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 250

            resolveBroadcastAddresses().forEach { address ->
                runCatching {
                    val packet = DatagramPacket(payload, payload.size, address, DEFAULT_DISCOVERY_PORT)
                    socket.send(packet)
                }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val buffer = ByteArray(2_048)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                    continue
                }

                val responseText = runCatching {
                    String(packet.data, 0, packet.length, Charsets.UTF_8)
                }.getOrNull() ?: continue

                val json = runCatching { JSONObject(responseText) }.getOrNull() ?: continue
                if (json.optString("type") != DISCOVERY_RESPONSE_TYPE) {
                    continue
                }

                val deviceId = json.optString("deviceId").trim()
                if (deviceId.isBlank() || deviceId == syncSettings.localDeviceId) {
                    continue
                }

                val name = json.optString("name").trim().ifBlank { "不明な端末" }
                val host = packet.address.hostAddress?.trim().orEmpty()
                val port = json.optInt("port", DEFAULT_HTTP_PORT)
                if (host.isBlank() || port <= 0) {
                    continue
                }

                discoveredById[deviceId] = ComicSyncDevice(
                    deviceId = deviceId,
                    name = name,
                    host = host,
                    port = port
                )
            }
        }

        val result = discoveredById.values.sortedBy { it.name.lowercase() }
        _discoveredDevices.value = result
        result
    }

    suspend fun pushPayloadToRegisteredDevices(payloadText: String): ComicSyncPushResult = withContext(Dispatchers.IO) {
        start()
        syncStore.ensureInitialized()

        val syncSettings = syncStore.settingsFlow.first()
        if (syncSettings.registeredDevices.isEmpty()) {
            return@withContext ComicSyncPushResult(0, 0, 0)
        }

        val discovered = discoverDevices(timeoutMs = 800L).associateBy { it.deviceId }
        val targets = syncSettings.registeredDevices
            .map { registered ->
                val refreshed = discovered[registered.deviceId]
                if (refreshed != null && refreshed != registered) {
                    syncStore.upsertRegisteredDevice(refreshed)
                    refreshed
                } else {
                    refreshed ?: registered
                }
            }
            .distinctBy { it.deviceId }

        var successCount = 0
        var failureCount = 0
        targets.forEach { target ->
            if (postPayload(target, payloadText)) {
                successCount += 1
            } else {
                failureCount += 1
            }
        }

        ComicSyncPushResult(
            attemptedCount = targets.size,
            successCount = successCount,
            failureCount = failureCount
        )
    }

    private suspend fun refreshLocalEndpoint() {
        val settings = syncStore.settingsFlow.first()
        _localEndpoint.value = ComicSyncLocalEndpoint(
            deviceId = settings.localDeviceId,
            deviceName = settings.localDeviceName,
            host = resolveLocalIpv4Address(),
            port = DEFAULT_HTTP_PORT
        )
    }

    private fun runHttpServerLoop() {
        runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(DEFAULT_HTTP_PORT))
            }
        }.onSuccess { serverSocket ->
            syncServerSocket = serverSocket
            while (started.get()) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    break
                }

                scope.launch {
                    handleIncomingHttp(socket)
                }
            }
        }
    }

    private suspend fun handleIncomingHttp(socket: Socket) {
        socket.use { client ->
            val request = runCatching {
                readHttpRequest(client.getInputStream())
            }.getOrNull() ?: return

            val output = client.getOutputStream()
            when {
                request.method == "POST" && request.path == SYNC_PATH -> {
                    val payloadText = request.body.toString(Charsets.UTF_8)
                    val result = runCatching {
                        importComicProgressPayload(comicBookDao, payloadText)
                    }.getOrElse { error ->
                        writeHttpResponse(
                            output = output,
                            statusCode = 400,
                            body = JSONObject()
                                .put("error", error.message ?: "invalid payload")
                                .toString()
                        )
                        return
                    }

                    writeHttpResponse(
                        output = output,
                        statusCode = 200,
                        body = JSONObject().apply {
                            put("updatedCount", result.updatedCount)
                            put("skippedCount", result.skippedCount)
                        }.toString()
                    )
                }

                request.method == "GET" && request.path == PING_PATH -> {
                    writeHttpResponse(
                        output = output,
                        statusCode = 200,
                        body = JSONObject().put("status", "ok").toString()
                    )
                }

                else -> {
                    writeHttpResponse(
                        output = output,
                        statusCode = 404,
                        body = JSONObject().put("error", "not found").toString()
                    )
                }
            }
        }
    }

    private suspend fun postPayload(target: ComicSyncDevice, payloadText: String): Boolean {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://${target.host}:${target.port}$SYNC_PATH")
                .post(payloadText.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrDefault(false)
        }
    }

    private fun runDiscoveryResponderLoop() {
        runCatching {
            DatagramSocket(DEFAULT_DISCOVERY_PORT, InetAddress.getByName("0.0.0.0")).apply {
                broadcast = true
            }
        }.onSuccess { socket ->
            discoveryServerSocket = socket
            val buffer = ByteArray(2_048)
            while (started.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: Exception) {
                    break
                }

                val requestText = runCatching {
                    String(packet.data, 0, packet.length, Charsets.UTF_8)
                }.getOrNull() ?: continue

                val requestJson = runCatching { JSONObject(requestText) }.getOrNull() ?: continue
                if (requestJson.optString("type") != DISCOVERY_REQUEST_TYPE) {
                    continue
                }

                scope.launch {
                    respondToDiscovery(socket, packet.address, packet.port)
                }
            }
        }
    }

    private suspend fun respondToDiscovery(socket: DatagramSocket, address: InetAddress, port: Int) {
        val settings = syncStore.settingsFlow.first()
        val response = JSONObject().apply {
            put("type", DISCOVERY_RESPONSE_TYPE)
            put("deviceId", settings.localDeviceId)
            put("name", settings.localDeviceName)
            put("port", DEFAULT_HTTP_PORT)
        }.toString().toByteArray(Charsets.UTF_8)

        runCatching {
            socket.send(DatagramPacket(response, response.size, address, port))
        }
    }

    private fun resolveBroadcastAddresses(): Set<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { networkInterface ->
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    return@forEach
                }

                networkInterface.interfaceAddresses.forEach { interfaceAddress ->
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast is Inet4Address) {
                        result += broadcast
                    }
                }
            }
        }
        runCatching { result += InetAddress.getByName("255.255.255.255") }
        return result
    }

    private fun resolveLocalIpv4Address(): String? {
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).firstNotNullOfOrNull network@{ networkInterface ->
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    return@network null
                }

                Collections.list(networkInterface.inetAddresses).firstNotNullOfOrNull address@{ address ->
                    val ipv4 = address as? Inet4Address ?: return@address null
                    if (ipv4.isLoopbackAddress) {
                        null
                    } else {
                        ipv4.hostAddress
                    }
                }
            }
        }.getOrNull()
    }

    private data class SimpleHttpRequest(
        val method: String,
        val path: String,
        val body: ByteArray
    )

    private fun readHttpRequest(input: InputStream): SimpleHttpRequest? {
        val headerBuffer = ByteArrayOutputStream()
        var terminatorIndex = 0

        while (terminatorIndex < HEADER_TERMINATOR.size) {
            val value = input.read()
            if (value == -1) {
                return null
            }

            headerBuffer.write(value)
            terminatorIndex = if (value.toByte() == HEADER_TERMINATOR[terminatorIndex]) {
                terminatorIndex + 1
            } else if (value.toByte() == HEADER_TERMINATOR[0]) {
                1
            } else {
                0
            }
        }

        val headerText = headerBuffer.toString(Charsets.ISO_8859_1.name())
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ') ?: return null
        if (requestLine.size < 2) {
            return null
        }

        val headers = lines.drop(1)
            .filter { it.contains(':') }
            .associate { line ->
                val separator = line.indexOf(':')
                line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }

        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val count = input.read(body, offset, contentLength - offset)
            if (count < 0) {
                break
            }
            offset += count
        }

        return SimpleHttpRequest(
            method = requestLine[0].uppercase(),
            path = requestLine[1],
            body = body.copyOf(offset)
        )
    }

    private fun writeHttpResponse(
        output: OutputStream,
        statusCode: Int,
        body: String
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = buildString {
            append("HTTP/1.1 ")
            append(statusCode)
            append(' ')
            append(statusText)
            append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n")
            append("Content-Length: ")
            append(bodyBytes.size)
            append("\r\n")
            append("Connection: close\r\n\r\n")
        }

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bodyBytes)
        output.flush()
    }

    companion object {
        const val DEFAULT_HTTP_PORT = 18_765
        const val DEFAULT_DISCOVERY_PORT = 18_766

        private const val DISCOVERY_REQUEST_TYPE = "comic_progress_discover"
        private const val DISCOVERY_RESPONSE_TYPE = "comic_progress_device"
        private const val SYNC_PATH = "/comic-progress/sync"
        private const val PING_PATH = "/comic-progress/ping"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val HEADER_TERMINATOR = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
    }
}