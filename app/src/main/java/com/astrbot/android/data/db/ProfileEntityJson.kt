package com.astrbot.android.data.db

import org.json.JSONArray

internal fun List<String>.toJsonArrayString(): String {
    return JSONArray().apply {
        forEach(::put)
    }.toString()
}

internal fun Set<String>.toJsonArrayString(): String {
    return toList().toJsonArrayString()
}

internal fun String.parseJsonStringList(): List<String> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return buildList {
        for (index in 0 until array.length()) {
            add(array.opt(index)?.toString().orEmpty())
        }
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
