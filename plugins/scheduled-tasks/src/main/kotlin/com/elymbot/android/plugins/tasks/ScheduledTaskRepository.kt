package com.elymbot.android.plugins.tasks

internal interface ScheduledTaskRepository {
    fun snapshot(): TaskStoreState

    fun <T> transact(block: (TaskStoreState) -> Pair<TaskStoreState, T>): T
}

internal class InMemoryScheduledTaskRepository(
    initialState: TaskStoreState = TaskStoreState(),
) : ScheduledTaskRepository {
    private var state = initialState

    @Synchronized
    override fun snapshot(): TaskStoreState = state

    @Synchronized
    override fun <T> transact(block: (TaskStoreState) -> Pair<TaskStoreState, T>): T {
        val (nextState, result) = block(state)
        state = nextState
        return result
    }
}
