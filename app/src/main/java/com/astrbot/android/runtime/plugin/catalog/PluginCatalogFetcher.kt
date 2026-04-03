package com.astrbot.android.runtime.plugin.catalog

import java.net.HttpURLConnection
import java.net.URL

fun interface PluginCatalogFetcher {
    suspend fun fetch(catalogUrl: String): String
}

class UrlConnectionPluginCatalogFetcher : PluginCatalogFetcher {
    override suspend fun fetch(catalogUrl: String): String {
        val connection = (URL(catalogUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }
}
