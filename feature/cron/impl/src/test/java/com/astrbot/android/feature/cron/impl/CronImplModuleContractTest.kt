package com.astrbot.android.feature.cron.impl

import com.astrbot.android.feature.cron.domain.CronExpressionParser
import com.astrbot.android.feature.cron.runtime.CronJobRunOutcome
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CronImplModuleContractTest {

    @Test
    fun impl_module_exposes_cron_api_and_runtime_contracts_for_terminal_verification() {
        val after = Instant.parse("2026-01-01T08:58:00Z").toEpochMilli()
        val nextFireTime = CronExpressionParser.nextFireTime(
            expression = "0 9 * * *",
            afterEpochMillis = after,
            timezone = "UTC",
        )

        assertEquals(Instant.parse("2026-01-01T09:00:00Z").toEpochMilli(), nextFireTime)
        assertEquals("Succeeded", CronJobRunOutcome.Succeeded.name)
    }
}
