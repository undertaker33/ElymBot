package com.astrbot.android.runtime

import android.content.Context
import android.content.Intent
import android.os.Build
import com.astrbot.android.data.NapCatBridgeRepository

object ContainerBridgeController {
    fun start(context: Context) {
        runCatching {
            NapCatBridgeRepository.markStarting()
            RuntimeLogRepository.append("Bridge start requested")
            val intent = Intent(context, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_START_BRIDGE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { error ->
            NapCatBridgeRepository.markError("启动失败：${error.message ?: error.javaClass.simpleName}")
            RuntimeLogRepository.append("Bridge start failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun stop(context: Context) {
        runCatching {
            RuntimeLogRepository.append("Bridge stop requested")
            val intent = Intent(context, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_STOP_BRIDGE
            }
            context.startService(intent)
        }.onFailure { error ->
            NapCatBridgeRepository.markError("停止失败：${error.message ?: error.javaClass.simpleName}")
            RuntimeLogRepository.append("Bridge stop failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun check(context: Context) {
        runCatching {
            NapCatBridgeRepository.markChecking()
            RuntimeLogRepository.append("Bridge health check requested")
            val intent = Intent(context, ContainerBridgeService::class.java).apply {
                action = ContainerBridgeService.ACTION_CHECK_BRIDGE
            }
            context.startService(intent)
        }.onFailure { error ->
            NapCatBridgeRepository.markError("检查失败：${error.message ?: error.javaClass.simpleName}")
            RuntimeLogRepository.append("Bridge check failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}
