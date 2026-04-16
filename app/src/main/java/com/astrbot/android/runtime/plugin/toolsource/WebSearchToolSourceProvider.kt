package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolResult
import com.astrbot.android.runtime.plugin.PluginToolResultStatus
import com.astrbot.android.runtime.plugin.PluginToolSourceKind
import com.astrbot.android.runtime.plugin.PluginToolVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchToolSourceProvider : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.WEB_SEARCH

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        if (!configProfile.webSearchEnabled) return emptyList()
        return listOf(buildWebSearchBinding())
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        return if (configProfile.webSearchEnabled) {
            ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        } else {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = false,
                detailCode = "web_search_disabled",
                detailMessage = "Web search is disabled in this config profile.",
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        val toolName = request.args.toolId.substringAfter(":")
        return try {
            val text = when (toolName) {
                "web_search" -> handleWebSearch(request.args.payload)
                else -> throw IllegalArgumentException("Unknown web search tool: $toolName")
            }
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = text,
                ),
            )
        } catch (e: Exception) {
            RuntimeLogRepository.append("WebSearch invoke error: ${e.message}")
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "web_search_error",
                    text = "Web search failed: ${e.message}",
                ),
            )
        }
    }

    private suspend fun handleWebSearch(payload: Map<String, Any?>): String {
        val query = (payload["query"] as? String)?.trim()
            ?: throw IllegalArgumentException("'query' parameter is required.")
        val maxResults = ((payload["max_results"] as? Number)?.toInt() ?: 5).coerceIn(1, 10)

        RuntimeLogRepository.append("WebSearch: searching '$query' (max $maxResults)")
        return executeWebSearchQuery(
            query = query,
            maxResults = maxResults,
            bingSearch = ::searchBing,
            sogouSearch = ::searchSogou,
        )
    }

    private suspend fun searchBing(query: String, maxResults: Int): List<WebSearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.bing.com/search?q=$encoded&count=$maxResults"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IllegalStateException("Empty response from Bing")
                parseBingResults(body, maxResults).also { results ->
                    RuntimeLogRepository.append("WebSearch: engine=bing results=${results.size} query='$query'")
                }
            }
        }
    }

    private suspend fun searchSogou(query: String, maxResults: Int): List<WebSearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.sogou.com/web?query=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IllegalStateException("Empty response from Sogou")
                parseSogouResults(
                    html = body,
                    query = query,
                    maxResults = maxResults,
                    searchUrl = url,
                ).also { results ->
                    RuntimeLogRepository.append("WebSearch: engine=sogou results=${results.size} query='$query'")
                }
            }
        }
    }

    private fun buildWebSearchBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.websearch"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.WEB_SEARCH,
                ownerId = ownerId,
                sourceRef = "web_search",
                displayName = "Web Search",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "web_search",
                description = "Search the web for information. Returns titles, URLs, and snippets of matching results.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.WEB_SEARCH,
                inputSchema = mapOf(
                    "type" to "object" as Any,
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Search query"),
                        "max_results" to mapOf("type" to "integer", "description" to "Max results to return (1-10, default 5)"),
                    ),
                    "required" to listOf("query"),
                ),
            ),
        )
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

internal data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val engine: String = "",
)

private enum class WebSearchEngine {
    BING,
    SOGOU,
}

internal suspend fun executeWebSearchQuery(
    query: String,
    maxResults: Int,
    bingSearch: suspend (String, Int) -> List<WebSearchResult>,
    sogouSearch: suspend (String, Int) -> List<WebSearchResult>,
): String {
    val engineOrder = if (query.containsChineseCharacters()) {
        listOf(WebSearchEngine.SOGOU, WebSearchEngine.BING)
    } else {
        listOf(WebSearchEngine.BING, WebSearchEngine.SOGOU)
    }

    val attempts = mutableListOf<String>()
    for (engine in engineOrder) {
        val results = runCatching {
            when (engine) {
                WebSearchEngine.BING -> bingSearch(query, maxResults)
                WebSearchEngine.SOGOU -> sogouSearch(query, maxResults)
            }
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "WebSearch: engine=${engine.name.lowercase()} failed (${error.message ?: error.javaClass.simpleName}) query='$query'",
            )
        }.getOrDefault(emptyList())
        attempts += "${engine.name.lowercase()}:${results.size}"
        if (results.isNotEmpty()) {
            return formatSearchResults(query, results.take(maxResults))
        }
    }
    throw IllegalStateException("No search results found for query='$query' after ${attempts.joinToString()}.")
}

