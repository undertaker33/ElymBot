package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.core.contract.ToolRegistrationOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginPackageStagerTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun stagesSignedReleaseIntoPrivateDirectory() = withPackageFixture { fixture ->
        val declaredPayload = "synthetic dex bytes".toByteArray(StandardCharsets.UTF_8)
        val packageFile = fixture.createPackage(declaredPayload)

        val staged = runBlocking { PluginPackageStager(context).stage(packageFile, fixture.expectation()) }
        try {
            assertEquals(PLUGIN_ID, staged.manifest.pluginId)
            assertEquals(VERSION, staged.manifest.version)
            assertEquals(PUBLISHER_ID, staged.manifest.publisherId)
            assertEquals(SYNTHETIC_ENTRY_CLASS, staged.manifest.entryClass)
            assertTrue(staged.directory.canonicalPath.startsWith(context.filesDir.canonicalPath))
            val pluginJar = File(staged.directory, "payload/plugin.jar")
            assertFalse(pluginJar.canWrite())
            ZipFile(pluginJar).use { jar ->
                assertArrayEquals(declaredPayload, jar.getInputStream(jar.getEntry("classes.dex")).use { it.readBytes() })
            }
            assertTrue(File(staged.directory, "manifest.json").isFile)
            assertTrue(File(staged.directory, "signature.bin").isFile)
        } finally {
            staged.directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsPayloadDigestMismatchAndPreservesActiveVersion() = withPackageFixture { fixture ->
        val declaredPayload = "signed content".toByteArray(StandardCharsets.UTF_8)
        val tamperedPayload = "faked content!".toByteArray(StandardCharsets.UTF_8)
        val packageFile = fixture.createPackage(declaredPayload, actualPayload = tamperedPayload)
        val activeMarker = fixture.createActiveVersionMarker()
        val oldBytes = activeMarker.readBytes()
        try {
            val failure = assertRejected(packageFile, fixture.expectation())

            assertEquals("Package payload integrity check failed", failure.message)
            assertArrayEquals(oldBytes, activeMarker.readBytes())
            assertTrue(activeMarker.exists())
            assertNoStagedPackage()
        } finally {
            activeMarker.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongRequestedIdentityOrVersionBeforeStaging() = withPackageFixture { fixture ->
        val packageFile = fixture.createPackage("plugin".toByteArray(StandardCharsets.UTF_8))

        assertRejected(packageFile, fixture.expectation(pluginId = "other-plugin"))
        assertRejected(packageFile, fixture.expectation(version = "2.0.0"))
        assertNoStagedPackage()
    }

    @Test
    fun rejectsHostApiAndAbiMismatchBeforeStaging() = withPackageFixture { fixture ->
        val packageFile = fixture.createPackage("plugin".toByteArray(StandardCharsets.UTF_8))

        assertRejected(packageFile, fixture.expectation(hostApi = "different-host"))
        assertRejected(packageFile, fixture.expectation(supportedAbis = setOf("x86_64")))
        assertNoStagedPackage()
    }

    @Test
    fun rejectsTamperedManifestSignatureAndUnsafePayloadPath() = withPackageFixture { fixture ->
        val payload = "plugin".toByteArray(StandardCharsets.UTF_8)
        val tamperedSignaturePackage = fixture.createPackage(
            payload = payload,
            signatureTransform = { it.copyOf().apply { this[0] = (this[0].toInt() xor 0x01).toByte() } },
        )
        assertRejected(tamperedSignaturePackage, fixture.expectation())

        val unsafePathPackage = fixture.createPackage(payload = payload, path = "../escape.dex")
        assertRejected(unsafePathPackage, fixture.expectation())
        assertFalse(File(fixture.root, "escape.dex").exists())
        assertNoStagedPackage()
    }

    @Test
    fun loadsRealDexRetriesUnavailableFirstInstallAndRestoresHostIdentity() = withPackageFixture { fixture ->
        System.clearProperty(SYNTHETIC_CLOSED_INSTANCE_PROPERTY)
        System.clearProperty(SYNTHETIC_PREPARED_INSTANCE_PROPERTY)
        System.clearProperty(SYNTHETIC_DATA_DIRECTORY_PROPERTY)
        System.setProperty(SYNTHETIC_PREPARE_ATTEMPTS_PROPERTY, "0")
        System.setProperty(SYNTHETIC_PREPARE_FAIL_PROPERTY, "true")
        val packageFile = fixture.createPackage(readTestDex())
        val manager = PluginRuntimeManager(context)
        var recoveredManager: PluginRuntimeManager? = null

        try {
            runBlocking {
                manager.restore { null }
                val unavailable = manager.install(packageFile, fixture.expectation())
                val originalIdentity = unavailable.installationIdentity
                assertEquals(PluginInstanceAvailability.UNAVAILABLE, unavailable.availability)
                assertTrue(unavailable.enabled)
                assertFalse(manager.isActive(originalIdentity))
                assertTrue(manager.activeRegistrations().isEmpty())
                assertEquals("1", System.getProperty(SYNTHETIC_PREPARE_ATTEMPTS_PROPERTY))
                assertTrue(File(context.filesDir, "plugin-packages/installed/$PLUGIN_ID").listFiles().orEmpty().isNotEmpty())

                System.clearProperty(SYNTHETIC_PREPARE_FAIL_PROPERTY)
                val retried = manager.enable(PLUGIN_ID, fixture.expectation())
                assertEquals(PluginInstanceAvailability.ACTIVE, retried.availability)
                assertEquals(originalIdentity, retried.installationIdentity)
                assertTrue(manager.isActive(originalIdentity))
                assertEquals("2", System.getProperty(SYNTHETIC_PREPARE_ATTEMPTS_PROPERTY))
                assertEquals(originalIdentity.instanceId, System.getProperty(SYNTHETIC_PREPARED_INSTANCE_PROPERTY))

                val registration = manager.activeRegistrations().single()
                assertEquals("synthetic.runtime-tool", registration.source.definition.id)
                assertEquals("synthetic_runtime_tool", registration.source.definition.name)
                assertEquals(ToolRegistrationOwner.Plugin(originalIdentity), registration.owner)
                val expectedDataDirectory = File(context.filesDir, "plugin-packages/data/$PLUGIN_ID").canonicalPath
                assertEquals(expectedDataDirectory, System.getProperty(SYNTHETIC_DATA_DIRECTORY_PROPERTY))

                val disabled = manager.disable(PLUGIN_ID)
                assertEquals(PluginInstanceAvailability.INVALIDATED, disabled.availability)
                assertEquals(originalIdentity.instanceId, System.getProperty(SYNTHETIC_CLOSED_INSTANCE_PROPERTY))
                val reenabled = manager.enable(PLUGIN_ID, fixture.expectation())
                assertTrue(reenabled.installationIdentity.instanceId != originalIdentity.instanceId)
                assertEquals(PluginInstanceAvailability.ACTIVE, reenabled.availability)
                assertEquals("3", System.getProperty(SYNTHETIC_PREPARE_ATTEMPTS_PROPERTY))
                assertEquals(
                    reenabled.installationIdentity.instanceId,
                    System.getProperty(SYNTHETIC_PREPARED_INSTANCE_PROPERTY),
                )

                val invalidUpdate = fixture.createPackage(
                    payload = readTestDex(),
                    version = "2.0.0",
                    entryClass = "com.elymbot.android.runtime.pluginpackage.MissingEntrypoint",
                )
                val updateFailure = runCatching {
                    manager.install(invalidUpdate, fixture.expectation(version = "2.0.0"))
                }.exceptionOrNull()
                assertTrue("Invalid candidate should fail preparation", updateFailure is PluginRuntimeLoadException)
                assertTrue(manager.isActive(reenabled.installationIdentity))
                assertEquals("1.2.3", manager.statusSnapshot().single().version)

                val restored = PluginRuntimeManager(context)
                recoveredManager = restored
                val restoredStatuses = restored.restore { reference ->
                    fixture.expectation(pluginId = reference.pluginId, version = reference.version)
                }
                val restoredStatus = restoredStatuses.single()
                assertEquals(reenabled.installationIdentity, restoredStatus.installationIdentity)
                assertEquals(PluginInstanceAvailability.ACTIVE, restoredStatus.availability)
                assertTrue(restored.isActive(reenabled.installationIdentity))
                assertEquals(
                    ToolRegistrationOwner.Plugin(reenabled.installationIdentity),
                    restored.activeRegistrations().single().owner,
                )
                assertEquals("4", System.getProperty(SYNTHETIC_PREPARE_ATTEMPTS_PROPERTY))
                assertEquals(
                    reenabled.installationIdentity.instanceId,
                    System.getProperty(SYNTHETIC_PREPARED_INSTANCE_PROPERTY),
                )
            }
        } finally {
            System.clearProperty(SYNTHETIC_PREPARE_FAIL_PROPERTY)
            runCatching {
                runBlocking { recoveredManager?.uninstall(PLUGIN_ID, deleteData = true) }
            }
            runCatching {
                runBlocking {
                    if (manager.isRestorationComplete) manager.uninstall(PLUGIN_ID, deleteData = true)
                }
            }
            System.clearProperty(SYNTHETIC_PREPARE_ATTEMPTS_PROPERTY)
            System.clearProperty(SYNTHETIC_DATA_DIRECTORY_PROPERTY)
            System.clearProperty(SYNTHETIC_PREPARED_INSTANCE_PROPERTY)
            System.clearProperty(SYNTHETIC_CLOSED_INSTANCE_PROPERTY)
        }
    }

    private fun assertRejected(
        packageFile: File,
        expectation: PluginPackageExpectation,
    ): PluginPackageException {
        val failure = runCatching {
            runBlocking { PluginPackageStager(context).stage(packageFile, expectation) }
        }.exceptionOrNull()
        assertTrue("Expected an invalid plugin package to be rejected", failure is PluginPackageException)
        return failure as PluginPackageException
    }

    private fun assertNoStagedPackage() {
        val stagingRoot = File(context.filesDir, "plugin-packages/staging")
        assertTrue(stagingRoot.listFiles().orEmpty().isEmpty())
    }

    private fun readTestDex(): ByteArray {
        return InstrumentationRegistry.getInstrumentation().context.assets
            .open(PLUGIN_FIXTURE_DEX_ASSET)
            .use { it.readBytes() }
    }

    private fun withPackageFixture(block: (PackageFixture) -> Unit) {
        val cacheDirectory = context.cacheDir
        Files.createDirectories(cacheDirectory.toPath())
        val root = Files.createTempDirectory(cacheDirectory.toPath(), "t02-plugin-package-").toFile()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val fixture = PackageFixture(root, keyPair)
        try {
            block(fixture)
        } finally {
            root.deleteRecursively()
        }
    }

    private inner class PackageFixture(
        val root: File,
        private val keyPair: KeyPair,
    ) {
        fun expectation(
            pluginId: String = PLUGIN_ID,
            version: String = VERSION,
            hostApi: String = HOST_API,
            supportedAbis: Set<String> = setOf(ABI),
        ) = PluginPackageExpectation(
            publisherId = PUBLISHER_ID,
            pluginId = pluginId,
            version = version,
            hostApi = hostApi,
            supportedAbis = supportedAbis,
            trustedPublisherKey = keyPair.public,
        )

        fun createPackage(
            payload: ByteArray,
            actualPayload: ByteArray = payload,
            path: String = PLUGIN_JAR_PATH,
            signatureTransform: ((ByteArray) -> ByteArray)? = null,
            version: String = VERSION,
            entryClass: String = SYNTHETIC_ENTRY_CLASS,
        ): File {
            val declaredPluginJar = createPluginJar(payload)
            val actualPluginJar = createPluginJar(actualPayload)
            val manifest = manifest(declaredPluginJar, path, version, entryClass)
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(manifest.toByteArray(StandardCharsets.UTF_8))
                sign()
            }
            val packagedSignature = signatureTransform?.invoke(signature) ?: signature
            val packageFile = File(root, "${UUID.randomUUID()}.elymbot-plugin")
            ZipOutputStream(FileOutputStream(packageFile)).use { output ->
                output.writeEntry("manifest.json", manifest.toByteArray(StandardCharsets.UTF_8))
                output.writeEntry("signature.bin", packagedSignature)
                output.writeEntry("payload/$path", actualPluginJar)
            }
            return packageFile
        }

        fun createActiveVersionMarker(): File {
            val active = File(context.filesDir, "plugin-packages/active/test-${UUID.randomUUID()}")
            assertTrue(active.mkdirs())
            return File(active, "version.marker").apply { writeText("old-active-version") }
        }

        private fun manifest(pluginJar: ByteArray, path: String, version: String, entryClass: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(pluginJar).toHex()
            return """
                {
                  "formatVersion": 1,
                  "publisherId": "$PUBLISHER_ID",
                  "pluginId": "$PLUGIN_ID",
                  "version": "$version",
                  "hostApi": "$HOST_API",
                  "abis": ["$ABI"],
                  "entryClass": "$entryClass",
                  "files": [{"path": "$path", "size": ${pluginJar.size}, "sha256": "$digest"}]
                }
            """.trimIndent()
        }

        private fun createPluginJar(dexBytes: ByteArray): ByteArray {
            val outputBytes = ByteArrayOutputStream()
            ZipOutputStream(outputBytes).use { output ->
                val crc = CRC32().apply { update(dexBytes) }
                val entry = ZipEntry("classes.dex").apply {
                    method = ZipEntry.STORED
                    size = dexBytes.size.toLong()
                    compressedSize = dexBytes.size.toLong()
                    this.crc = crc.value
                }
                output.putNextEntry(entry)
                output.write(dexBytes)
                output.closeEntry()
            }
            return outputBytes.toByteArray()
        }

        private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private companion object {
        const val PUBLISHER_ID = "official.example"
        const val PLUGIN_ID = "sample-plugin"
        const val VERSION = "1.2.3"
        const val HOST_API = "host-api-test-1"
        const val ABI = "arm64-v8a"
        const val PLUGIN_JAR_PATH = "plugin.jar"
        const val PLUGIN_FIXTURE_DEX_ASSET = "plugin-fixtures/signed-dex-plugin.dex"
        const val SYNTHETIC_ENTRY_CLASS = "com.elymbot.testfixture.SignedDexPluginEntrypoint"
    }
}

private const val SYNTHETIC_PREPARED_INSTANCE_PROPERTY = "elymbot.test.dex.prepared-instance"
private const val SYNTHETIC_CLOSED_INSTANCE_PROPERTY = "elymbot.test.dex.closed-instance"
private const val SYNTHETIC_DATA_DIRECTORY_PROPERTY = "elymbot.test.dex.data-directory"
private const val SYNTHETIC_PREPARE_ATTEMPTS_PROPERTY = "elymbot.test.dex.prepare-attempts"
private const val SYNTHETIC_PREPARE_FAIL_PROPERTY = "elymbot.test.dex.fail-prepare"
