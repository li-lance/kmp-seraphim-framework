package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class WebTaskBoardAdapterTest {
    private val q = '"'.toString()

    private class InMemoryRepository : TaskBoardRepository {
        val columns = mutableListOf<Column>()
        val tasks = mutableListOf<TaskItem>()

        override suspend fun open(): PersistedBoard = PersistedBoard(columns.toList(), tasks.toList(), 1L, 1L)

        override suspend fun apply(deltas: List<BoardDelta>) {
            deltas.forEach { delta ->
                when (delta) {
                    is BoardDelta.UpsertColumn -> {
                        columns.removeAll { it.id == delta.column.id }
                        columns += delta.column
                    }
                    is BoardDelta.DeleteColumn -> columns.removeAll { it.id == delta.id }
                    is BoardDelta.UpsertTask -> {
                        tasks.removeAll { it.id == delta.task.id }
                        tasks += delta.task
                    }
                    is BoardDelta.DeleteTask -> tasks.removeAll { it.id == delta.id }
                }
            }
        }
    }

    @Test
    fun `subscribers receive JSON snapshots after commands`() = runTest {
        val store = TaskBoardStore(InMemoryRepository())
        store.open()
        val bridge = SnapshotBridge(store)
        val received = mutableListOf<String>()
        val job = bridge.subscribe({ received += it }, backgroundScope)

        store.createTask(store.snapshot().columns.first().id, "Buy milk")

        testScheduler.runCurrent()
        assertTrue(received.isNotEmpty(), "no snapshot delivered")
        val latest = received.last()
        assertTrue(latest.contains(q + "columns" + q + ":["), latest)
        assertTrue(latest.contains(q + "title" + q + ":" + q + "Buy milk" + q), latest)
        assertTrue(latest.contains(q + "kind" + q + ":" + q + "START" + q), latest)
        job.cancel()
    }

    @Test
    fun `JSON escapes newlines in titles`() = runTest {
        val store = TaskBoardStore(InMemoryRepository())
        store.open()
        val bridge = SnapshotBridge(store)
        val received = mutableListOf<String>()
        val job = bridge.subscribe({ received += it }, backgroundScope)

        store.createTask(store.snapshot().columns.first().id, "Line1\nLine2")

        testScheduler.runCurrent()
        val latest = received.last()
        assertTrue(latest.contains(q + "title" + q + ":" + q + "Line1\\nLine2" + q), latest)
        job.cancel()
    }

    @Test
    fun `disposed subscriptions stop receiving snapshots`() = runTest {
        val store = TaskBoardStore(InMemoryRepository())
        store.open()
        val bridge = SnapshotBridge(store)
        val received = mutableListOf<String>()
        val job = bridge.subscribe({ received += it }, backgroundScope)
        testScheduler.runCurrent()
        val before = received.size

        job.cancel()
        store.createTask(store.snapshot().columns.first().id, "A")
        testScheduler.runCurrent()
        assertEquals(before, received.size)
    }
}