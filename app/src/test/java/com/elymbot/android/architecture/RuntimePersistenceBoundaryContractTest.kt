package com.elymbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePersistenceBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/elymbot/android")
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/runtime-persistence-allowlist.txt")
    private val allowlist: ImportAllowlist = loadAllowlist()

    @Test
    fun allowlist_file_must_exist_and_be_well_formed() {
        assertTrue(
            "Runtime persistence allowlist file must exist: $allowlistFile",
            allowlistFile.exists(),
        )

        val malformedEntries = allowlist.entries.filter { entry ->
            entry.path.isBlank() ||
                entry.importName.isBlank() ||
                entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.path.contains("*") ||
                entry.path.endsWith("/") ||
                entry.path.startsWith("/") ||
                entry.importName.contains("*")
        }

        assertTrue(
            "Runtime persistence allowlist contains malformed entries: $malformedEntries",
            malformedEntries.isEmpty(),
        )
    }

    @Test
    fun allowlist_entries_must_point_to_existing_production_files() {
        val missing = allowlist.entries
            .map { entry -> entry.path }
            .distinct()
            .filterNot { path -> projectRoot.resolve(path).exists() || mainRoot.resolve(path).exists() }

        assertTrue(
            "Runtime persistence allowlist points to missing production files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun runtime_scan_roots_must_cover_current_feature_and_core_runtime_modules() {
        val expectedRoots = listOf(
            "feature/chat/runtime/src/main/java",
            "feature/cron/runtime/src/main/java",
            "feature/plugin/runtime/src/main/java",
            "feature/provider/runtime/src/main/java",
            "feature/qq/runtime/src/main/java",
            "core/runtime-audio/src/main/java",
            "core/runtime-container/src/main/java",
            "core/runtime-context/src/main/java",
            "core/runtime-llm/src/main/java",
            "core/runtime-search/src/main/java",
            "core/runtime-secret/src/main/java",
            "core/runtime-session/src/main/java",
            "core/runtime-tool/src/main/java",
        ).filter { root -> projectRoot.resolve(root).exists() }

        val scannedRoots = runtimeSourceRoots()
            .map { root -> projectRoot.relativize(root).toString().replace('\\', '/') }
            .toSet()
        val missing = expectedRoots.filterNot(scannedRoots::contains)

        assertTrue(
            "Runtime persistence contract must scan current feature/*/runtime and core/runtime-* roots: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun feature_and_core_runtime_must_not_import_database_layer_directly() {
        val violations = runtimeFiles()
            .flatMap { file ->
                val path = relativePath(file)
                databaseImports(file).mapNotNull { importName ->
                    if (allowlist.isAllowed(path, importName)) {
                        null
                    } else {
                        "$path imports $importName"
                    }
                }
            }

        assertTrue(
            "feature/*/runtime/** and core/runtime/** must not import com.elymbot.android.data.db.* directly. " +
                "Add only precise temporary allowlist entries for existing debt: $violations",
            violations.isEmpty(),
        )
    }

    private fun runtimeFiles(): List<Path> {
        return runtimeSourceRoots().flatMap { root ->
            Files.walk(root).use { stream ->
                stream
                    .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                    .toList()
            }
        }
    }

    private fun runtimeSourceRoots(): List<Path> {
        val featureRuntimeRoots = projectRoot.resolve("feature").takeIf { it.exists() }
            ?.let { featureRoot ->
                Files.list(featureRoot).use { stream ->
                    stream
                        .filter { path -> Files.isDirectory(path) }
                        .map { path -> path.resolve("runtime/src/main/java") }
                        .filter { path -> path.exists() }
                        .toList()
                }
            }
            .orEmpty()
        val coreRuntimeRoots = projectRoot.resolve("core").takeIf { it.exists() }
            ?.let { coreRoot ->
                Files.list(coreRoot).use { stream ->
                    stream
                        .filter { path -> Files.isDirectory(path) }
                        .filter { path ->
                            val moduleName = path.fileName.toString()
                            moduleName == "runtime" || moduleName.startsWith("runtime-")
                        }
                        .map { path -> path.resolve("src/main/java") }
                        .filter { path -> path.exists() }
                        .toList()
                }
            }
            .orEmpty()

        return (featureRuntimeRoots + coreRuntimeRoots).distinct()
    }

    private fun databaseImports(file: Path): List<String> {
        return file.readLines()
            .asSequence()
            .map { line -> importRegex.matchEntire(line.trim())?.groupValues?.get(1) }
            .filterNotNull()
            .filter { importName -> importName == "com.elymbot.android.data.db" || importName.startsWith("com.elymbot.android.data.db.") }
            .toList()
    }

    private fun loadAllowlist(): ImportAllowlist {
        if (!allowlistFile.exists()) {
            return ImportAllowlist(emptyList())
        }

        val entries = allowlistFile.readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == 7) {
                    "Invalid runtime persistence allowlist entry at line ${index + 1}: $line"
                }
                ImportAllowlistEntry(
                    path = parts[0],
                    importName = parts[1],
                    owner = parts[2],
                    target = parts[3],
                    reason = parts[4],
                    expires = parts[5],
                    issue = parts[6],
                )
            }
            .toList()

        return ImportAllowlist(entries)
    }

    private fun relativePath(file: Path): String {
        val projectRelative = projectRoot.relativize(file).toString().replace('\\', '/')
        return projectRelative.removePrefix("app/src/main/java/com/elymbot/android/")
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/elymbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/elymbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private data class ImportAllowlist(
        val entries: List<ImportAllowlistEntry>,
    ) {
        fun isAllowed(path: String, importName: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.importName == importName }
        }
    }

    private data class ImportAllowlistEntry(
        val path: String,
        val importName: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )

    private companion object {
        private val importRegex = Regex("""^import\s+([A-Za-z0-9_.*]+)$""")
    }
}
