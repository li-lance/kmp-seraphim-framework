package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ColumnCommandsTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `createColumn inserts before the end column`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("Doing")
        val snapshot = store.snapshot()
        assertEquals(listOf("开始", "Doing", "结束"), snapshot.columns.map { it.name })
        assertEquals(ColumnKind.MIDDLE, snapshot.columns[1].kind)
    }

    @Test
    fun `createColumn keeps ranks dense`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("A")
        store.createColumn("B")
        assertEquals(listOf(0, 1, 2, 3), store.snapshot().columns.map { it.rank })
    }

    @Test
    fun `createColumn rejects blank and overlong names without writing`() = runTest {
        val (store, repository) = openedStore()
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.InvalidColumnName> { store.createColumn("   ") }
        assertFailsWith<BoardError.InvalidColumnName> { store.createColumn("x".repeat(MAX_COLUMN_NAME_LENGTH + 1)) }
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `renameColumn works on fixed columns and trims`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.renameColumn(startId, "  Inbox  ")
        assertEquals("Inbox", store.snapshot().columns.first().name)
    }

    @Test
    fun `renameColumn fails for unknown columns without writing`() = runTest {
        val (store, repository) = openedStore()
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.ColumnNotFound> { store.renameColumn(ColumnId(999L), "X") }
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `moveColumn reorders middle columns and renumbers ranks`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("A")
        val b = store.createColumn("B").columns[2].id
        store.moveColumn(b, 1)
        val snapshot = store.snapshot()
        assertEquals(listOf("开始", "B", "A", "结束"), snapshot.columns.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), snapshot.columns.map { it.rank })
    }

    @Test
    fun `start and end columns cannot move`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("A")
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        assertFailsWith<BoardError.ColumnNotMovable> { store.moveColumn(startId, 1) }
        assertFailsWith<BoardError.ColumnNotMovable> { store.moveColumn(endId, 1) }
    }

    @Test
    fun `moveColumn rejects out-of-range targets`() = runTest {
        val (store, _) = openedStore()
        val a = store.createColumn("A").columns[1].id
        assertFailsWith<BoardError.IndexOutOfRange> { store.moveColumn(a, 0) }
        assertFailsWith<BoardError.IndexOutOfRange> { store.moveColumn(a, 2) }
    }
}
