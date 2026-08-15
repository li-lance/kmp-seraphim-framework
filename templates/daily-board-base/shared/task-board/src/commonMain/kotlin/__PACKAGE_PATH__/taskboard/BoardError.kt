package __PACKAGE_NAME__.taskboard

sealed class BoardError(message: String) : RuntimeException(message) {
    data class InvalidTitle(val title: String) : BoardError("Invalid task title: $title")
    data class InvalidColumnName(val name: String) : BoardError("Invalid column name: $name")
    data class InvalidNotes(val notes: String) : BoardError("Invalid notes")
    data class NotUpdatable(val id: TaskId) : BoardError("Task ${id.value} is archived and cannot be updated")
    data class ColumnNotFound(val id: ColumnId) : BoardError("Column not found: ${id.value}")
    data class TaskNotFound(val id: TaskId) : BoardError("Task not found: ${id.value}")
    data class NotArchivable(val id: TaskId) : BoardError("Task ${id.value} is not in the end column")
    data class NotRestorable(val id: TaskId) : BoardError("Task ${id.value} is not archived")
    data class ColumnNotDeletable(val id: ColumnId) : BoardError("Start/end column cannot be deleted: ${id.value}")
    data class ColumnNotMovable(val id: ColumnId) : BoardError("Start/end column cannot be moved: ${id.value}")
    data class IndexOutOfRange(val index: Int, val size: Int) : BoardError("Index $index out of range for size $size")
    class NotOpen : BoardError("Store is not open")
}

class StorageError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
