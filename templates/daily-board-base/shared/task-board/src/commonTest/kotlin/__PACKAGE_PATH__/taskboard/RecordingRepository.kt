package __PACKAGE_NAME__.taskboard

class RecordingRepository : TaskBoardRepository {
    val appliedBatches = mutableListOf<List<BoardDelta>>()

    override suspend fun open(): PersistedBoard = PersistedBoard(emptyList(), emptyList(), 1L, 1L)

    override suspend fun apply(deltas: List<BoardDelta>) {
        appliedBatches += deltas
    }
}
