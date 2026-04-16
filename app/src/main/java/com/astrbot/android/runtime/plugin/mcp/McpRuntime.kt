package com.astrbot.android.runtime.plugin.mcp

import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.runtime.plugin.AllowedValue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class McpDiscoveredTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, AllowedValue>,
)

data class McpToolCallOutput(
    val text: String,
    val structuredContent: Map<String, AllowedValue>?,
    val isError: Boolean,
)

class McpSseClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    suspend fun listTools(server: McpServerEntry): List<McpDiscoveredTool> {
        val result = callJsonRpc(
            server = server,
            method = "tools/list",
            params = JSONObject(),
        )
        val tools = result.optJSONArray("tools") ?: JSONArray()
        return buildList {
            for (index in 0 until tools.length()) {
                val item = tools.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                val schemaObject = item.optJSONObject("inputSchema")
                    ?: item.optJSONObject("input_schema")
                    ?: JSONObject().put("type", "object")
                add(
                    McpDiscoveredTool(
                        name = name,
                        description = item.optString("description").trim(),
                        inputSchema = jsonObjectToAllowedMap(schemaObject),
                    ),
                )
            }
        }
    }

    suspend fun callTool(
        server: McpServerEntry,
        toolName: String,
        arguments: Map<String, AllowedValue>,
    ): McpToolCallOutput {
        val result = callJsonRpc(
            server = server,
            method = "tools/call",
            params = JSONObject().apply {
                put("name", toolName)
                put("arguments", mapToJsonObject(arguments))
            },
        )
        val isError = result.optBoolean("isError", result.optBoolean("is_error", false))
        val content = result.optJSONArray("content") ?: JSONArray()
        val text = buildList {
            for (index in 0 until content.length()) {
                when (val item = content.opt(index)) {
                    is JSONObject -> {
                        val type = item.optString("type").trim()
                        if (type.equals("text", ignoreCase = true)) {
                            item.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    is String -> if (item.isNotBlank()) add(item)
                }
            }
        }.joinToString(separator = "\n").ifBlank { result.toString() }

        val structured = result.optJSONObject("structuredContent")
            ?: result.optJSONObject("structured_content")

        return McpToolCallOutput(
            text = text,
            structuredContent = structured?.let(::jsonObjectToAllowedMap),
            isError = isError,
        )
    }

    private suspend fun callJsonRpc(
        server: McpServerEntry,
        method: String,
        params: JSONObject,
    ): JSONObject = withContext(Dispatchers.IO) {
        val requestPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", UUID.randomUUID().toString())
            put("method", method)
            put("params", params)
        }
        val requestBuilder = Request.Builder()
            .url(server.url)
            .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        server.headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                requestBuilder.header(key, value)
            }
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        response.use { httpResponse ->
            val body = httpResponse.body?.string().orEmpty()
            if (!httpResponse.isSuccessful) {
                error("MCP HTTP ${httpResponse.code}: ${body.take(500)}")
            }
            val root = JSONObject(body)
            val errorObj = root.optJSONObject("error")
            if (errorObj != null) {
                val code = errorObj.optInt("code", -1)
                val message = errorObj.optString("message").ifBlank { "Unknown MCP error" }
                error("MCP RPC error $code: $message")
            }
            root.optJSONObject("result") ?: error("MCP response missing result")
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class McpSessionManager(
    private val client: McpSseClient = McpSseClient(),
    private val ttlMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class ToolCacheEntry(
        val cachedAt: Long,
        val tools: List<McpDiscoveredTool>,
    )

    private val cache = ConcurrentHashMap<String, ToolCacheEntry>()

    suspend fun discoverTools(server: McpServerEntry): List<McpDiscoveredTool> {
        val now = clock()
        val cached = cache[server.serverId]
        if (cached != null && now - cached.cachedAt <= ttlMs) {
            return cached.tools
        }
        val discovered = client.listTools(server)
        cache[server.serverId] = ToolCacheEntry(cachedAt = now, tools = discovered)
        return discovered
    }

    suspend fun callTool(
        server: McpServerEntry,
        toolName: String,
        arguments: Map<String, AllowedValue>,
    ): McpToolCallOutput {
        return client.callTool(server, toolName, arguments)
    }
}

internal fun mapToJsonObject(values: Map<String, *>): JSONObject {
    return JSONObject().apply {
        values.forEach { (key, value) ->
            put(key, toJsonValue(value))
        }
    }
}

private fun toJsonValue(value: Any?): Any {
    return when (value) {
        null -> JSONObject.NULL
        is JSONObject,
        is JSONArray,
        is String,
        is Boolean,
        is Int,
        is Long,
        is Double,
        is Float,
        -> value

        is Number -> value
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (key, nested) ->
                val stringKey = key as? String ?: return@forEach
                put(stringKey, toJsonValue(nested))
            }
        }

        is Iterable<*> -> JSONArray().apply {
            value.forEach { item ->
                put(toJsonValue(item))
            }
        }

        else -> value.toString()
    }
}

internal fun jsonObjectToAllowedMap(obj: JSONObject): Map<String, AllowedValue> {
    val result = linkedMapOf<String, AllowedValue>()
    obj.keys().forEach { key ->
        result[key] = jsonValueToAllowed(obj.opt(key))
    }
    return result
}

private fun jsonValueToAllowed(value: Any?): AllowedValue {
    return when (value) {
        null,
        JSONObject.NULL,
        -> null

        is String,
        is Boolean,
        is Int,
        is Long,
        is Double,
        is Float,
        -> value

        is Number -> value.toDouble()
        is JSONObject -> jsonObjectToAllowedMap(value)
        is JSONArray -> {
            buildList {
                for (index in 0 until value.length()) {
                    add(jsonValueToAllowed(value.opt(index)))
                }
            }
        }

        else -> value.toString()
    }
}