internal fun parseBingResults(
    html: String,
    maxResults: Int,
): List<WebSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<WebSearchResult>()
    val items = doc.select("li.b_algo")
    for (item in items) {
        if (results.size >= maxResults) break
        val titleEl = item.selectFirst("h2 a") ?: continue
        val title = titleEl.text().trim()
        val href = titleEl.attr("href").trim()
        val snippetEl = item.selectFirst(".b_caption p") ?: item.selectFirst("p")
        val snippet = snippetEl?.text()?.trim()?.take(MAX_SEARCH_SNIPPET_LENGTH).orEmpty()
        if (title.isNotBlank() && href.isNotBlank()) {
            results += WebSearchResult(
                title = title,
                url = href,
                snippet = snippet,
                engine = "bing",
            )
        }
    }
    return results
}

internal fun parseSogouResults(
    html: String,
    query: String,
    maxResults: Int,
    searchUrl: String,
): List<WebSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<WebSearchResult>()

    extractSogouWeatherCard(doc, searchUrl)?.let(results::add)
    val items = doc.select("div.vrwrap, div.rb")
    for (item in items) {
        if (results.size >= maxResults) break
        val titleEl = item.selectFirst("h3 a") ?: continue
        val title = titleEl.text().trim()
        val href = titleEl.attr("href").trim()
        val snippetEl = item.selectFirst("p.str_info, div.str-text-info, p")
        val snippet = snippetEl?.text()?.trim()?.take(MAX_SEARCH_SNIPPET_LENGTH).orEmpty()
        if (title.isNotBlank() && href.isNotBlank()) {
            results += WebSearchResult(
                title = title,
                url = href,
                snippet = snippet,
                engine = "sogou",
            )
        }
    }

    if (results.isNotEmpty()) {
        return results.take(maxResults)
    }

    val fallbackTitle = query.trim().takeIf(String::isNotBlank) ?: return emptyList()
    val fallbackSnippet = doc.selectFirst("title")?.text()?.trim().orEmpty()
    return if (fallbackSnippet.isNotBlank()) {
        listOf(
            WebSearchResult(
                title = fallbackTitle,
                url = searchUrl,
                snippet = fallbackSnippet.take(MAX_SEARCH_SNIPPET_LENGTH),
                engine = "sogou",
            ),
        )
    } else {
        emptyList()
    }
}

private fun extractSogouWeatherCard(
    doc: Document,
    searchUrl: String,
): WebSearchResult? {
    val weatherRoot = doc.selectFirst("div.weather210208, div.weather210208-wrap") ?: return null
    val location = sequenceOf(
        doc.selectFirst(".location-tab li")?.text()?.trim(),
        doc.selectFirst(".location-module li")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val temperature = sequenceOf(
        weatherRoot.selectFirst(".temperature p")?.text()?.trim(),
        weatherRoot.selectFirst(".js_shikuang p")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val condition = sequenceOf(
        weatherRoot.selectFirst(".wind-box .weath")?.text()?.trim(),
        weatherRoot.selectFirst(".detail")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    if (location.isBlank() && temperature.isBlank() && condition.isBlank()) {
        return null
    }

    val title = buildString {
        append(location.ifBlank { "天气" })
        if (!endsWith("天气")) {
            append("天气")
        }
    }
    val snippet = listOf(temperature, condition)
        .filter(String::isNotBlank)
        .joinToString("，")
        .ifBlank { "已提取到天气卡片。" }

    return WebSearchResult(
        title = title,
        url = searchUrl,
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "sogou",
    )
}

private fun formatSearchResults(
    query: String,
    results: List<WebSearchResult>,
): String {
    val arr = JSONArray()
    results.forEach { result ->
        arr.put(
            JSONObject().apply {
                put("engine", result.engine)
                put("title", result.title)
                put("url", result.url)
                put("snippet", result.snippet)
            },
        )
    }
    return JSONObject().apply {
        put("query", query)
        put("count", results.size)
        put("results", arr)
    }.toString(2)
}

private fun String.containsChineseCharacters(): Boolean {
    return any { char -> char.code in 0x4E00..0x9FFF }
}

private const val MAX_SEARCH_SNIPPET_LENGTH = 700
