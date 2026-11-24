package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elymbot.android.ui.PluginManagementActionResult
import com.elymbot.android.ui.PluginReleaseReference
import com.elymbot.android.ui.PluginRuntimeAvailability
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialPluginDistributionRepositoryTest {
    @Test
    fun appManagementInstallsAndReenablesFromThePublishedScheduledTaskPackage() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("t02ScheduleUiProbe") == "true")
        val probe = File(targetContext().cacheDir, "t02-scheduled-ui-probe")
        val packageBytes = File(probe, "scheduled-tasks-ui-probe-final.elymbot-plugin").readBytes()
        val publisherKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(File(probe, "final-probe-publisher-public.der").readBytes()),
        )
        val reference = PluginReleaseReference("scheduled-tasks", "synthetic.elymbot.tests", "0.0.2-ui-probe")
        val hostApi = "host-api-test-1"
        isolatedContext().use { context ->
            LocalHttpServer().use { server ->
                val rootKey = newKeyPair()
                val configuration = configuration(rootKey, server.port).copy(hostApi = hostApi)
                val published = PluginDefinition(
                    version = reference.version,
                    publisherKey = publisherKey,
                    packageBytes = packageBytes,
                    githubUrl = "http://localhost:${server.port}/tasks",
                    giteeUrl = null,
                    pluginId = reference.pluginId,
                    publisherId = reference.publisherId,
                    hostApi = hostApi,
                )
                val directory = signedDirectory(rootKey, listOf(published))
                server.respond("/latest", 200, releaseApi(server.port, directory))
                server.respond("/directory", 200, directory.bytes)
                server.respond("/signature", 200, directory.signature)
                server.respond("/tasks", 200, packageBytes)

                // Drive the public actions explicitly; the test uses only this local directory transport.
                val manualScope = CoroutineScope(Job().apply { cancel() } + Dispatchers.IO)
                val manager = PluginRuntimeManager(context)
                val service = AppHostManagementUiService(
                    context,
                    manager,
                    OfficialPluginDistributionConfigurationLoad.Ready(configuration),
                    OfficialPluginDistributionRepository(context, configuration),
                    OfficialAppUpdateService(context),
                    manualScope,
                )
                service.restorePlugins()
                service.refreshPluginDirectory()
                assertEquals(reference, service.pluginManagement.value.releases.single().reference)
                assertTrue(service.pluginManagement.value.releases.single().compatible)
                assertEquals(PluginManagementActionResult.Completed, service.installPlugin(reference))
                assertEquals(PluginRuntimeAvailability.Active, service.pluginManagement.value.installed.single().availability)
                assertEquals(1, server.requestCount("/tasks"))

                assertEquals(PluginManagementActionResult.Completed, service.disablePlugin(reference.pluginId))
                assertEquals(PluginRuntimeAvailability.Disabled, service.pluginManagement.value.installed.single().availability)
                assertEquals(PluginManagementActionResult.Completed, service.enablePlugin(reference.pluginId))
                assertEquals(PluginRuntimeAvailability.Active, service.pluginManagement.value.installed.single().availability)
                assertEquals(1, server.requestCount("/tasks"))
                assertEquals(PluginManagementActionResult.Completed, service.uninstallPlugin(reference.pluginId, deleteData = true))
                assertTrue(service.pluginManagement.value.installed.isEmpty())
            }
        }
    }

    @Test
    fun missingTrustConfigurationIsObservableAndDoesNotFetch() = runBlocking {
        val context = isolatedContext()
        try {
            val repository = OfficialPluginDistributionRepository(context, configuration = null)
            val snapshot = repository.refreshPluginDirectory()
            assertEquals(PluginDirectoryStatus.NOT_CONFIGURED, snapshot.status)
            assertNull(repository.resolveExpectation(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.0.0")))
        } finally {
            context.close()
        }
    }

    @Test
    fun signedDirectoryResolvesHistoryAndLastGoodCacheSurvivesNetworkFailure() = runBlocking {
        val context = isolatedContext()
        val rootKey = newKeyPair()
        val publisherKey = newKeyPair()
        val packageBytes = "synthetic signed plugin bytes".toByteArray(UTF_8)
        val older = release("1.0.0", publisherKey, packageBytes, "http://localhost:1/plugin-old")
        val newer = release("1.1.0", publisherKey, packageBytes, "http://localhost:1/plugin-new")
        val directory = signedDirectory(rootKey, listOf(older, newer))
        val server = LocalHttpServer()
        server.respond("/latest", 200, releaseApi(server.port, directory))
        server.respond("/directory", 200, directory.bytes)
        server.respond("/signature", 200, directory.signature)
        val configuration = configuration(rootKey, server.port)

        try {
            val first = OfficialPluginDistributionRepository(context, configuration)
            val firstSnapshot = first.refreshPluginDirectory()
            assertEquals("snapshot=$firstSnapshot", PluginDirectoryStatus.READY, firstSnapshot.status)
            assertNotNull(first.resolveExpectation(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.0.0")))
            assertNotNull(first.resolveExpectation(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.1.0")))
            server.close()

            val offline = OfficialPluginDistributionRepository(context, configuration)
            val snapshot = offline.refreshPluginDirectory()
            assertEquals(PluginDirectoryStatus.NETWORK_ERROR, snapshot.status)
            assertTrue(snapshot.usingLastGoodDirectory)
            assertNotNull(offline.resolveExpectation(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.0.0")))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun giteeIsUsedOnlyAfterGithubTransferFailureAndMustMatchSignedDigest() = runBlocking {
        val context = isolatedContext()
        val rootKey = newKeyPair()
        val publisherKey = newKeyPair()
        val packageBytes = "exact package bytes".toByteArray(UTF_8)
        val server = LocalHttpServer()
        val definition = release(
            version = "1.0.0",
            publisherKey = publisherKey,
            packageBytes = packageBytes,
            githubUrl = "http://localhost:${server.port}/primary",
            giteeUrl = "http://localhost:${server.port}/mirror",
        )
        val signed = signedDirectory(rootKey, listOf(definition))
        server.respond("/latest", 200, releaseApi(server.port, signed))
        server.respond("/directory", 200, signed.bytes)
        server.respond("/signature", 200, signed.signature)
        server.respond("/primary", 503, "temporarily unavailable".toByteArray(UTF_8))
        server.respond("/mirror", 200, packageBytes)

        try {
            val repository = OfficialPluginDistributionRepository(context, configuration(rootKey, server.port))
            assertEquals(PluginDirectoryStatus.READY, repository.refreshPluginDirectory().status)
            val download = repository.downloadPlugin(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.0.0"))
            try {
                assertArrayEquals(packageBytes, download.packageFile.readBytes())
                assertTrue(download.packageFile.canRead())
                assertFalse(download.packageFile.canWrite())
            } finally {
                val file = download.packageFile
                download.close()
                assertFalse(file.exists())
            }
            assertEquals(1, server.requestCount("/primary"))
            assertEquals(1, server.requestCount("/mirror"))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun primaryIntegrityFailureIsRejectedWithoutTryingGitee() = runBlocking {
        val context = isolatedContext()
        val rootKey = newKeyPair()
        val publisherKey = newKeyPair()
        val expectedPackage = "expected package".toByteArray(UTF_8)
        val alteredPackage = "altered package!".toByteArray(UTF_8)
        assertEquals(expectedPackage.size, alteredPackage.size)
        val server = LocalHttpServer()
        val definition = release(
            version = "1.0.0",
            publisherKey = publisherKey,
            packageBytes = expectedPackage,
            githubUrl = "http://localhost:${server.port}/primary",
            giteeUrl = "http://localhost:${server.port}/mirror",
        )
        val signed = signedDirectory(rootKey, listOf(definition))
        server.respond("/latest", 200, releaseApi(server.port, signed))
        server.respond("/directory", 200, signed.bytes)
        server.respond("/signature", 200, signed.signature)
        server.respond("/primary", 200, alteredPackage)
        server.respond("/mirror", 200, expectedPackage)

        try {
            val repository = OfficialPluginDistributionRepository(context, configuration(rootKey, server.port))
            assertEquals(PluginDirectoryStatus.READY, repository.refreshPluginDirectory().status)
            val failure = runCatching {
                repository.downloadPlugin(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.0.0"))
            }.exceptionOrNull()
            assertNotNull("A digest mismatch must reject the package", failure)
            assertEquals(0, server.requestCount("/mirror"))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun invalidDirectorySignatureDoesNotReplaceLastGoodSnapshot() = runBlocking {
        val context = isolatedContext()
        val rootKey = newKeyPair()
        val publisherKey = newKeyPair()
        val packageBytes = "synthetic package".toByteArray(UTF_8)
        val release = release("1.0.0", publisherKey, packageBytes, "http://localhost:1/plugin")
        val validDirectory = signedDirectory(rootKey, listOf(release))
        val invalidDirectory = signedDirectory(newKeyPair(), listOf(release))
        val server = LocalHttpServer()
        server.respond("/latest", 200, releaseApi(server.port, validDirectory))
        server.respond("/directory", 200, validDirectory.bytes)
        server.respond("/signature", 200, validDirectory.signature)
        val config = configuration(rootKey, server.port)

        try {
            val repository = OfficialPluginDistributionRepository(context, config)
            assertEquals(PluginDirectoryStatus.READY, repository.refreshPluginDirectory().status)
            assertNotNull(repository.resolveExpectation(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.0.0")))

            server.replace("/latest", 200, releaseApi(server.port, invalidDirectory))
            server.replace("/directory", 200, invalidDirectory.bytes)
            server.replace("/signature", 200, invalidDirectory.signature)
            val invalidSnapshot = repository.refreshPluginDirectory()

            assertEquals(PluginDirectoryStatus.INVALID_DIRECTORY, invalidSnapshot.status)
            assertTrue(invalidSnapshot.usingLastGoodDirectory)
            assertNotNull(repository.resolveExpectation(PluginPackageReference(PLUGIN_ID, PUBLISHER_ID, "1.0.0")))
        } finally {
            server.close()
            context.close()
        }
    }

    private fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isolatedContext(): TestStorageContext = TestStorageContext(targetContext())

    private fun configuration(rootKey: KeyPair, port: Int): PluginDistributionConfiguration =
        PluginDistributionConfiguration(
            trustedDirectoryKey = rootKey.public,
            hostApi = HOST_API,
            supportedAbis = setOf("x86_64"),
            githubLatestReleaseApiUrl = "http://localhost:$port/latest",
            allowInsecureLocalhostForTests = true,
        )

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private data class PluginDefinition(
        val version: String,
        val publisherKey: PublicKey,
        val packageBytes: ByteArray,
        val githubUrl: String,
        val giteeUrl: String?,
        val pluginId: String = PLUGIN_ID,
        val publisherId: String = PUBLISHER_ID,
        val hostApi: String = HOST_API,
    )

    private data class SignedDirectory(val bytes: ByteArray, val signature: ByteArray)

    private fun release(
        version: String,
        publisherKey: KeyPair,
        packageBytes: ByteArray,
        githubUrl: String,
        giteeUrl: String? = null,
    ) = PluginDefinition(version, publisherKey.public, packageBytes, githubUrl, giteeUrl)

    private fun signedDirectory(rootKey: KeyPair, releases: List<PluginDefinition>): SignedDirectory {
        val ordered = releases.sortedBy(PluginDefinition::version)
        val plugins = JSONArray()
        for (release in ordered) {
            val packageMetadata = JSONObject()
                .put("size", release.packageBytes.size)
                .put("sha256", sha256(release.packageBytes))
                .put("githubUrl", release.githubUrl)
            release.giteeUrl?.let { packageMetadata.put("giteeUrl", it) }
            plugins.put(
                JSONObject()
                    .put("publisherId", release.publisherId)
                    .put("pluginId", release.pluginId)
                    .put("version", release.version)
                    .put("hostApi", release.hostApi)
                    .put("abis", JSONArray().put("x86_64"))
                    .put("publisherPublicKey", Base64.getEncoder().encodeToString(release.publisherKey.encoded))
                    .put("package", packageMetadata),
            )
        }
        val json = JSONObject().put("formatVersion", 1).put("plugins", plugins).toString().toByteArray(UTF_8)
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(rootKey.private)
        signer.update(json)
        return SignedDirectory(json, signer.sign())
    }

    private fun releaseApi(port: Int, directory: SignedDirectory): ByteArray {
        val directoryUrl = "http://localhost:$port/directory"
        val signatureUrl = "http://localhost:$port/signature"
        val assets = JSONArray()
            .put(
                JSONObject()
                    .put("name", "elymbot-plugin-directory.json")
                    .put("size", directory.bytes.size)
                    .put("digest", "sha256:${sha256(directory.bytes)}")
                    .put("browser_download_url", directoryUrl),
            )
            .put(
                JSONObject()
                    .put("name", "elymbot-plugin-directory.sig")
                    .put("size", directory.signature.size)
                    .put("digest", "sha256:${sha256(directory.signature)}")
                    .put("browser_download_url", signatureUrl),
            )
        return JSONObject().put("assets", assets).toString().toByteArray(UTF_8)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(chars)
    }

    private companion object {
        const val PLUGIN_ID = "synthetic-plugin"
        const val PUBLISHER_ID = "synthetic-publisher"
        const val HOST_API = "elymbot-plugin-api-v1"
    }

    private class LocalHttpServer : Closeable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "plugin-distribution-http-fixture").apply { isDaemon = true }
        }
        private val responses = ConcurrentHashMap<String, Response>()
        private val requests = ConcurrentHashMap<String, AtomicInteger>()
        @Volatile private var closed = false
        val port: Int = server.localPort

        init {
            executor.execute {
                while (!closed) {
                    try {
                        server.accept().use(::respond)
                    } catch (_: Exception) {
                        if (!closed) continue
                    }
                }
            }
        }

        fun respond(path: String, status: Int, body: ByteArray) {
            responses[path] = Response(status, body)
        }

        fun replace(path: String, status: Int, body: ByteArray) {
            responses[path] = Response(status, body)
        }

        fun requestCount(path: String): Int = requests[path]?.get() ?: 0

        private fun respond(socket: Socket) {
            socket.soTimeout = 5_000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return
            val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
            while (readLine(input)?.isNotEmpty() == true) Unit
            requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            val response = responses[path] ?: Response(404, byteArrayOf())
            val reason = when (response.status) {
                200 -> "OK"
                503 -> "Service Unavailable"
                else -> "Not Found"
            }
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write("HTTP/1.1 ${response.status} $reason\r\n".toByteArray(UTF_8))
            output.write("Content-Length: ${response.body.size}\r\n".toByteArray(UTF_8))
            output.write("Content-Type: application/octet-stream\r\n".toByteArray(UTF_8))
            output.write("Connection: close\r\n\r\n".toByteArray(UTF_8))
            output.write(response.body)
            output.flush()
        }

        private fun readLine(input: BufferedInputStream): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) return if (bytes.size() == 0) null else bytes.toString(UTF_8.name())
                if (value == '\n'.code) break
                if (value != '\r'.code) bytes.write(value)
                if (bytes.size() > 8_192) return null
            }
            return bytes.toString(UTF_8.name())
        }

        override fun close() {
            closed = true
            runCatching { server.close() }
            executor.shutdownNow()
        }

        private data class Response(val status: Int, val body: ByteArray)
    }

    private class TestStorageContext(base: Context) : ContextWrapper(base), Closeable {
        private val root = File(base.cacheDir, "plugin-distribution-test-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
        private val isolatedFiles = File(root, "files").apply { check(mkdirs() || isDirectory) }
        private val isolatedCache = File(root, "cache").apply { check(mkdirs() || isDirectory) }

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = isolatedFiles
        override fun getCacheDir(): File = isolatedCache

        override fun close() {
            check(root.deleteRecursively())
        }
    }

}
