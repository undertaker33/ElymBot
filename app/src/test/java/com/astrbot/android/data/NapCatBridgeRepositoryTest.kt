package com.astrbot.android.data

import com.astrbot.android.model.RuntimeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NapCatBridgeRepositoryTest {
    @Test
    fun `mark running stores typed running state`() {
        NapCatBridgeRepository.resetRuntimeStateForTests()

        NapCatBridgeRepository.markRunning(pidHint = "pid-1")

        val state = NapCatBridgeRepository.runtimeState.value
        assertEquals(RuntimeStatus.RUNNING, state.statusType)
        assertEquals("Running", state.status)
    }

    @Test
    fun `mark checking preserves active startup status`() {
        NapCatBridgeRepository.resetRuntimeStateForTests()
        NapCatBridgeRepository.markStarting()

        NapCatBridgeRepository.markChecking()

        assertEquals(RuntimeStatus.STARTING, NapCatBridgeRepository.runtimeState.value.statusType)
    }
}
