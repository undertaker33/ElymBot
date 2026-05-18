package com.elymbot.android.feature.plugin.runtime

typealias PluginPackageValidator = com.elymbot.android.feature.plugin.data.PluginPackageValidator

fun compareVersions(left: String, right: String): Int =
    com.elymbot.android.feature.plugin.data.compareVersions(left, right)

internal fun normalizeArchiveEntryName(entryName: String): String =
    com.elymbot.android.feature.plugin.data.normalizeArchiveEntryName(entryName)
