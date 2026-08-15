package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class MoveTaskTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `moving into the end column completes the task`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val id = store.createTask(startId, "A").tasks.getValue(startId).single().id
        store.moveTask(id, endId, 0)
        val snapshot = store.snapshot()
        assertEquals(emptyList(), snapshot.tasks.getValue(startId))
        assertEquals(listOf("A"), snapshot.tasks.getValue(endId).map { it.title })
    }

    @Test
    fun `moving out of the end column reopens the task`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val id = store.createTask(endId, "done").tasks.getValue(endId).single().id
        store.moveTask(id, startId, 0)
        assertEquals(listOf("done"), store.snapshot().tasks.getValue(startId).map { it.title })
    }

    @Test
    fun `cross-column move renumbers both columns densely`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(startId, "A")
        store.createTask(startId, "B")
        store.createTask(startId, "C")
        val id = store.snapshot().tasks.getValue(startId)[1].id
        store.moveTask(id, doing, 0)
        val snapshot = store.snapshot()
        assertEquals(listOf("A", "C"), snapshot.tasks.getValue(startId).map { it.title })
        assertEquals(listOf(0, 1), snapshot.tasks.getValue(startId).map { it.rank })
        assertEquals(listOf("B"), snapshot.tasks.getValue(doing).map { it.title })
        assertEquals(listOf(0), snapshot.tasks.getValue(doing).map { it.rank })
    }

    @Test
    fun `same-column reorder keeps a stable total order`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        store.createTask(startId, "B")
        store.createTask(startId, "C")
        val id = store.snapshot().tasks.getValue(startId).last().id
        store.moveTask(id, startId, 0)
        assertEquals(listOf("C", "A", "B"), store.snapshot().tasks.getValue(startId).map { it.title })
    }

    @Test
    fun `moving a task to its own position is a no-op`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val id = store.snapshot().tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        store.moveTask(id, startId, 0)
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `moveTask rejects unknown tasks, columns and bad indexes`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val id = store.snapshot().tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.TaskNotFound> { store.moveTask(TaskId(999L), startId, 0) }
        assertFailsWith<BoardError.ColumnNotFound> { store.moveTask(id, ColumnId(999L), 0) }
        assertFailsWith<BoardError.IndexOutOfRange> { store.moveTask(id, startId, 5) }
        assertEquals(baseline, repository.appliedBatches.size)
    }
}
