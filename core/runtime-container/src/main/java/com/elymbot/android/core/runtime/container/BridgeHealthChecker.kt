package com.elymbot.android.core.runtime.container

import com.elymbot.android.core.runtime.network.RuntimeNetworkCapability
import com.elymbot.android.core.runtime.network.RuntimeNetworkException
import com.elymbot.android.core.runtime.network.RuntimeNetworkFailure
import com.elymbot.android.core.runtime.network.RuntimeNetworkRequest
import com.elymbot.android.core.runtime.network.RuntimeNetworkTransport
import com.elymbot.android.core.runtime.network.RuntimeTimeoutProfile
import javax.inject.Inject
import kotlinx.coroutines.delay

data class HealthCheckResult(
    val ok: Boolean,
    val code: Int,
    val message: String,
)

class BridgeHealthChecker @Inject constructor(
    private val transport: RuntimeNetworkTransport,
) {
    suspend fun check(url: String): HealthCheckResult {
        if (url.isBlank()) {
            return HealthCheckResult(false, -1, "Health URL is blank")
        }

        return try {
            val response = transport.execute(
                RuntimeNetworkRequest(
                    capability = RuntimeNetworkCapability.ACTIVE_CAPABILITY,
                    method = "GET",
                    url = url,
                    timeoutProfile = RuntimeTimeoutProfile.ACTIVE_CAPABILITY_CALLBACK,
                    connectTimeoutMs = HEALTH_TIMEOUT_MS,
                    readTimeoutMs = HEALTH_TIMEOUT_MS,
                    followRedirects = true,
                ),
            )
            val code = response.statusCode
            val ok = code in 200..399
            HealthCheckResult(ok, code, "HTTP $code")
        } catch (e: RuntimeNetworkException) {
            val failure = e.failure
            HealthCheckResult(false, failure.statusCodeOrDefault(), failure.summary)
        } catch (e: Exception) {
            HealthCheckResult(false, -1, e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun checkWithRetry(
        url: String,
        attempts: Int = 10,
        delayMs: Long = 1500,
    ): HealthCheckResult {
        var lastResult = check(url)
        repeat((attempts - 1).coerceAtLeast(0)) {
            if (lastResult.ok) {
                return lastResult
            }
            delay(delayMs)
            lastResult = check(url)
        }
        return lastResult
    }

    private fun RuntimeNetworkFailure.statusCodeOrDefault(): Int {
        return when (this) {
            is RuntimeNetworkFailure.Http -> statusCode
            else -> -1
        }
    }

    private companion object {
        const val HEALTH_TIMEOUT_MS = 3000L
    }
}
