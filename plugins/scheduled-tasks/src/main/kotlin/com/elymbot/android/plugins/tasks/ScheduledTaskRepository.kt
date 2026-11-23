package com.elymbot.android.plugins.tasks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface ScheduledTaskRepository {
    val dataRevision: StateFlow<Long>

    fun snapshot(): TaskStoreState

    fun <T> transact(block: (TaskStoreState) -> Pair<TaskStoreState, T>): T
}

internal class InMemoryScheduledTaskRepository(
    initialState: TaskStoreState = TaskStoreState(),
) : ScheduledTaskRepository {
    private val mutableDataRevision = MutableStateFlow(0L)
    override val dataRevision: StateFlow<Long> = mutableDataRevision.asStateFlow()
    private var state = initialState

    @Synchronized
    override fun snapshot(): TaskStoreState = state

    @Synchronized
    override fun <T> transact(block: (TaskStoreState) -> Pair<TaskStoreState, T>): T {
        val (nextState, result) = block(state)
        val changed = nextState != state
        state = nextState
        if (changed) mutableDataRevision.value += 1L
        return result
    }
}
