package com.elymbot.android

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elymbot.android.ui.AppUpdateCandidate
import com.elymbot.android.ui.AppUpdateInstallResult
import com.elymbot.android.ui.AppUpdateInstallTicket
import com.elymbot.android.ui.AppUpdatePrompt
import com.elymbot.android.ui.ElymBotTheme
import com.elymbot.android.ui.ElymBotUiService
import com.elymbot.android.ui.ElymBotViewModel
import com.elymbot.android.ui.ScheduledTaskDetail
import com.elymbot.core.contract.ScheduledTask
import com.elymbot.core.contract.ScheduledTaskCreationSource
import com.elymbot.core.contract.ScheduledTaskEndCondition
import com.elymbot.core.contract.ScheduledTaskRepeatRule
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskAndUpdateLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun pausedTaskCanRunManually() {
        val invocations = AtomicInteger()
        val task = ScheduledTask(
            id = "test-task", revision = 1, title = "Test task", prompt = "Test prompt",
            startLocalDateTime = LocalDateTime.parse("2030-01-01T08:00"), timeZoneId = "UTC",
            repeatRule = ScheduledTaskRepeatRule.Once, endCondition = ScheduledTaskEndCondition.Never,
            enabled = false, nextRunAt = null, creationSource = ScheduledTaskCreationSource.APP,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ElymBotTheme(dark = false) {
                    Column {
                        ScheduledTaskDetail(task, emptyList(), busy = false, onRun = { invocations.incrementAndGet() },
                            onToggle = {}, onEdit = {}, onDelete = {})
                    }
                }
            }
        }
        compose.onNodeWithText(label(R.string.elym_task_run_now)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, invocations.get()) }
    }

    @Test
    fun activityRecreationCleansAnUnhandedUpdateTicket() {
        val entered = AtomicReference<Continuation<AppUpdateInstallResult>?>()
        val finished = AtomicReference<String?>()
        val disposed = AtomicBoolean()
        val dismissed = AtomicInteger()
        val ticket = AppUpdateInstallTicket("test-ticket", Intent("com.elymbot.android.test.UPDATE_INSTALLER"), 2L, "2-test")
        compose.activityRule.scenario.onActivity { activity ->
            val realService = (activity.application as ElymBotApplication).uiService
            val service = object : ElymBotUiService by realService {
                override fun canRequestAppUpdateInstall() = true

                // Deliver a ticket even when Activity teardown races with preparation completing.
                override suspend fun prepareAppUpdateInstall(offerId: String): AppUpdateInstallResult =
                    suspendCoroutine { entered.set(it) }

                override suspend fun finishAppUpdateInstall(ticketId: String) {
                    finished.set(ticketId)
                }
            }
            val model = ElymBotViewModel(service)
            activity.setContent {
                DisposableEffect(Unit) { onDispose { disposed.set(true) } }
                ElymBotTheme(dark = false) {
                    AppUpdatePrompt(model, AppUpdateCandidate("test-offer", 2L, "2-test")) { dismissed.incrementAndGet() }
                }
            }
        }
        compose.onNodeWithText(label(R.string.elym_update_download)).performClick()
        compose.waitUntil("confirmed download begins", timeoutMillis = 5_000) { entered.get() != null }
        val preparation = checkNotNull(entered.get())
        compose.activityRule.scenario.recreate()
        compose.waitUntil("old Activity composition is disposed", timeoutMillis = 5_000) { disposed.get() }
        preparation.resume(AppUpdateInstallResult.Ready(ticket))
        compose.waitUntil("unhanded update ticket is cleaned", timeoutMillis = 5_000) { finished.get() != null }
        assertEquals(ticket.id, finished.get())
        assertEquals(0, dismissed.get())
    }

    private fun label(resource: Int) = compose.activity.getString(resource)
}
