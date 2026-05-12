package com.astrbot.android.feature.plugin.runtime

typealias PluginPackageValidator = com.astrbot.android.feature.plugin.data.PluginPackageValidator

fun compareVersions(left: String, right: String): Int =
    com.astrbot.android.feature.plugin.data.compareVersions(left, right)

internal fun normalizeArchiveEntryName(entryName: String): String =
    com.astrbot.android.feature.plugin.data.normalizeArchiveEntryName(entryName)
