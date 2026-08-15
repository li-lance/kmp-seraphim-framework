package __PACKAGE_NAME__.localdata

import app.cash.sqldelight.db.SqlDriver
import kotlin.time.Instant
import __PACKAGE_NAME__.taskboard.BoardDelta
import __PACKAGE_NAME__.taskboard.Column
import __PACKAGE_NAME__.taskboard.ColumnId
import __PACKAGE_NAME__.taskboard.EpochDay
import __PACKAGE_NAME__.taskboard.PersistedBoard
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardRepository
import __PACKAGE_NAME__.taskboard.TaskId
import __PACKAGE_NAME__.taskboard.TaskItem
import __PACKAGE_NAME__.taskboard.columnKindAt

class SqlDelightTaskBoardRepository(driver: SqlDriver) : TaskBoardRepository {

    private val queries = TaskBoardDatabase(driver).boardQueries

    override suspend fun open(): PersistedBoard {
        val columnRows = queries.selectColumns().executeAsList()
        val columns = columnRows.sortedBy { it.rank }.mapIndexed { index, row ->
            Column(ColumnId(row.id), row.name, columnKindAt(index, columnRows.size), row.rank.toInt())
        }
        val taskRows = queries.selectTasks().executeAsList()
        val tasks = taskRows.map { row ->
            TaskItem(
                id = TaskId(row.id),
                columnId = ColumnId(row.column_id ?: 0L),
                title = row.title,
                notes = row.notes,
                dueDate = row.due_date?.let(::EpochDay),
                rank = row.rank.toInt(),
                createdAt = Instant.fromEpochMilliseconds(row.created_at),
                archivedAt = row.archived_at?.let(Instant::fromEpochMilliseconds),
            )
        }
        return PersistedBoard(
            columns = columns,
            tasks = tasks,
            nextColumnId = (columnRows.maxOfOrNull { it.id } ?: 0L) + 1,
            nextTaskId = (taskRows.maxOfOrNull { it.id } ?: 0L) + 1,
        )
    }

    override suspend fun apply(deltas: List<BoardDelta>) {
        try {
            queries.transaction {
                deltas.forEach { delta ->
                    when (delta) {
                        is BoardDelta.UpsertColumn -> queries.upsertColumn(
                            id = delta.column.id.value,
                            name = delta.column.name,
                            rank = delta.column.rank.toLong(),
                        )
                        is BoardDelta.DeleteColumn -> queries.deleteColumnRow(delta.id.value)
                        is BoardDelta.UpsertTask -> queries.upsertTask(
                            id = delta.task.id.value,
                            column_id = delta.task.columnId.value.takeUnless { delta.task.archivedAt != null },
                            title = delta.task.title,
                            notes = delta.task.notes,
                            due_date = delta.task.dueDate?.value,
                            rank = delta.task.rank.toLong(),
                            created_at = delta.task.createdAt.toEpochMilliseconds(),
                            archived_at = delta.task.archivedAt?.toEpochMilliseconds(),
                        )
                        is BoardDelta.DeleteTask -> queries.deleteTaskRow(delta.id.value)
                    }
                }
            }
        } catch (failure: Exception) {
            throw StorageError("Failed to persist board changes", failure)
        }
    }
}
