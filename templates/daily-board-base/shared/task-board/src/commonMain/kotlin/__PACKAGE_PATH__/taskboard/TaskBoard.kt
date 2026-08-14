package __PACKAGE_NAME__.taskboard

data class TaskItem(val id: Long, val title: String)
data class TaskBoardSnapshot(val tasks: List<TaskItem>)

class TaskBoard {
    private val tasks = mutableListOf<TaskItem>()

    fun createTask(title: String): TaskBoardSnapshot {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "Task title must not be blank" }
        tasks += TaskItem(id = tasks.size.toLong() + 1, title = normalized)
        return snapshot()
    }

    fun snapshot(): TaskBoardSnapshot = TaskBoardSnapshot(tasks.toList())
}
