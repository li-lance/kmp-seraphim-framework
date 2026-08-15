package com.seraphim.dailyboard.taskboard

import kotlin.time.Clock
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

    suspend fun createColumn(name: String): BoardSnapshot = mutex.withLock {
        requireOpen()
        val normalized = validateColumnName(name)
        val insertionIndex = columns.size - 1
        val column = Column(ColumnId(nextColumnId++), normalized, ColumnKind.MIDDLE, rank = insertionIndex)
        val newColumns = renumberColumns(columns.toMutableList().apply { add(insertionIndex, column) })
        repository.apply(columnDeltas(columns, newColumns))
        columns = newColumns.toMutableList()
        publish()
    }

    suspend fun renameColumn(id: ColumnId, name: String): BoardSnapshot = mutex.withLock {
        requireOpen()
        val normalized = validateColumnName(name)
        val index = columns.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.ColumnNotFound(id)
        val updated = columns[index].copy(name = normalized)
        repository.apply(listOf(BoardDelta.UpsertColumn(updated)))
        columns[index] = updated
        publish()
    }

    suspend fun moveColumn(id: ColumnId, toIndex: Int): BoardSnapshot = mutex.withLock {
        requireOpen()
        val fromIndex = columns.indexOfFirst { it.id == id }
        if (fromIndex < 0) throw BoardError.ColumnNotFound(id)
        if (columns[fromIndex].kind != ColumnKind.MIDDLE) throw BoardError.ColumnNotMovable(id)
        if (toIndex < 1 || toIndex > columns.size - 2) throw BoardError.IndexOutOfRange(toIndex, columns.size)
        val reordered = columns.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        val newColumns = renumberColumns(reordered)
        repository.apply(columnDeltas(columns, newColumns))
        columns = newColumns.toMutableList()
        publish()
    }

    suspend fun deleteColumn(id: ColumnId): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = columns.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.ColumnNotFound(id)
        if (columns[index].kind != ColumnKind.MIDDLE) throw BoardError.ColumnNotDeletable(id)
        val now = Clock.System.now()
        val archived = tasks.filter { it.columnId == id }.map { it.copy(archivedAt = now, rank = 0) }
        val newColumns = renumberColumns(columns.filterNot { it.id == id })
        val newTasks = tasks.filterNot { it.columnId == id } + archived
        repository.apply(
            listOf(BoardDelta.DeleteColumn(id)) +
                archived.map { BoardDelta.UpsertTask(it) } +
                columnDeltas(columns, newColumns),
        )
        columns = newColumns.toMutableList()
        tasks = newTasks.toMutableList()
        publish()
    }

    suspend fun createTask(
        columnId: ColumnId,
        title: String,
        notes: String? = null,
        dueDate: EpochDay? = null,
    ): BoardSnapshot = mutex.withLock {
        requireOpen()
        val normalizedTitle = validateTitle(title)
        val normalizedNotes = validateNotes(notes)
        if (columns.none { it.id == columnId }) throw BoardError.ColumnNotFound(columnId)
        val rank = tasks.count { it.archivedAt == null && it.columnId == columnId }
        val task = TaskItem(
            id = TaskId(nextTaskId++),
            columnId = columnId,
            title = normalizedTitle,
            notes = normalizedNotes,
            dueDate = dueDate,
            rank = rank,
            createdAt = Clock.System.now(),
            archivedAt = null,
        )
        repository.apply(listOf(BoardDelta.UpsertTask(task)))
        tasks.add(task)
        publish()
    }

    suspend fun updateTask(
        id: TaskId,
        title: String? = null,
        notes: String? = null,
        dueDate: EpochDay? = null,
        clearDueDate: Boolean = false,
    ): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.TaskNotFound(id)
        val current = tasks[index]
        if (current.archivedAt != null) throw BoardError.NotUpdatable(id)
        if (title == null && notes == null && dueDate == null && !clearDueDate) return@withLock publish()
        val newTitle = title?.let { validateTitle(it) } ?: current.title
        val newNotes = if (notes != null) validateNotes(notes) else current.notes
        val newDueDate = if (clearDueDate) null else dueDate ?: current.dueDate
        val updated = current.copy(title = newTitle, notes = newNotes, dueDate = newDueDate)
        repository.apply(listOf(BoardDelta.UpsertTask(updated)))
        tasks[index] = updated
        publish()
    }

    suspend fun moveTask(id: TaskId, toColumnId: ColumnId, toIndex: Int): BoardSnapshot = mutex.withLock {
        requireOpen()
        val taskIndex = tasks.indexOfFirst { it.id == id }
        if (taskIndex < 0) throw BoardError.TaskNotFound(id)
        val task = tasks[taskIndex]
        if (task.archivedAt != null) throw BoardError.NotUpdatable(id)
        if (columns.none { it.id == toColumnId }) throw BoardError.ColumnNotFound(toColumnId)
        val fromColumnId = task.columnId
        val fromList = columnTasks(fromColumnId).filterNot { it.id == id }
        val targetList = (if (toColumnId == fromColumnId) fromList else columnTasks(toColumnId)).toMutableList()
        if (toIndex < 0 || toIndex > targetList.size) throw BoardError.IndexOutOfRange(toIndex, targetList.size + 1)
        if (toColumnId == fromColumnId && toIndex == task.rank) return@withLock publish()
        targetList.add(toIndex, task.copy(columnId = toColumnId))
        val targetRenumbered = targetList.mapIndexed { index, item -> item.copy(rank = index) }
        val fromRenumbered =
            if (toColumnId == fromColumnId) emptyList() else fromList.mapIndexed { index, item -> item.copy(rank = index) }
        val newTasks = tasks.toMutableList()
        newTasks.removeAll { it.archivedAt == null && (it.columnId == fromColumnId || it.columnId == toColumnId) }
        newTasks.addAll(fromRenumbered + targetRenumbered)
        repository.apply(
            fromRenumbered.map { BoardDelta.UpsertTask(it) } + targetRenumbered.map { BoardDelta.UpsertTask(it) },
        )
        tasks = newTasks
        publish()
    }

    suspend fun archiveTask(id: TaskId): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.TaskNotFound(id)
        val task = tasks[index]
        if (task.archivedAt != null) throw BoardError.NotRestorable(id)
        val endColumnId = columns.last().id
        if (task.columnId != endColumnId) throw BoardError.NotArchivable(id)
        val archived = task.copy(archivedAt = Clock.System.now(), rank = 0)
        val renumbered = columnTasks(endColumnId).filterNot { it.id == id }
            .mapIndexed { position, item -> item.copy(rank = position) }
        val newTasks = tasks.toMutableList()
        newTasks.removeAll { it.archivedAt == null && it.columnId == endColumnId }
        newTasks.addAll(renumbered + archived)
        repository.apply(listOf(BoardDelta.UpsertTask(archived)) + renumbered.map { BoardDelta.UpsertTask(it) })
        tasks = newTasks
        publish()
    }

    suspend fun restoreTask(id: TaskId): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.TaskNotFound(id)
        val task = tasks[index]
        if (task.archivedAt == null) throw BoardError.NotRestorable(id)
        val startColumnId = columns.first().id
        val rank = tasks.count { it.archivedAt == null && it.columnId == startColumnId }
        val restored = task.copy(columnId = startColumnId, rank = rank, archivedAt = null)
        repository.apply(listOf(BoardDelta.UpsertTask(restored)))
        tasks[index] = restored
        publish()
    }

    private fun columnTasks(columnId: ColumnId): List<TaskItem> =
        tasks.filter { it.archivedAt == null && it.columnId == columnId }
            .sortedWith(compareBy({ it.rank }, { it.id.value }))

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

    private fun validateColumnName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty() || name.length > MAX_COLUMN_NAME_LENGTH) throw BoardError.InvalidColumnName(raw)
        return name
    }

    private fun validateTitle(raw: String): String {
        val title = raw.trim()
        if (title.isEmpty() || title.length > MAX_TITLE_LENGTH) throw BoardError.InvalidTitle(raw)
        return title
    }

    private fun validateNotes(raw: String?): String? {
        if (raw == null) return null
        val notes = raw.trim()
        if (notes.length > MAX_NOTES_LENGTH) throw BoardError.InvalidNotes(raw)
        return notes.ifEmpty { null }
    }

    private fun renumberColumns(list: List<Column>): List<Column> =
        list.mapIndexed { index, column -> column.copy(rank = index, kind = columnKindAt(index, list.size)) }

    private fun columnDeltas(old: List<Column>, new: List<Column>): List<BoardDelta> =
        new.filterNot { column -> old.contains(column) }.map { BoardDelta.UpsertColumn(it) }
}
