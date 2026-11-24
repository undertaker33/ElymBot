package com.elymbot.android.plugins.qq

import com.elymbot.core.contract.PlatformDeliveryFailure
import com.elymbot.core.contract.PlatformDeliveryResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class QqOneBotDeliveryResultTest {
    @Test
    fun mapsOnlyExplicitSynchronousSuccessAndProtocolFailure() {
        assertEquals(
            PlatformDeliveryResult.Delivered("42"),
            response("""{"status":"ok","retcode":0,"data":{"message_id":42}}"""),
        )
        assertEquals(
            PlatformDeliveryResult.Failed(PlatformDeliveryFailure.MESSAGE_REJECTED, retryable = false),
            response("""{"status":"failed","retcode":1400,"data":null}"""),
        )
    }

    @Test
    fun keepsAsyncAndContradictoryResponsesUnknown() {
        listOf(
            """{"status":"async","retcode":1,"data":null}""",
            """{"status":"failed","retcode":1,"data":null}""",
            """{"status":"ok","retcode":1404,"data":null}""",
            """{"status":"failed","retcode":0,"data":null}""",
            """{"status":"ok","retcode":"0","data":{}}""",
            """{"status":"unknown","retcode":1400,"data":null}""",
        ).forEach { payload ->
            assertEquals(
                PlatformDeliveryResult.Unknown(PlatformDeliveryFailure.RESPONSE_INVALID),
                response(payload),
            )
        }
    }

    private fun response(payload: String) =
        Json.parseToJsonElement(payload).jsonObject.toQqDeliveryResult()
}
