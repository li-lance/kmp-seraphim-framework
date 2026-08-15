package com.seraphim.dailyboard.taskboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskBoardStore(private val repository: TaskBoardRepository) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<BoardSnapshot?>(null)
    private var open = false
    private var nextColumnId = 1L
    private var nextTaskId = 1L
    private var columns = mutableListOf<Column>()
    private var tasks = mutableListOf<TaskItem>()

    fun observe(): StateFlow<BoardSnapshot?> = _snapshot

    suspend fun open() = mutex.withLock {
        if (open) return@withLock
        val persisted = repository.open()
        nextColumnId = persisted.nextColumnId
        nextTaskId = persisted.nextTaskId
        columns = persisted.columns
            .sortedBy { it.rank }
            .mapIndexed { index, column -> column.copy(kind = columnKindAt(index, persisted.columns.size)) }
            .toMutableList()
        if (columns.isEmpty()) {
            val start = Column(ColumnId(nextColumnId++), START_COLUMN_NAME, ColumnKind.START, 0)
            val end = Column(ColumnId(nextColumnId++), END_COLUMN_NAME, ColumnKind.END, 1)
            repository.apply(listOf(BoardDelta.UpsertColumn(start), BoardDelta.UpsertColumn(end)))
            columns.addAll(listOf(start, end))
        }
        val columnIds = columns.map { it.id }.toSet()
        val orphan = persisted.tasks.firstOrNull { it.archivedAt == null && it.columnId !in columnIds }
        if (orphan != null) throw StorageError("Orphan task ${orphan.id.value} references a missing column")
        tasks = persisted.tasks.toMutableList()
        open = true
        publish()
    }

    suspend fun snapshot(): BoardSnapshot = mutex.withLock {
        requireOpen()
        _snapshot.value!!
    }

    private fun requireOpen() {
        if (!open) throw BoardError.NotOpen()
    }

    private fun publish(): BoardSnapshot = buildSnapshot().also { _snapshot.value = it }

    private fun buildSnapshot(): BoardSnapshot {
        val active = tasks.filter { it.archivedAt == null }.groupBy { it.columnId }
        val ordered = columns.associateWith { column ->
            active[column.id].orEmpty().sortedWith(compareBy({ it.rank }, { it.id.value }))
        }.mapKeys { it.key.id }
        return BoardSnapshot(
            columns = columns.toList(),
            tasks = ordered,
            archivedTasks = tasks.filter { it.archivedAt != null }.sortedByDescending { it.archivedAt },
        )
    }
}
