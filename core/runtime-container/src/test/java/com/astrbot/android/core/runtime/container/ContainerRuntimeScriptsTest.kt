package com.astrbot.android.core.runtime.container

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerRuntimeScriptsTest {

    @Test
    fun command_routes_through_runtime_script_with_shell_and_extra_args() {
        val filesDir = File("build/test-files")
        val command = ContainerRuntimeScripts.command(
            filesDir = filesDir,
            nativeLibraryDir = "/native/libs",
            script = ContainerRuntimeScript.START_NAPCAT,
            extraArgs = listOf("--dry-run"),
        )

        assertEquals(File("/system/bin/sh"), command.executable)
        assertTrue(command.args[0].replace('\\', '/').endsWith("runtime/scripts/start_napcat.sh"))
        assertEquals(filesDir.absolutePath, command.args[1])
        assertEquals("/native/libs", command.args[2])
        assertEquals("--dry-run", command.args[3])
    }
}
