package com.astrbot.android

import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityAutoStartPolicyTest {
    @Test
    fun `auto start is skipped while runtime is already active`() {
        assertFalse(
            shouldAutoStartBridgeForTests(
                autoStartEnabled = true,
                runtimeState = NapCatRuntimeState(statusType = RuntimeStatus.RUNNING),
            ),
        )
        assertFalse(
            shouldAutoStartBridgeForTests(
                autoStartEnabled = true,
                runtimeState = NapCatRuntimeState(statusType = RuntimeStatus.STARTING),
            ),
        )
    }

    @Test
    fun `auto start remains allowed for stopped runtime when feature is enabled`() {
        assertTrue(
            shouldAutoStartBridgeForTests(
                autoStartEnabled = true,
                runtimeState = NapCatRuntimeState(statusType = RuntimeStatus.STOPPED),
            ),
        )
    }
}
