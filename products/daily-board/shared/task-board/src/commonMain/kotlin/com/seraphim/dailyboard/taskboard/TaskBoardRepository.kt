package com.seraphim.dailyboard.taskboard

interface TaskBoardRepository {
    suspend fun open(): PersistedBoard
    suspend fun apply(deltas: List<BoardDelta>): Unit
}

data class PersistedBoard(
    val columns: List<Column>,
    val tasks: List<TaskItem>,
    val nextColumnId: Long,
    val nextTaskId: Long,
)

sealed interface BoardDelta {
    data class UpsertColumn(val column: Column) : BoardDelta
    data class DeleteColumn(val id: ColumnId) : BoardDelta
    data class UpsertTask(val task: TaskItem) : BoardDelta
    data class DeleteTask(val id: TaskId) : BoardDelta
}
