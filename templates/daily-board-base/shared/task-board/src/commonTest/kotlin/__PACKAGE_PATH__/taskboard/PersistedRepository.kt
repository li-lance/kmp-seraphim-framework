package __PACKAGE_NAME__.taskboard

class PersistedRepository(
    private val columns: List<Column>,
    private val tasks: List<TaskItem>,
    private val nextColumnId: Long,
    private val nextTaskId: Long,
) : TaskBoardRepository {
    val appliedBatches = mutableListOf<List<BoardDelta>>()

    override suspend fun open(): PersistedBoard = PersistedBoard(columns, tasks, nextColumnId, nextTaskId)

    override suspend fun apply(deltas: List<BoardDelta>) {
        appliedBatches += deltas
    }
}
