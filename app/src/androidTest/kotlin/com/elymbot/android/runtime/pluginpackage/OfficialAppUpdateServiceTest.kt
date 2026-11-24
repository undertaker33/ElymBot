package com.elymbot.android.runtime.pluginpackage

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elymbot.android.ui.AppUpdateUiStatus
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialAppUpdateServiceTest {
    @Test
    fun appStartupChecksOnceAndManualCheckCanRecoverFromNetworkFailure() = runBlocking<Unit> {
        isolatedContext().use { context ->
            LocalHttpServer().use { server ->
                val app = installedApp(context)
                val job = Job()
                server.respond("/latest", 503, ByteArray(0))
                try {
                    val host = AppHostManagementUiService(
                        context = context,
                        runtimeManager = PluginRuntimeManager(context),
                        configurationLoad = OfficialPluginDistributionConfigurationLoad.NotConfigured,
                        distributionRepository = OfficialPluginDistributionRepository(context, null),
                        appUpdateService = service(context, app, server.port),
                        scope = CoroutineScope(job + Dispatchers.IO),
                    )
                    val failure = withTimeout(5_000) {
                        host.appUpdate.first { it.status != AppUpdateUiStatus.Checking }
                    }
                    assertEquals(AppUpdateUiStatus.NetworkError, failure.status)
                    assertEquals(1, server.requestCount("/latest"))

                    host.checkForAppUpdate(forceRefresh = false)
                    assertEquals(1, server.requestCount("/latest"))

                    val apk = "unrequested APK fixture".toByteArray(UTF_8)
                    val sidecar = releaseMetadata(
                        packageName = app.packageName,
                        versionCode = app.versionCode + 1,
                        versionName = "0.1.1-test",
                        apkDigest = sha256(apk),
                    )
                    server.replace(
                        "/latest",
                        200,
                        releaseApi(
                            server.port,
                            listOf(
                                ReleaseAsset("elymbot-release.json", "metadata.json", sidecar),
                                ReleaseAsset("release.apk", "app.apk", apk),
                            ),
                        ),
                    )
                    server.respond("/metadata.json", 200, sidecar)
                    host.checkForAppUpdate(forceRefresh = true)
                    assertEquals(AppUpdateUiStatus.UpdateAvailable, host.appUpdate.value.status)
                    assertEquals(2, server.requestCount("/latest"))
                    assertEquals(0, server.requestCount("/app.apk"))
                } finally {
                    job.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun defaultProductionConfigurationRejectsDebugPackageBeforeNetwork() = runBlocking {
        val context = isolatedContext()
        val server = LocalHttpServer()
        try {
            val service = OfficialAppUpdateService(context)
            val result = service.checkForUpdate()
            assertEquals(AppUpdateStatus.INCOMPATIBLE_PACKAGE, result.status)
            assertEquals(0, server.requestCount("/latest"))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun currentOfficialReleaseWithoutSidecarReportsMissingMetadata() = runBlocking {
        val context = isolatedContext()
        val server = LocalHttpServer()
        val app = installedApp(context)
        server.respond(
            "/latest",
            200,
            releaseApi(
                server.port,
                listOf(ReleaseAsset("ElymBot-v0.1.0-release.apk", "app.apk", "current release".toByteArray(UTF_8))),
            ),
        )
        try {
            val service = service(context, app, server.port)
            val result = service.checkForUpdate()
            assertEquals(AppUpdateStatus.METADATA_MISSING, result.status)
            assertNull(result.availableUpdate)
            assertEquals(0, server.requestCount("/app.apk"))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun newerNumericVersionIsAvailableWithoutDownloadingApkBeforeConfirmation() = runBlocking {
        val context = isolatedContext()
        val server = LocalHttpServer()
        val app = installedApp(context)
        val apkBytes = "synthetic apk transfer fixture".toByteArray(UTF_8)
        val metadataBytes = releaseMetadata(
            packageName = app.packageName,
            versionCode = app.versionCode + 1,
            versionName = "0.1.1-test",
            apkDigest = sha256(apkBytes),
        )
        server.respond(
            "/latest",
            200,
            releaseApi(
                server.port,
                listOf(
                    ReleaseAsset("elymbot-release.json", "metadata.json", metadataBytes),
                    ReleaseAsset("release.apk", "app.apk", apkBytes),
                ),
            ),
        )
        server.respond("/metadata.json", 200, metadataBytes)
        server.respond("/app.apk", 200, apkBytes)

        try {
            val result = service(context, app, server.port).checkForUpdate()
            assertEquals(AppUpdateStatus.UPDATE_AVAILABLE, result.status)
            assertNotNull(result.availableUpdate)
            val offer = requireNotNull(result.availableUpdate)
            assertEquals(app.packageName, offer.packageName)
            assertEquals(app.versionCode + 1, offer.versionCode)
            assertEquals("0.1.1-test", offer.versionName)
            assertEquals(0, server.requestCount("/app.apk"))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun apiDigestMismatchAndWrongPackageMetadataFailClosed() = runBlocking {
        val context = isolatedContext()
        val server = LocalHttpServer()
        val app = installedApp(context)
        val apkBytes = "apk".toByteArray(UTF_8)
        val mismatchMetadata = releaseMetadata(
            packageName = app.packageName,
            versionCode = app.versionCode + 1,
            versionName = "0.1.1-test",
            apkDigest = sha256(apkBytes),
        )
        server.respond(
            "/latest",
            200,
            releaseApi(
                server.port,
                listOf(
                    ReleaseAsset("elymbot-release.json", "metadata.json", mismatchMetadata, apiDigest = "0".repeat(64)),
                    ReleaseAsset("release.apk", "app.apk", apkBytes),
                ),
            ),
        )
        server.respond("/metadata.json", 200, mismatchMetadata)
        server.respond("/app.apk", 200, apkBytes)

        try {
            val badDigest = service(context, app, server.port).checkForUpdate()
            assertEquals(AppUpdateStatus.INVALID_RELEASE, badDigest.status)
            assertNull(badDigest.availableUpdate)
            assertEquals(0, server.requestCount("/app.apk"))

            val wrongPackage = releaseMetadata(
                packageName = "com.example.other",
                versionCode = app.versionCode + 1,
                versionName = "0.1.1-test",
                apkDigest = sha256(apkBytes),
            )
            server.replace(
                "/latest",
                200,
                releaseApi(
                    server.port,
                    listOf(
                        ReleaseAsset("elymbot-release.json", "metadata.json", wrongPackage),
                        ReleaseAsset("release.apk", "app.apk", apkBytes),
                    ),
                ),
            )
            server.replace("/metadata.json", 200, wrongPackage)
            val wrongIdentity = service(context, app, server.port).checkForUpdate()
            assertEquals(AppUpdateStatus.INVALID_RELEASE, wrongIdentity.status)
            assertNull(wrongIdentity.availableUpdate)
            assertEquals(0, server.requestCount("/app.apk"))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun currentOrOlderNumericVersionIsUpToDate() = runBlocking {
        val context = isolatedContext()
        val server = LocalHttpServer()
        val app = installedApp(context)
        val apkBytes = "unused apk bytes".toByteArray(UTF_8)
        val metadataBytes = releaseMetadata(
            packageName = app.packageName,
            versionCode = app.versionCode,
            versionName = app.versionName,
            apkDigest = sha256(apkBytes),
        )
        server.respond(
            "/latest",
            200,
            releaseApi(
                server.port,
                listOf(
                    ReleaseAsset("elymbot-release.json", "metadata.json", metadataBytes),
                    ReleaseAsset("release.apk", "app.apk", apkBytes),
                ),
            ),
        )
        server.respond("/metadata.json", 200, metadataBytes)

        try {
            val result = service(context, app, server.port).checkForUpdate()
            assertEquals(AppUpdateStatus.UP_TO_DATE, result.status)
            assertNull(result.availableUpdate)
            assertEquals(0, server.requestCount("/app.apk"))
        } finally {
            server.close()
            context.close()
        }
    }

    @Test
    fun installerIntentUsesReadOnlyAppUpdateProviderUri() {
        val context = targetContext()
        val app = installedApp(context)
        val service = OfficialAppUpdateService(
            context,
            OfficialAppUpdateConfiguration(
                packageName = app.packageName,
                trustedSignerSha256 = app.signerSha256,
            ),
        )
        val updateDirectory = File(context.cacheDir, "app-updates")
        val createdUpdateDirectory = if (updateDirectory.exists()) {
            false
        } else {
            check(updateDirectory.mkdir())
            true
        }
        val packageFile = File(updateDirectory, "installer-test-${UUID.randomUUID()}.apk")
        val packageBytes = "scoped installer URI fixture".toByteArray(UTF_8)
        var createdPackageFile = false
        var verifiedUpdate: VerifiedAppUpdateApk? = null
        try {
            check(updateDirectory.isDirectory)
            check(!Files.isSymbolicLink(updateDirectory.toPath()))
            check(updateDirectory.canonicalFile.toPath().startsWith(context.cacheDir.canonicalFile.toPath()))
            createdPackageFile = packageFile.createNewFile()
            check(createdPackageFile)
            packageFile.writeBytes(packageBytes)
            check(packageFile.setReadable(false, false))
            check(packageFile.setReadable(true, true))
            check(packageFile.setWritable(false, false))
            assertFalse(packageFile.canWrite())

            // The private owner token keeps production intent creation restricted to service-verified handles.
            val tokenField = OfficialAppUpdateService::class.java.getDeclaredField("serviceToken")
                .apply { isAccessible = true }
            val ownerToken = tokenField.get(service)
            val update = VerifiedAppUpdateApk(
                packageFile = packageFile,
                versionCode = app.versionCode + 1,
                versionName = "0.1.1-test",
                ownerToken = ownerToken,
            )
            verifiedUpdate = update

            val intent = service.installerIntent(context, update)
            assertEquals(Intent.ACTION_INSTALL_PACKAGE, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))

            val uri = requireNotNull(intent.data)
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.updates", uri.authority)
            assertEquals("app_updates", uri.pathSegments.first())
            assertEquals(packageFile.name, uri.lastPathSegment)
            val sharedBytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            assertArrayEquals(packageBytes, sharedBytes)
        } finally {
            verifiedUpdate?.close()
            if (createdPackageFile && packageFile.exists()) packageFile.delete()
            if (createdUpdateDirectory && updateDirectory.list()?.isEmpty() == true) updateDirectory.delete()
        }
    }

    private fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isolatedContext(): TestStorageContext = TestStorageContext(targetContext())

    private fun installedApp(context: Context): InstalledApp {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        return InstalledApp(
            packageName = context.packageName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(),
            versionName = info.versionName.orEmpty(),
            signerSha256 = sha256(signatures.single().toByteArray()),
        )
    }

    private fun service(context: Context, app: InstalledApp, port: Int) = OfficialAppUpdateService(
        context,
        OfficialAppUpdateConfiguration(
            packageName = app.packageName,
            trustedSignerSha256 = app.signerSha256,
            githubLatestReleaseApiUrl = "http://localhost:$port/latest",
            allowInsecureLocalhostForTests = true,
        ),
    )

    private data class InstalledApp(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
        val signerSha256: String,
    )

    private data class ReleaseAsset(
        val name: String,
        val path: String,
        val bytes: ByteArray,
        val apiDigest: String? = null,
    )

    private fun releaseMetadata(
        packageName: String,
        versionCode: Long,
        versionName: String,
        apkDigest: String,
    ): ByteArray = JSONObject()
        .put("formatVersion", 1)
        .put("packageName", packageName)
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("apkAssetName", "release.apk")
        .put("apkSha256", apkDigest)
        .toString()
        .toByteArray(UTF_8)

    private fun releaseApi(port: Int, assets: List<ReleaseAsset>): ByteArray {
        val apiAssets = JSONArray()
        assets.forEach { asset ->
            apiAssets.put(
                JSONObject()
                    .put("name", asset.name)
                    .put("size", asset.bytes.size)
                    .put("digest", "sha256:${asset.apiDigest ?: sha256(asset.bytes)}")
                    .put("browser_download_url", "http://localhost:$port/${asset.path}"),
            )
        }
        return JSONObject().put("assets", apiAssets).toString().toByteArray(UTF_8)
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

    private class LocalHttpServer : Closeable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "app-update-http-fixture").apply { isDaemon = true }
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
            val reason = if (response.status == 200) "OK" else "Not Found"
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write("HTTP/1.1 ${response.status} $reason\r\n".toByteArray(UTF_8))
            output.write("Content-Length: ${response.body.size}\r\n".toByteArray(UTF_8))
            output.write("Connection: close\r\n\r\n".toByteArray(UTF_8))
            output.write(response.body)
            output.flush()
        }

        private fun readLine(input: BufferedInputStream): String? {
            val output = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) return if (output.size() == 0) null else output.toString(UTF_8.name())
                if (value == '\n'.code) break
                if (value != '\r'.code) output.write(value)
                if (output.size() > 8_192) return null
            }
            return output.toString(UTF_8.name())
        }

        override fun close() {
            closed = true
            runCatching { server.close() }
            executor.shutdownNow()
        }

        private data class Response(val status: Int, val body: ByteArray)
    }

    private class TestStorageContext(base: Context) : ContextWrapper(base), Closeable {
        private val root = File(base.cacheDir, "app-update-test-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
        private val files = File(root, "files").apply { check(mkdirs() || isDirectory) }
        private val cache = File(root, "cache").apply { check(mkdirs() || isDirectory) }

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = files
        override fun getCacheDir(): File = cache

        override fun close() {
            assertTrue(root.deleteRecursively())
        }
    }
}
