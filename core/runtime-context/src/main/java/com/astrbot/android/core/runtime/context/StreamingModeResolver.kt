package com.astrbot.android.core.runtime.context

enum class RuntimeStreamingMode {
    NON_STREAM,
    NATIVE_STREAM,
    PSEUDO_STREAM,
}

object StreamingModeResolver {

    fun resolve(ctx: ResolvedRuntimeContext): RuntimeStreamingMode {
        return resolve(ctx.deliveryPolicy.streamingEnabled, ctx.provider)
    }

    fun resolve(streamingEnabled: Boolean, provider: RuntimeProviderSnapshot): RuntimeStreamingMode {
        return when {
            !streamingEnabled -> RuntimeStreamingMode.NON_STREAM
            provider.supportsStreaming -> RuntimeStreamingMode.NATIVE_STREAM
            else -> RuntimeStreamingMode.PSEUDO_STREAM
        }
    }
}
