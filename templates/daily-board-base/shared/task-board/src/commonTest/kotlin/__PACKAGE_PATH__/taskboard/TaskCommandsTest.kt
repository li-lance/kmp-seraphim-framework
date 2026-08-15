package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

class TaskCommandsTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `createTask appends at the end of its column with a dense rank`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "  A  ")
        store.createTask(startId, "B")
        val snapshot = store.snapshot()
        assertEquals(listOf("A", "B"), snapshot.tasks.getValue(startId).map { it.title })
        assertEquals(listOf(0, 1), snapshot.tasks.getValue(startId).map { it.rank })
    }

    @Test
    fun `createTask normalizes notes and stores due dates`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val day = EpochDay(20_000L)
        store.createTask(startId, "A", notes = "  note  ", dueDate = day)
        val task = store.snapshot().tasks.getValue(startId).single()
        assertEquals("note", task.notes)
        assertEquals(day, task.dueDate)
        store.createTask(startId, "B", notes = "   ")
        assertNull(store.snapshot().tasks.getValue(startId).last().notes)
    }

    @Test
    fun `createTask rejects invalid input without writing`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.InvalidTitle> { store.createTask(startId, "  ") }
        assertFailsWith<BoardError.InvalidTitle> { store.createTask(startId, "x".repeat(MAX_TITLE_LENGTH + 1)) }
        assertFailsWith<BoardError.InvalidNotes> { store.createTask(startId, "A", notes = "x".repeat(MAX_NOTES_LENGTH + 1)) }
        assertFailsWith<BoardError.ColumnNotFound> { store.createTask(ColumnId(999L), "A") }
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `updateTask changes only provided fields`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val id = store.createTask(startId, "A", notes = "n", dueDate = EpochDay(10L)).tasks.getValue(startId).single().id
        store.updateTask(id, title = "A2")
        var task = store.snapshot().tasks.getValue(startId).single()
        assertEquals("A2", task.title)
        assertEquals("n", task.notes)
        assertEquals(EpochDay(10L), task.dueDate)
        store.updateTask(id, notes = "")
        task = store.snapshot().tasks.getValue(startId).single()
        assertNull(task.notes)
    }

    @Test
    fun `updateTask clears due date only when asked`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val id = store.createTask(startId, "A", dueDate = EpochDay(10L)).tasks.getValue(startId).single().id
        store.updateTask(id, title = "A1")
        assertEquals(EpochDay(10L), store.snapshot().tasks.getValue(startId).single().dueDate)
        store.updateTask(id, clearDueDate = true)
        assertNull(store.snapshot().tasks.getValue(startId).single().dueDate)
        store.updateTask(id, dueDate = EpochDay(11L))
        assertEquals(EpochDay(11L), store.snapshot().tasks.getValue(startId).single().dueDate)
    }

    @Test
    fun `updateTask with no changes is a no-op without writing`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val baseline = repository.appliedBatches.size
        store.updateTask(store.snapshot().tasks.getValue(startId).single().id)
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `updateTask fails for unknown and archived tasks`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.TaskNotFound> { store.updateTask(TaskId(999L), title = "X") }
        assertEquals(baseline, repository.appliedBatches.size)
        val archived = store.snapshot().tasks.getValue(startId).single().copy(archivedAt = Clock.System.now())
        val snapshot = store.snapshot()
        val archivedStore = TaskBoardStore(
            PersistedRepository(
                columns = snapshot.columns,
                tasks = listOf(archived),
                nextColumnId = 2L,
                nextTaskId = 2L,
            ),
        )
        archivedStore.open()
        assertFailsWith<BoardError.NotUpdatable> { archivedStore.updateTask(archived.id, title = "X") }
    }
}
