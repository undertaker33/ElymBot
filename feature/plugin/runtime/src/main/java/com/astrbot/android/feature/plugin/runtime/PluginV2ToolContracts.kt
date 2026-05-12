package com.astrbot.android.feature.plugin.runtime

internal fun buildToolId(pluginId: String, name: String): String {
    return "${pluginId.trim()}:${name.trim()}"
}

internal fun requireToolSchema(schema: JsonLikeMap): JsonLikeMap {
    val normalized = PluginV2ValueSanitizer.requireAllowedMap(schema)
    val type = normalized["type"]
    require(type is String && type.isNotBlank()) {
        "inputSchema.type must be a non-blank string."
    }
    return normalized
}
