package com.elymbot.core.contract

import java.io.File

/** Context supplied by the verified host loader while preparing one installed plugin instance. */
data class PluginHostContext(
    val installationIdentity: ToolInstallationIdentity,
    /** Host-owned private data directory, isolated by plugin and stable across code versions. */
    val dataDirectory: File,
)

/** Implemented by the signed plugin entrypoint declared in its verified package. */
fun interface PluginEntrypoint {
    /** Prepare a candidate runtime; preparation failures propagate to the host loader. */
    fun prepare(context: PluginHostContext): PluginHandle
}

/** Prepared plugin resources and tool contributions owned by the host lifecycle. */
interface PluginHandle {
    /** Sources are contributions only; the host binds them to the verified installation identity. */
    val toolSources: List<ToolSource>

    /** Release resources held by the prepared or active plugin instance. */
    fun close()
}
