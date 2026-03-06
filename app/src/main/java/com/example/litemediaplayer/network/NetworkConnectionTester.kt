package com.example.litemediaplayer.network

import android.util.Base64
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NetworkConnectionTester {
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun testConnection(input: NetworkServerInput): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                when (input.protocol) {
                    Protocol.SMB -> testSmb(input)
                    Protocol.HTTP,
                    Protocol.WEBDAV -> testHttpLike(input)
                }
            }
        }
    }

    private fun testSmb(input: NetworkServerInput): String {
        val client = SMBClient()
        val connection = client.connect(input.host, input.port ?: DEFAULT_SMB_PORT)
        try {
            val session = connection.authenticate(
                AuthenticationContext(
                    input.username.orEmpty(),
                    (input.password ?: "").toCharArray(),
                    ""
                )
            )

            val shareName = input.shareName?.takeIf { it.isNotBlank() }
            if (!shareName.isNullOrBlank()) {
                val share = session.connectShare(shareName)
                share.close()
            }
            session.close()
            return "SMB接続に成功しました"
        } finally {
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }

    private fun testHttpLike(input: NetworkServerInput): String {
        val url = buildHttpUrl(input)
        val requestBuilder = Request.Builder()
            .url(url)
            .head()

        val user = input.username?.takeIf { it.isNotBlank() }
        val pass = input.password?.takeIf { it.isNotBlank() }
        if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
            val token = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            requestBuilder.addHeader("Authorization", "Basic $token")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        response.use {
            if (!it.isSuccessful) {
                throw IOException("HTTPステータス: ${it.code}")
            }
        }

        return when (input.protocol) {
            Protocol.WEBDAV -> "WebDAV接続に成功しました"
            else -> "HTTP接続に成功しました"
        }
    }

    private fun buildHttpUrl(input: NetworkServerInput): String {
        val normalizedPath = input.basePath
            ?.trim()
            ?.trimStart('/')
            ?.takeIf { it.isNotBlank() }
            ?: ""

        val hostPort = if (input.port != null) {
            "${input.host}:${input.port}"
        } else {
            input.host
        }

        return if (normalizedPath.isBlank()) {
            "http://$hostPort/"
        } else {
            "http://$hostPort/$normalizedPath"
        }
    }

    companion object {
        private const val DEFAULT_SMB_PORT = 445
    }
}
