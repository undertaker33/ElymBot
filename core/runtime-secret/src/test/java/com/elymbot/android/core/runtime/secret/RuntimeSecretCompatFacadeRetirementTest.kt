package com.elymbot.android.core.runtime.secret

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeSecretCompatFacadeRetirementTest {

    @Test
    fun production_secret_module_does_not_keep_static_compat_facades() {
        val sourceRoot = detectProjectRoot().resolve("core/runtime-secret/src/main/java")
        val retiredFacades = listOf(
            "com/elymbot/android/core/runtime/secret/AppSecretStore.kt" to "object AppSecretStore",
            "com/elymbot/android/core/runtime/secret/RuntimeSecretRepository.kt" to "object RuntimeSecretRepository",
        )

        val remainingFacades = retiredFacades.mapNotNull { (relativePath, objectDeclaration) ->
            val sourceFile = sourceRoot.resolve(relativePath)
            if (sourceFile.exists() && sourceFile.readText().contains(objectDeclaration)) {
                relativePath
            } else {
                null
            }
        }

        assertFalse(
            "Runtime secrets production path must use injected RuntimeSecretStore, not static compat facades: $remainingFacades",
            remainingFacades.isNotEmpty(),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return generateSequence(cwd) { path -> path.parent }
            .first { path -> path.resolve("settings.gradle.kts").exists() }
    }
}
