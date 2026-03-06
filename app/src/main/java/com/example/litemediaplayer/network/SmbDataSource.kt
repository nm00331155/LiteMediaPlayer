package com.example.litemediaplayer.network

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class SmbDataSource(
    private val client: SMBClient,
    private val server: NetworkServer
) : BaseDataSource(/* isNetwork= */ true) {
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var smbFile: File? = null
    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0L
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        val shareName = server.shareName?.takeIf { it.isNotBlank() }
            ?: throw IOException("SMB共有名が設定されていません")
        val path = dataSpec.uri.encodedPath
            ?.removePrefix("/")
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("SMBパスが不正です")

        openedUri = dataSpec.uri

        connection = client.connect(server.host, server.port ?: DEFAULT_SMB_PORT)
        session = connection?.authenticate(
            AuthenticationContext(
                server.username.orEmpty(),
                (server.password ?: "").toCharArray(),
                ""
            )
        )

        val connectedShare = session?.connectShare(shareName)
        if (connectedShare !is DiskShare) {
            throw IOException("SMBディスク共有に接続できません")
        }
        share = connectedShare

        smbFile = connectedShare.openFile(
            path,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        inputStream = smbFile?.inputStream

        val fileSize = smbFile?.fileInformation?.standardInformation?.endOfFile ?: 0L
        if (dataSpec.position > 0L) {
            inputStream?.skip(dataSpec.position)
        }

        bytesRemaining = (fileSize - dataSpec.position).coerceAtLeast(0L)
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = min(length.toLong(), bytesRemaining).toInt()
        val read = inputStream?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT
        if (read == C.RESULT_END_OF_INPUT) {
            return C.RESULT_END_OF_INPUT
        }

        bytesRemaining -= read.toLong()
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? {
        return openedUri
    }

    override fun close() {
        runCatching { inputStream?.close() }
        runCatching { smbFile?.close() }
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }

        inputStream = null
        smbFile = null
        share = null
        session = null
        connection = null

        if (openedUri != null) {
            transferEnded()
        }
        openedUri = null
        bytesRemaining = 0L
    }

    companion object {
        private const val DEFAULT_SMB_PORT = 445
    }
}
