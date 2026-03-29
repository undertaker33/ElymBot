package com.astrbot.android.data

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

class NapCatLoginDiagnosticsTest {
    @Test
    fun builds_runtime_diagnostics_with_config_snapshot_and_log_tail() {
        val filesDir = createTempDirectory("napcat-login-diagnostics").toFile()
        val configDir = File(filesDir, "runtime/rootfs/ubuntu/root/napcat/config").apply { mkdirs() }
        val logsDir = File(filesDir, "runtime/logs").apply { mkdirs() }

        File(configDir, "webui.json").writeText(
            """
            {
              "host": "127.0.0.1",
              "port": 6099,
              "token": "astrbot_android_webui"
            }
            """.trimIndent(),
        )
        File(configDir, "onebot11.json").writeText(
            """
            {
              "network": {
                "websocketClients": [
                  {
                    "url": "ws://127.0.0.1:6199/ws",
                    "token": "astrbot_android_bridge"
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        File(logsDir, "napcat.log").writeText(
            """
            first line
            second line
            third line
            """.trimIndent(),
        )

        val lines = NapCatLoginRepository.buildRuntimeDiagnosticsLinesForTests(
            filesDir = filesDir,
            trigger = "refresh",
            detail = "token is invalid",
        )

        assertTrue(lines.any { it.contains("trigger=refresh") && it.contains("detail=token is invalid") })
        assertTrue(lines.any { it.contains("webui.json") && it.contains("astrbot_android_webui") })
        assertTrue(lines.any { it.contains("onebot11.json") && it.contains("astrbot_android_bridge") })
        assertTrue(lines.any { it.contains("napcat.log") && it.contains("third line") })
    }
}
