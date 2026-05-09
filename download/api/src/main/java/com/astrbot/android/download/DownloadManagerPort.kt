package com.astrbot.android.download

import kotlinx.coroutines.flow.Flow

interface DownloadManagerPort {
    suspend fun enqueue(request: DownloadRequest): String

    fun observe(taskKey: String): Flow<DownloadTaskRecord?>

    suspend fun awaitCompletion(
        taskKey: String,
        onUpdate: (DownloadTaskRecord) -> Unit = {},
    ): DownloadTaskRecord

    suspend fun resume(taskKey: String)

    suspend fun cancel(taskKey: String)
}
