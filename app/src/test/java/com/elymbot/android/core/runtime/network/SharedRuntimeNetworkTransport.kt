package com.elymbot.android.core.runtime.network

import okhttp3.OkHttpClient

internal object SharedRuntimeNetworkTransport {
    @Volatile
    private var instance: RuntimeNetworkTransport = OkHttpRuntimeNetworkTransport()

    fun get(): RuntimeNetworkTransport = instance

    fun sharedBaseClient(): OkHttpClient {
        val transport = instance
        return if (transport is OkHttpRuntimeNetworkTransport) {
            transport.baseClient
        } else {
            OkHttpClient()
        }
    }

    fun setOverrideForTests(transport: RuntimeNetworkTransport?) {
        instance = transport ?: OkHttpRuntimeNetworkTransport()
    }
}
