package com.example.litemediaplayer.network

import com.hierynomus.smbj.SMBClient

class SmbClientProvider {
    fun createClient(): SMBClient {
        return SMBClient()
    }
}
