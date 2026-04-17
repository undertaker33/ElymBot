package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.network.RuntimeNetworkCapability
import com.astrbot.android.runtime.network.RuntimeNetworkRequest
import com.astrbot.android.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.runtime.network.RuntimeTimeoutProfile
import com.astrbot.android.runtime.network.SharedRuntimeNetworkTransport
import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolResult
import com.astrbot.android.runtime.plugin.PluginToolResultStatus
import com.astrbot.android.runtime.plugin.PluginToolSourceKind
import com.astrbot.android.runtime.plugin.PluginToolVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

class WebSearchToolSourceProvider(
    private val transport: RuntimeNetworkTransport = SharedRuntimeNetworkTransport.get(),
) : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.WEB_SEARCH

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
            val request = RuntimeNetworkRequest(
                capability = RuntimeNetworkCapability.WEB_SEARCH,
                method = "GET",
                url = url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                ),
                timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
            )
            val response = transport.execute(request)
            val body = response.bodyString
            if (body.isBlank()) throw IllegalStateException("Empty response from Bing")
            parseBingResults(body, maxResults).also { results ->
                val bingSearchUrl = "https://www.bing.com/search"
                val modules = mutableListOf<String>()
                val richCount = results.count { it.url == bingSearchUrl }
                val algoCount = results.size - richCount
                if (richCount > 0) modules += "rich:$richCount"
                if (algoCount > 0) modules += "b_algo:$algoCount"
                RuntimeLogRepository.append(
                    "WebSearch: engine=bing modules=[${modules.joinToString()}] results=${results.size} query='$query'",
                )
            }
        }
    }

    private suspend fun searchSogou(query: String, maxResults: Int): List<WebSearchResult> {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.sogou.com/web?query=$encoded"
            val request = RuntimeNetworkRequest(
                capability = RuntimeNetworkCapability.WEB_SEARCH,
                method = "GET",
                url = url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                ),
                timeoutProfile = RuntimeTimeoutProfile.WEB_SEARCH,
            )
            val response = transport.execute(request)
            val body = response.bodyString
            if (body.isBlank()) throw IllegalStateException("Empty response from Sogou")
            parseSogouResults(
                html = body,
                maxResults = maxResults,
                searchUrl = url,
            ).also { results ->
                RuntimeLogRepository.append("WebSearch: engine=sogou results=${results.size} query='$query'")
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
    val engineOrder = listOf(WebSearchEngine.BING, WebSearchEngine.SOGOU)

    val attempts = mutableListOf<String>()
    var lowRelevanceFallback: List<WebSearchResult>? = null

    for ((index, engine) in engineOrder.withIndex()) {
        val results = runCatching {
            when (engine) {
                WebSearchEngine.BING -> bingSearch(query, maxResults)
                WebSearchEngine.SOGOU -> sogouSearch(query, maxResults)
            }
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "WebSearch: engine=${engine.name.lowercase()} failed (${error.message ?: error.javaClass.simpleName}) query='$query'",
            )
        }.getOrDefault(emptyList()).filterUsefulSearchResults(query, engine)
        attempts += "${engine.name.lowercase()}:${results.size}"

        if (results.isNotEmpty()) {
            val isLastEngine = index == engineOrder.lastIndex
            val isRelevant = assessBatchRelevance(query, results)
            if (isLastEngine || isRelevant) {
                val payload = formatSearchResults(query, results.take(maxResults))
                RuntimeLogRepository.append("WebSearch: payload query='$query' ${payload.toSingleLineLog()}")
                return payload
            } else {
                lowRelevanceFallback = lowRelevanceFallback ?: results
                val queryBigrams = extractQueryBigrams(query)
                val scores = results.map { r ->
                    val text = "${r.title} ${r.snippet}"
                    val matched = queryBigrams.count { text.contains(it) }
                    "${r.title.take(20)}:$matched/${queryBigrams.size}"
                }
                RuntimeLogRepository.append(
                    "WebSearch: engine=${engine.name.lowercase()} low-relevance for query='$query' scores=[${ scores.joinToString() }], falling back to next engine",
                )
            }
        }
    }

    // Last resort: return low-relevance results rather than failing completely
    lowRelevanceFallback?.let { fallback ->
        val payload = formatSearchResults(query, fallback.take(maxResults))
        RuntimeLogRepository.append("WebSearch: returning low-relevance fallback for query='$query' ${payload.toSingleLineLog()}")
        return payload
    }

    throw IllegalStateException("No search results found for query='$query' after ${attempts.joinToString()}.")
}

