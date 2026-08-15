package __PACKAGE_NAME__.taskboard

import kotlin.time.Instant

@JvmInline
value class ColumnId(val value: Long)

@JvmInline
value class TaskId(val value: Long)

@JvmInline
value class EpochDay(val value: Long) {
    val epochMillis: Long get() = value * 86_400_000L

    companion object {
        fun fromInstant(instant: Instant): EpochDay =
            EpochDay(Math.floorDiv(instant.toEpochMilliseconds(), 86_400_000L))
    }
}

enum class ColumnKind { START, MIDDLE, END }

data class Column(
    val id: ColumnId,
    val name: String,
    val kind: ColumnKind,
    val rank: Int,
)

data class TaskItem(
    val id: TaskId,
    val columnId: ColumnId,
    val title: String,
    val notes: String?,
    val dueDate: EpochDay?,
    val rank: Int,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

data class BoardSnapshot(
    val columns: List<Column>,
    val tasks: Map<ColumnId, List<TaskItem>>,
    val archivedTasks: List<TaskItem>,
)

const val MAX_COLUMN_NAME_LENGTH = 80
const val MAX_TITLE_LENGTH = 500
const val MAX_NOTES_LENGTH = 2000
const val START_COLUMN_NAME = "开始"
const val END_COLUMN_NAME = "结束"

fun columnKindAt(index: Int, size: Int): ColumnKind = when {
    size <= 1 -> ColumnKind.START
    index == 0 -> ColumnKind.START
    index == size - 1 -> ColumnKind.END
    else -> ColumnKind.MIDDLE
}
