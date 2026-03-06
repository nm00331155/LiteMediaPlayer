package com.example.litemediaplayer.network

import androidx.media3.datasource.DataSource

class SmbDataSourceFactory(
    private val smbClientProvider: SmbClientProvider,
    private val server: NetworkServer
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return SmbDataSource(
            client = smbClientProvider.createClient(),
            server = server
        )
    }
}
