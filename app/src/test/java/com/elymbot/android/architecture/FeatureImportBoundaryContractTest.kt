package com.elymbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureImportBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/elymbot/android")
    private val scannedSegments = setOf("api", "data", "runtime", "presentation", "impl", "domain")
    private val productionSourceRoots: List<Path> = (listOf(mainRoot) + discoverFeatureSourceRoots())
        .filter { root -> root.exists() }
    private val allowlistFile: Path =
        projectRoot.resolve("app/src/test/resources/architecture/feature-import-allowlist.txt")

    private val allowlist: FeatureImportAllowlist = loadAllowlist()

    @Test
    fun allowlist_file_must_exist_and_be_well_formed() {
        assertTrue(
            "Feature import allowlist file must exist: $allowlistFile",
            allowlistFile.exists(),
        )

        val malformedEntries = allowlist.entries.filter { entry ->
            entry.path.isBlank() ||
                entry.forbiddenImport.isBlank() ||
                entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.path.contains("*") ||
                entry.path.contains("\\") ||
                entry.path.endsWith("/") ||
                entry.path.startsWith("/") ||
                !entry.path.endsWith(".kt") ||
                !entry.path.startsWith("feature/") ||
                !entry.forbiddenImport.startsWith("import com.elymbot.android.feature.") ||
                entry.forbiddenImport.contains("*")
        }

        assertTrue(
            "Feature import allowlist contains malformed entries: $malformedEntries",
            malformedEntries.isEmpty(),
        )
    }

    @Test
    fun allowlist_entries_must_point_to_existing_production_files() {
        val missing = allowlist.entries
            .map { entry -> entry.path }
            .distinct()
            .filterNot(::productionFileExists)

        assertTrue(
            "Feature import allowlist points to missing production files: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun allowlist_entries_must_still_match_current_feature_import_debt() {
        val currentDebt = findForbiddenFeatureImports()
            .map { usage -> usage.path to usage.forbiddenImport }
            .toSet()
        val staleEntries = allowlist.entries.filterNot { entry ->
            entry.path to entry.forbiddenImport in currentDebt
        }

        assertTrue(
            "Feature import allowlist has stale entries with no matching current import: $staleEntries",
            staleEntries.isEmpty(),
        )
    }

    @Test
    fun feature_scan_roots_must_cover_all_current_feature_source_roots() {
        val scannedRoots = featureScanRoots()
            .map { scanRoot -> scanRoot.root }
            .toSet()
        val missingRoots = discoverFeatureSourceRoots()
            .filterNot { expectedRoot -> expectedRoot in scannedRoots }
            .map { missingRoot -> projectRoot.relativize(missingRoot).toString().replace('\\', '/') }

        assertTrue(
            "Feature import scan must cover every current feature source root: $missingRoots",
            missingRoots.isEmpty(),
        )
    }

    @Test
    fun feature_modules_must_not_add_unallowlisted_forbidden_feature_imports() {
        val violations = findForbiddenFeatureImports()
            .filterNot { usage -> allowlist.isAllowed(usage.path, usage.forbiddenImport) }

        assertTrue(
            "Feature modules must not add unallowlisted forbidden feature imports. Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun provider_presentation_must_not_import_legacy_provider_runtime_package() {
        val forbiddenImport = forbiddenImportOrNull(
            line = "import com.elymbot.android.feature.provider.runtime.ProviderRuntimePort",
            path = "feature/provider/presentation/src/main/java/com/elymbot/android/feature/provider/presentation/ProviderViewModel.kt",
            sourceFeature = "provider",
            sourceSegment = "presentation",
        )

        assertTrue(
            "Provider presentation must reject imports from the legacy provider runtime package.",
            forbiddenImport != null,
        )
    }

    private fun findForbiddenFeatureImports(): List<ForbiddenFeatureImport> {
        return featureScanRoots().flatMap { scanRoot ->
            kotlinFilesUnder(scanRoot.root).flatMap { file ->
                val relative = relativePath(file)
                file.readLines().mapNotNull { line ->
                    forbiddenImportOrNull(
                        line = line,
                        path = relative,
                        sourceFeature = scanRoot.sourceFeature,
                        sourceSegment = scanRoot.sourceSegment,
                    )
                }
            }
        }
    }

    private fun forbiddenImportOrNull(
        line: String,
        path: String,
        sourceFeature: String,
        sourceSegment: String,
    ): ForbiddenFeatureImport? {
        val trimmed = line.trim()
        val match = featureImportRegex.matchEntire(trimmed) ?: return null
        val targetFeature = match.groupValues[1]
        val targetRemainder = match.groupValues[2]

        val targetSegment = targetRemainder.substringBefore('.')
        val forbidden = when {
            sourceSegment == "api" && targetSegment in forbiddenApiTargetSegments -> true
            sourceSegment == "runtime" && targetSegment == "presentation" -> true
            sourceSegment == "presentation" &&
                targetSegment in forbiddenPresentationTargetSegments &&
                (targetFeature != sourceFeature || sourceFeature == "provider") -> true
            isIntentionalCrossFeatureApiImport(
                sourceFeature = sourceFeature,
                targetFeature = targetFeature,
                targetRemainder = targetRemainder,
            ) -> false
            targetFeature == sourceFeature -> false
            sourceSegment == "domain" -> true
            else -> false
        }
        return if (forbidden) {
            ForbiddenFeatureImport(
                path = path,
                forbiddenImport = trimmed,
                sourceFeature = sourceFeature,
                targetFeature = targetFeature,
            )
        } else {
            null
        }
    }

    private fun loadAllowlist(): FeatureImportAllowlist {
        if (!allowlistFile.exists()) {
            return FeatureImportAllowlist(emptyList())
        }

        val entries = allowlistFile.readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                require(parts.size == 7) {
                    "Invalid feature import allowlist entry at line ${index + 1}: $line"
                }
                FeatureImportAllowlistEntry(
                    path = parts[0],
                    forbiddenImport = parts[1],
                    owner = parts[2],
                    target = parts[3],
                    reason = parts[4],
                    expires = parts[5],
                    issue = parts[6],
                )
            }
            .toList()

        return FeatureImportAllowlist(entries)
    }

    private fun featureScanRoots(): List<FeatureScanRoot> {
        return productionSourceRoots.flatMap { sourceRoot ->
            featureModuleScanRootOrNull(sourceRoot)?.let { scanRoot ->
                return@flatMap listOf(scanRoot)
            }

            val featureRoot = sourceRoot.resolve("feature")
            if (!featureRoot.exists()) {
                emptyList()
            } else {
                Files.list(featureRoot).use { stream ->
                    stream
                        .filter { path -> path.isDirectory() }
                        .flatMap { featureDirectory ->
                            val sourceFeature = featureDirectory.fileName.toString()
                            scannedSegments
                                .map { segment ->
                                    FeatureScanRoot(
                                        root = featureDirectory.resolve(segment),
                                        sourceFeature = sourceFeature,
                                        sourceSegment = segment,
                                    )
                                }
                                .filter { scanRoot -> scanRoot.root.exists() }
                                .stream()
                        }
                        .toList()
                }
            }
        }.distinctBy { scanRoot -> scanRoot.root }
    }

    private fun discoverFeatureSourceRoots(): List<Path> {
        val featureRoot = projectRoot.resolve("feature")
        if (!featureRoot.exists()) {
            return emptyList()
        }

        return Files.list(featureRoot).use { featureStream ->
            featureStream
                .filter { path -> path.isDirectory() }
                .flatMap { featureDirectory ->
                    scannedSegments
                        .map { segment ->
                            featureDirectory.resolve(segment).resolve("src/main/java/com/elymbot/android")
                        }
                        .filter { sourceRoot -> sourceRoot.exists() }
                        .stream()
                }
                .toList()
        }
    }

    private fun featureModuleScanRootOrNull(sourceRoot: Path): FeatureScanRoot? {
        val relativeRoot = projectRoot.relativize(sourceRoot).map { path -> path.toString() }
        val featureIndex = relativeRoot.indexOf("feature")
        if (featureIndex < 0 || featureIndex + 2 >= relativeRoot.size) {
            return null
        }

        val sourceFeature = relativeRoot[featureIndex + 1]
        val sourceSegment = relativeRoot[featureIndex + 2]
        return if (
            sourceSegment in scannedSegments &&
            relativeRoot.drop(featureIndex + 3).joinToString("/") == "src/main/java/com/elymbot/android"
        ) {
            FeatureScanRoot(
                root = sourceRoot,
                sourceFeature = sourceFeature,
                sourceSegment = sourceSegment,
            )
        } else {
            null
        }
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePath(file: Path): String {
        val projectRelative = projectRoot.relativize(file).toString().replace('\\', '/')
        return projectRelative.removePrefix("app/src/main/java/com/elymbot/android/")
    }

    private fun productionFileExists(relativePath: String): Boolean {
        return projectRoot.resolve(relativePath).exists() ||
            mainRoot.resolve(relativePath).exists() ||
            productionSourceRoots.any { sourceRoot -> sourceRoot.resolve(relativePath).exists() }
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/elymbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/elymbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private data class FeatureImportAllowlist(
        val entries: List<FeatureImportAllowlistEntry>,
    ) {
        fun isAllowed(path: String, forbiddenImport: String): Boolean {
            return entries.any { entry -> entry.path == path && entry.forbiddenImport == forbiddenImport }
        }
    }

    private data class FeatureImportAllowlistEntry(
        val path: String,
        val forbiddenImport: String,
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )

    private data class ForbiddenFeatureImport(
        val path: String,
        val forbiddenImport: String,
        val sourceFeature: String,
        val targetFeature: String,
    )

    private data class FeatureScanRoot(
        val root: Path,
        val sourceFeature: String,
        val sourceSegment: String,
    )

    private companion object {
        private val featureImportRegex =
            Regex("""import\s+com\.elymbot\.android\.feature\.([A-Za-z0-9_]+)\.(.+)""")
        private val forbiddenApiTargetSegments = setOf("data", "runtime", "presentation", "impl")
        private val forbiddenPresentationTargetSegments = setOf("data", "runtime", "impl")

        private fun isIntentionalCrossFeatureApiImport(
            sourceFeature: String,
            targetFeature: String,
            targetRemainder: String,
        ): Boolean {
            return sourceFeature == "chat" &&
                targetFeature == "conversation" &&
                targetRemainder.startsWith("domain.")
        }
    }
}
