package com.astrbot.android.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.astrbot.android.data.NapCatBridgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class ContainerBridgeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressMonitorJob: Job? = null
    private var healthMonitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Container idle"))
        RuntimeLogRepository.append("ContainerBridgeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BRIDGE -> serviceScope.launch { handleStartBridge() }
            ACTION_STOP_BRIDGE -> serviceScope.launch { handleStopBridge() }
            ACTION_CHECK_BRIDGE -> serviceScope.launch { handleCheckBridge() }
            else -> RuntimeLogRepository.append("Bridge service received default start")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopHealthMonitor()
        stopProgressMonitor()
        serviceScope.cancel()
        RuntimeLogRepository.append("ContainerBridgeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handleStartBridge() {
        val config = NapCatBridgeRepository.config.value
        stopHealthMonitor()
        NapCatBridgeRepository.markStarting()
        syncProgressFromRuntimeFiles()
        startProgressMonitor()
        RuntimeLogRepository.append("Starting local NapCat bridge")
        updateForeground("Starting NapCat")

        val result = BridgeCommandRunner.execute(config.startCommand)
        if (result.exitCode == 0 || config.startCommand.isBlank()) {
            RuntimeLogRepository.append("Start command completed: ${result.stdout.ifBlank { "no output" }}")
            syncProgressFromRuntimeFiles()
            RuntimeLogRepository.append("Waiting for NapCat health endpoint")

            val health = BridgeHealthChecker.checkWithRetry(config.healthUrl)
            if (health.ok) {
                stopHealthMonitor()
                stopProgressMonitor()
                NapCatBridgeRepository.markRunning(pidHint = "napcat-local")
                RuntimeLogRepository.append("Health check passed after start: ${health.message}")
                updateForeground("NapCat running")
                return
            }

            val statusResult = BridgeCommandRunner.execute(config.statusCommand)
            if (statusResult.exitCode == 0) {
                val pidHint = statusResult.stdout.substringAfter("RUNNING:", "napcat-local").trim().ifBlank { "napcat-local" }
                NapCatBridgeRepository.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is running, but HTTP endpoint is not ready: ${health.message}",
                )
                syncProgressFromRuntimeFiles()
                RuntimeLogRepository.append("Health endpoint not ready yet, but process is running: ${health.message}")
                appendContainerLogTail("NapCat log")
                updateForeground(buildProgressNotificationText())
                startHealthMonitor(config.healthUrl, pidHint)
            } else {
                stopHealthMonitor()
                stopProgressMonitor()
                NapCatBridgeRepository.markError("Started, but health check failed: ${health.message}")
                RuntimeLogRepository.append("Health check failed after start: ${health.message}")
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat start incomplete")
            }
        } else {
            stopHealthMonitor()
            stopProgressMonitor()
            NapCatBridgeRepository.markError("Start command failed: ${result.stderr.ifBlank { result.stdout }}")
            RuntimeLogRepository.append("Start command failed: ${result.stderr.ifBlank { result.stdout }}")
            appendContainerLogTail("NapCat log")
            updateForeground("NapCat start failed")
        }
    }

    private fun startHealthMonitor(url: String, pidHint: String) {
        if (healthMonitorJob?.isActive == true) return

        healthMonitorJob = serviceScope.launch {
            try {
                waitForHealthy(url, pidHint)
            } finally {
                healthMonitorJob = null
            }
        }
    }

    private fun stopHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = null
    }

    private suspend fun waitForHealthy(url: String, pidHint: String) {
        val config = NapCatBridgeRepository.config.value
        val startedAt = System.currentTimeMillis()
        var lastActivityAt = runtimeActivityTimestamp().takeIf { it > 0L } ?: startedAt

        while (true) {
            delay(HEALTH_POLL_INTERVAL_MS)
            val snapshot = syncProgressFromRuntimeFiles()
            runtimeActivityTimestamp()
                .takeIf { it > 0L }
                ?.let { lastActivityAt = maxOf(lastActivityAt, it) }

            val health = BridgeHealthChecker.check(url)
            if (health.ok) {
                stopProgressMonitor()
                NapCatBridgeRepository.markRunning(pidHint = pidHint)
                RuntimeLogRepository.append("Health endpoint became ready: ${health.message}")
                updateForeground("NapCat running")
                return
            }

            val statusResult = BridgeCommandRunner.execute(config.statusCommand)
            if (config.statusCommand.isNotBlank() && statusResult.exitCode != 0) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                NapCatBridgeRepository.markStopped(reason = "NapCat process exited during startup")
                RuntimeLogRepository.append(
                    "NapCat process exited before health endpoint became ready: ${statusResult.stderr.ifBlank { statusResult.stdout }}",
                )
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat stopped")
                return
            }

            NapCatBridgeRepository.markProcessRunning(
                pidHint = pidHint,
                details = buildPendingHealthDetails(snapshot, health, startedAt),
            )
            updateForeground(buildProgressNotificationText())

            val now = System.currentTimeMillis()
            val elapsed = now - startedAt
            val silentFor = now - lastActivityAt
            if (elapsed >= MAX_STARTUP_WAIT_MS) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                NapCatBridgeRepository.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is still running, but WebUI startup exceeded ${MAX_STARTUP_WAIT_MS / 60000} minutes. Check runtime logs.",
                )
                RuntimeLogRepository.append("Health endpoint still not ready after max wait: elapsed=${elapsed / 1000}s")
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat still warming up")
                return
            }

            if (elapsed >= MIN_WAIT_BEFORE_STALE_TIMEOUT_MS && silentFor >= STALE_ACTIVITY_TIMEOUT_MS) {
                stopProgressMonitor()
                syncProgressFromRuntimeFiles()
                NapCatBridgeRepository.markProcessRunning(
                    pidHint = pidHint,
                    details = "NapCat process is still running, but no runtime activity was seen for ${silentFor / 60000} minutes. Check runtime logs.",
                )
                RuntimeLogRepository.append(
                    "Health endpoint still not ready and runtime activity is stale: elapsed=${elapsed / 1000}s silent=${silentFor / 1000}s",
                )
                appendContainerLogTail("NapCat log")
                updateForeground("NapCat startup stalled")
                return
            }
        }
    }

    private suspend fun handleStopBridge() {
        val config = NapCatBridgeRepository.config.value
        stopHealthMonitor()
        stopProgressMonitor()
        val result = BridgeCommandRunner.execute(config.stopCommand)
        RuntimeLogRepository.append(
            if (result.exitCode == 0 || config.stopCommand.isBlank()) {
                "Stop command completed"
            } else {
                "Stop command failed: ${result.stderr.ifBlank { result.stdout }}"
            },
        )
        NapCatBridgeRepository.markStopped()
        updateForeground("NapCat stopped")
    }

    private suspend fun handleCheckBridge() {
        val config = NapCatBridgeRepository.config.value
        NapCatBridgeRepository.markChecking()
        syncProgressFromRuntimeFiles()
        RuntimeLogRepository.append("Checking local NapCat bridge")

        val statusResult = BridgeCommandRunner.execute(config.statusCommand)
        if (config.statusCommand.isNotBlank()) {
            RuntimeLogRepository.append(
                if (statusResult.exitCode == 0) {
                    "Status command completed: ${statusResult.stdout.ifBlank { "ok" }}"
                } else {
                    "Status command failed: ${statusResult.stderr.ifBlank { statusResult.stdout }}"
                },
            )
        }

        val health = BridgeHealthChecker.check(config.healthUrl)
        if (health.ok) {
            stopHealthMonitor()
            stopProgressMonitor()
            NapCatBridgeRepository.markRunning(pidHint = "napcat-local")
            RuntimeLogRepository.append("Bridge health check passed: ${health.message}")
            updateForeground("NapCat healthy")
            return
        }

        if (statusResult.exitCode == 0) {
            val pidHint = statusResult.stdout.substringAfter("RUNNING:", "napcat-local").trim().ifBlank { "napcat-local" }
            NapCatBridgeRepository.markProcessRunning(
                pidHint = pidHint,
                details = "NapCat process is running, but HTTP endpoint is not ready: ${health.message}",
            )
            syncProgressFromRuntimeFiles()
            RuntimeLogRepository.append("Bridge process is running but endpoint is not ready: ${health.message}")
            startProgressMonitor()
            startHealthMonitor(config.healthUrl, pidHint)
            updateForeground(buildProgressNotificationText())
            return
        }

        stopHealthMonitor()
        stopProgressMonitor()
        NapCatBridgeRepository.markStopped(reason = "Health check failed")
        RuntimeLogRepository.append("Bridge health check failed: ${health.message}")
        updateForeground("NapCat stopped")
    }

    private fun startProgressMonitor() {
        if (progressMonitorJob?.isActive == true) return

        progressMonitorJob = serviceScope.launch {
            while (isActive) {
                syncProgressFromRuntimeFiles()
                updateForeground(buildProgressNotificationText())
                delay(1000)
            }
        }
    }

    private fun stopProgressMonitor() {
        progressMonitorJob?.cancel()
        progressMonitorJob = null
    }

    private fun syncProgressFromRuntimeFiles(): RuntimeProgressSnapshot {
        val progressDir = File(filesDir, "runtime/run")
        val percent = progressDir.resolve("napcat_progress").readIntOrDefault(0)
        val rawLabel = progressDir.resolve("napcat_progress_label").readSafeText()
        val indeterminate = progressDir.resolve("napcat_progress_mode").readSafeText() == "1"
        val installerCached = progressDir.resolve("napcat_installer_cached").readSafeText() == "1"
        val label = localizeProgressLabel(rawLabel)

        NapCatBridgeRepository.markInstallerCached(installerCached)
        if (label.isNotBlank() || percent > 0) {
            NapCatBridgeRepository.updateProgress(
                label = label,
                percent = percent,
                indeterminate = indeterminate,
                installerCached = installerCached,
            )
        }

        return RuntimeProgressSnapshot(
            label = label,
            percent = percent,
            indeterminate = indeterminate,
            installerCached = installerCached,
        )
    }

    private fun buildProgressNotificationText(): String {
        val runtimeState = NapCatBridgeRepository.runtimeState.value
        return when (runtimeState.status) {
            "Running" -> "NapCat running"
            "Error" -> "NapCat start failed"
            "Stopped" -> "NapCat stopped"
            else -> {
                if (runtimeState.progressLabel.isNotBlank()) {
                    "NapCat starting: ${runtimeState.progressLabel}"
                } else {
                    "NapCat warming up"
                }
            }
        }
    }

    private fun localizeProgressLabel(rawLabel: String): String {
        return when (rawLabel) {
            "preparing-start" -> "Preparing start"
            "prepare-container" -> "Preparing container"
            "install-base" -> "Installing base packages from network"
            "base-ready" -> "Base packages ready"
            "backup-config" -> "Backing up existing NapCat config"
            "download-installer" -> "Downloading upstream installer"
            "installer-downloaded" -> "Installer script downloaded"
            "installer-cached" -> "Existing install detected"
            "run-installer" -> "Installing NapCat from network"
            "restore-config" -> "Restoring NapCat config"
            "write-config" -> "Writing NapCat config"
            "start-napcat" -> "Starting NapCat"
            else -> rawLabel
        }
    }

    private fun buildPendingHealthDetails(
        snapshot: RuntimeProgressSnapshot,
        health: HealthCheckResult,
        startedAt: Long,
    ): String {
        val stage = snapshot.label.ifBlank { "Starting NapCat" }
        val percentSuffix = if (snapshot.percent in 1..99) " (${snapshot.percent}%)" else ""
        return "NapCat process is running. Current stage: $stage$percentSuffix. Waiting for HTTP endpoint: ${health.message}. Elapsed ${formatElapsed(System.currentTimeMillis() - startedAt)}."
    }

    private fun runtimeActivityTimestamp(): Long {
        val progressDir = File(filesDir, "runtime/run")
        val activityFiles = mutableListOf(File(filesDir, "runtime/logs/napcat.log"))
        progressDir.listFiles()?.let { activityFiles.addAll(it) }
        return activityFiles
            .filter { it.exists() }
            .maxOfOrNull { it.lastModified() }
            ?: 0L
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "$minutes min $seconds s" else "$seconds s"
    }

    private fun appendContainerLogTail(prefix: String, maxLines: Int = 60) {
        val logFile = File(filesDir, "runtime/logs/napcat.log")
        if (!logFile.exists()) {
            RuntimeLogRepository.append("$prefix unavailable: ${logFile.absolutePath}")
            return
        }

        val lines = logFile.readLines()
            .takeLast(maxLines)
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            RuntimeLogRepository.append("$prefix is empty")
            return
        }

        lines.forEach { RuntimeLogRepository.append("$prefix | $it") }
    }

    private fun updateForeground(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AstrBot Runtime",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("AstrBot Runtime")
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    private fun File.readSafeText(): String {
        return if (exists()) {
            runCatching { readText().trim() }.getOrDefault("")
        } else {
            ""
        }
    }

    private fun File.readIntOrDefault(defaultValue: Int): Int {
        return readSafeText().toIntOrNull() ?: defaultValue
    }

    private data class RuntimeProgressSnapshot(
        val label: String = "",
        val percent: Int = 0,
        val indeterminate: Boolean = false,
        val installerCached: Boolean = false,
    )

    companion object {
        const val ACTION_START_BRIDGE = "com.astrbot.android.action.START_BRIDGE"
        const val ACTION_STOP_BRIDGE = "com.astrbot.android.action.STOP_BRIDGE"
        const val ACTION_CHECK_BRIDGE = "com.astrbot.android.action.CHECK_BRIDGE"

        private const val HEALTH_POLL_INTERVAL_MS = 5_000L
        private const val MAX_STARTUP_WAIT_MS = 45 * 60 * 1000L
        private const val MIN_WAIT_BEFORE_STALE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val STALE_ACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
        private const val CHANNEL_ID = "astrbot_runtime"
        private const val NOTIFICATION_ID = 1001
    }
}
