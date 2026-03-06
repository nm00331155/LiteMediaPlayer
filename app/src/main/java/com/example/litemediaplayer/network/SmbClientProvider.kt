package com.example.litemediaplayer.network

import com.hierynomus.smbj.SMBClient
import javax.inject.Inject

class SmbClientProvider @Inject constructor() {
    fun createClient(): SMBClient {
        return SMBClient()
    }
}