internal fun parseBingResults(
    html: String,
    maxResults: Int,
): List<WebSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<WebSearchResult>()

    // Priority 1: Rich results (weather card, answer card)
    extractBingWeatherCard(doc)?.let(results::add)
    if (results.size < maxResults) {
        extractBingAnswerCard(doc)?.let(results::add)
    }
    if (results.size < maxResults) {
        extractBingContextCard(doc)?.let(results::add)
    }

    // Priority 2: Regular b_algo results
    val items = doc.select("li.b_algo, div.b_algo")
    for (item in items) {
        if (results.size >= maxResults) break
        val headerLink = sequenceOf(
            item.selectFirst(".b_algoheader a[href]"),
            item.selectFirst("h2 a[href]"),
            item.selectFirst("a.tilk[href]"),
            item.selectFirst("a[href]"),
        ).firstOrNull()
        val title = sequenceOf(
            item.selectFirst(".b_algoheader h2")?.text()?.trim(),
            item.selectFirst("h2")?.text()?.trim(),
            headerLink?.text()?.trim(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val href = headerLink?.attr("href")?.trim().orEmpty()
        val snippetEl = sequenceOf(
            item.selectFirst(".b_caption p"),
            item.selectFirst(".b_caption .b_lineclamp3"),
            item.selectFirst(".b_caption"),
            item.selectFirst("p"),
        ).firstOrNull()
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

private fun extractBingWeatherCard(doc: Document): WebSearchResult? {
    val weatherContainer = sequenceOf(
        doc.selectFirst("[data-tag^='wea']"),
        doc.selectFirst("#wtr_snc"),
        doc.selectFirst(".wtr_hero"),
        doc.selectFirst("#wtr_card"),
        doc.selectFirst(".b_ans:has(.wtr_currTemp)"),
    ).firstOrNull() ?: return null

    val location = sequenceOf(
        weatherContainer.selectFirst(".wtr_locTitle")?.text()?.trim(),
        weatherContainer.selectFirst(".wtr_loc")?.text()?.trim(),
        doc.selectFirst(".wtr_locTitle")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    val temperature = sequenceOf(
        weatherContainer.selectFirst(".wtr_currTemp")?.text()?.trim(),
        weatherContainer.selectFirst(".wtr-currTemp")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    val condition = sequenceOf(
        weatherContainer.selectFirst(".wtr_condi")?.text()?.trim(),
        weatherContainer.selectFirst(".wtr_cond")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    val forecast = weatherContainer.selectFirst(".wtr_fctxt")?.text()?.trim().orEmpty()

    if (location.isBlank() && temperature.isBlank() && condition.isBlank()) return null

    val weatherSuffix = "\u5929\u6c14"
    val title = buildString {
        append(location.ifBlank { weatherSuffix })
        if (!endsWith(weatherSuffix)) append(weatherSuffix)
    }
    val snippet = listOf(temperature, condition, forecast)
        .filter(String::isNotBlank)
        .joinToString("\uff0c")
        .ifBlank { "\u5df2\u63d0\u53d6\u5230\u5929\u6c14\u5361\u7247\u3002" }

    return WebSearchResult(
        title = title,
        url = "https://www.bing.com/search",
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "bing",
    )
}

private fun extractBingAnswerCard(doc: Document): WebSearchResult? {
    val answerContainers = doc.select("div.b_ans, li.b_ans")
    for (container in answerContainers) {
        // Skip weather cards (handled by extractBingWeatherCard)
        if (container.selectFirst(".wtr_currTemp, .wtr_hero, .wtr_condi, [data-tag^='wea']") != null) continue

        val richContent = container.selectFirst(".b_rich, .b_vPanel, .b_promoteText") ?: continue
        val title = sequenceOf(
            richContent.selectFirst("h2")?.text()?.trim(),
            richContent.selectFirst(".b_entityTitle")?.text()?.trim(),
            container.selectFirst("h2")?.text()?.trim(),
        ).firstOrNull { !it.isNullOrBlank() } ?: continue

        val snippet = sequenceOf(
            richContent.selectFirst(".b_factrow")?.text()?.trim(),
            richContent.selectFirst(".b_paractl")?.text()?.trim(),
            richContent.selectFirst("p")?.text()?.trim(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        if (title.isNotBlank() && snippet.isNotBlank()) {
            return WebSearchResult(
                title = title,
                url = "https://www.bing.com/search",
                snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
                engine = "bing",
            )
        }
    }
    return null
}

private fun extractBingContextCard(doc: Document): WebSearchResult? {
    val contextContainer = sequenceOf(
        doc.selectFirst("#b_context"),
        doc.selectFirst(".b_context"),
    ).firstOrNull() ?: return null

    val title = sequenceOf(
        contextContainer.selectFirst(".b_entityTitle")?.text()?.trim(),
        contextContainer.selectFirst("h2")?.text()?.trim(),
        contextContainer.selectFirst(".b_focusLabel")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() } ?: return null

    val snippet = sequenceOf(
        contextContainer.selectFirst(".b_caption p")?.text()?.trim(),
        contextContainer.selectFirst(".b_paractl")?.text()?.trim(),
        contextContainer.selectFirst(".b_snippet")?.text()?.trim(),
        contextContainer.selectFirst("p")?.text()?.trim(),
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    if (snippet.isBlank()) return null

    return WebSearchResult(
        title = title,
        url = "https://www.bing.com/search",
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "bing",
    )
}

internal fun parseSogouResults(
    html: String,
    maxResults: Int,
    searchUrl: String,
): List<WebSearchResult> {
    val doc = Jsoup.parse(html)
    val results = mutableListOf<WebSearchResult>()

    extractSogouWeatherCardV2(doc, searchUrl)?.let(results::add)
    results += extractSogouSupListResults(html)
    extractSogouAnswerSummaryResult(html, searchUrl)?.let(results::add)

    val items = doc.select("div.vrwrap, div.rb")
    for (item in items) {
        if (results.size >= maxResults) break
        val titleEl = sequenceOf(
            item.selectFirst("h3 a[href]"),
            item.selectFirst(".vr-title a[href]"),
            item.selectFirst("a[href]"),
        ).firstOrNull() ?: continue
        val title = sequenceOf(
            item.selectFirst("h3")?.text()?.trim(),
            item.selectFirst(".vr-title")?.text()?.trim(),
            titleEl.text().trim(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val href = titleEl.attr("href").trim()
        val snippetEl = sequenceOf(
            item.selectFirst("p.str_info"),
            item.selectFirst("div.str-text-info"),
            item.selectFirst(".text-layout"),
            item.selectFirst("p"),
        ).firstOrNull()
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

    return results
        .distinctBy { "${it.engine}|${it.url}|${it.title}" }
        .take(maxResults)
}

private fun extractSogouWeatherCardV2(
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

    val weatherSuffix = "\u5929\u6c14"
    val title = buildString {
        append(location.ifBlank { weatherSuffix })
        if (!endsWith(weatherSuffix)) {
            append(weatherSuffix)
        }
    }
    val snippet = listOf(temperature, condition)
        .filter(String::isNotBlank)
        .joinToString("\uff0c")
        .ifBlank { "\u5df2\u63d0\u53d6\u5230\u5929\u6c14\u5361\u7247\u3002" }

    return WebSearchResult(
        title = title,
        url = searchUrl,
        snippet = snippet.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "sogou",
    )
}

private fun extractSogouSupListResults(html: String): List<WebSearchResult> {
    val arrayText = extractJsonArrayByKey(html, "supList") ?: return emptyList()
    val array = runCatching { JSONArray(arrayText) }.getOrNull() ?: return emptyList()
    val results = mutableListOf<WebSearchResult>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val title = item.optString("sup_title").trim()
        val url = item.optString("sup_url").trim().replace("\\/", "/")
        val snippet = item.optString("sup_passage").trim().take(MAX_SEARCH_SNIPPET_LENGTH)
        val source = item.optString("sup_source").trim()
        val normalizedTitle = when {
            title.isNotBlank() -> title
            source.isNotBlank() -> source
            else -> ""
        }
        if (normalizedTitle.isNotBlank() && url.isNotBlank() && snippet.isNotBlank()) {
            results += WebSearchResult(
                title = normalizedTitle,
                url = url,
                snippet = snippet,
                engine = "sogou",
            )
        }
    }
    return results
}

private fun extractSogouAnswerSummaryResult(
    html: String,
    searchUrl: String,
): WebSearchResult? {
    val escapedSummary = Regex("\"answer_summary\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)
        ?: return null
    val summary = decodeJsonString(escapedSummary).trim()
    if (summary.isBlank()) return null
    return WebSearchResult(
        title = "\u641c\u72d7\u641c\u7d22\u6458\u8981",
        url = searchUrl,
        snippet = summary.take(MAX_SEARCH_SNIPPET_LENGTH),
        engine = "sogou",
    )
}

private fun extractJsonArrayByKey(
    html: String,
    key: String,
): String? {
    val keyIndex = html.indexOf("\"$key\"")
    if (keyIndex < 0) return null
    val arrayStart = html.indexOf('[', keyIndex)
    if (arrayStart < 0) return null

    var index = arrayStart
    var depth = 0
    var inString = false
    var escaping = false
    while (index < html.length) {
        val ch = html[index]
        if (escaping) {
            escaping = false
        } else if (ch == '\\') {
            escaping = true
        } else if (ch == '"') {
            inString = !inString
        } else if (!inString) {
            when (ch) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return html.substring(arrayStart, index + 1)
                    }
                }
            }
        }
        index++
    }
    return null
}

private fun decodeJsonString(value: String): String {
    return runCatching {
        JSONObject("""{"v":"$value"}""").getString("v")
    }.getOrDefault(value)
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

private fun List<WebSearchResult>.filterUsefulSearchResults(
    query: String,
    engine: WebSearchEngine,
): List<WebSearchResult> {
    val filtered = filterNot { result -> result.looksLikeSearchPortalPlaceholder(query, engine) }
    if (filtered.size != size) {
        RuntimeLogRepository.append(
            "WebSearch: filtered ${size - filtered.size} placeholder result(s) for engine=${engine.name.lowercase()} query='$query'",
        )
    }
    return filtered
}

private fun WebSearchResult.looksLikeSearchPortalPlaceholder(
    query: String,
    engine: WebSearchEngine,
): Boolean {
    val normalizedTitle = title.normalizeSearchText()
    val normalizedQuery = query.normalizeSearchText()
    val normalizedSnippet = snippet.normalizeSearchText()
    val normalizedUrl = url.lowercase()

    val titleEqualsQuery = normalizedTitle.isNotBlank() && normalizedTitle == normalizedQuery
    val genericSnippet = normalizedSnippet in setOf(
        "\u0051\u0051\u6d4f\u89c8\u5668\u641c\u7d22".normalizeSearchText(),
        "\u641c\u72d7\u641c\u7d22".normalizeSearchText(),
        "bing",
        "\u641c\u7d22".normalizeSearchText(),
    )
    val searchPortalUrl = when (engine) {
        WebSearchEngine.BING -> normalizedUrl.contains("bing.com/search?")
        WebSearchEngine.SOGOU -> normalizedUrl.contains("sogou.com/web?")
    }

    return searchPortalUrl && (titleEqualsQuery || genericSnippet)
}

private fun String.normalizeSearchText(): String {
    return lowercase()
        .replace(Regex("\\s+"), "")
        .trim()
}

private fun String.toSingleLineLog(maxLength: Int = 4000): String {
    val compact = replace("\r", " ")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (compact.length <= maxLength) {
        compact
    } else {
        compact.take(maxLength) + "...(truncated)"
    }
}

internal fun assessBatchRelevance(
    query: String,
    results: List<WebSearchResult>,
): Boolean {
    if (results.isEmpty()) return false
    // Only assess queries with significant CJK content; Latin bigrams are too noisy
    val cjkCount = query.count { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
    if (cjkCount < 2) return true
    val queryBigrams = extractQueryBigrams(query)
    if (queryBigrams.size < 3) return true // Too short to reliably assess
    return results.any { result ->
        val text = "${result.title} ${result.snippet}"
        val matchCount = queryBigrams.count { bigram -> text.contains(bigram) }
        val ratio = matchCount.toDouble() / queryBigrams.size
        ratio >= RELEVANCE_PER_RESULT_THRESHOLD
    }
}

private fun extractQueryBigrams(query: String): Set<String> {
    val segments = query.split(Regex("\\s+")).filter(String::isNotBlank)
    val bigrams = mutableSetOf<String>()
    for (segment in segments) {
        val cleaned = segment.replace(Regex("[\\p{Punct}]+"), "")
        if (cleaned.length >= 2) {
            for (i in 0 until cleaned.length - 1) {
                bigrams.add(cleaned.substring(i, i + 2))
            }
        }
    }
    return bigrams
}

private const val RELEVANCE_PER_RESULT_THRESHOLD = 0.25

private const val MAX_SEARCH_SNIPPET_LENGTH = 700
