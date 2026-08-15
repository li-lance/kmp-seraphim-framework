package com.seraphim.dailyboard.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ArchiveRestoreTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `only end-column tasks can be archived`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val active = store.createTask(startId, "A").tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.NotArchivable> { store.archiveTask(active) }
        assertEquals(baseline, repository.appliedBatches.size)
        val done = store.createTask(endId, "B").tasks.getValue(endId).single().id
        store.archiveTask(done)
        val archived = store.snapshot().archivedTasks.single()
        assertEquals("B", archived.title)
        assertEquals(0, archived.rank)
        assertNull(store.snapshot().tasks.getValue(endId).firstOrNull { it.id == done })
    }

    @Test
    fun `archiving renumbers the remaining end-column tasks`() = runTest {
        val (store, _) = openedStore()
        val endId = store.snapshot().columns.last().id
        store.createTask(endId, "A")
        val b = store.createTask(endId, "B").tasks.getValue(endId).last().id
        store.archiveTask(b)
        assertEquals(listOf(0), store.snapshot().tasks.getValue(endId).map { it.rank })
    }

    @Test
    fun `restore returns the task to the end of the start column`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        store.createTask(startId, "existing")
        val id = store.createTask(endId, "done").tasks.getValue(endId).single().id
        store.archiveTask(id)
        store.restoreTask(id)
        val snapshot = store.snapshot()
        assertNull(snapshot.archivedTasks.firstOrNull { it.id == id })
        val restored = snapshot.tasks.getValue(startId).last()
        assertEquals("done", restored.title)
        assertNull(restored.archivedAt)
        assertEquals(1, restored.rank)
    }

    @Test
    fun `archive and restore fail cleanly on wrong states`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val done = store.createTask(endId, "done").tasks.getValue(endId).single().id
        val active = store.createTask(startId, "A").tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.TaskNotFound> { store.archiveTask(TaskId(999L)) }
        assertFailsWith<BoardError.TaskNotFound> { store.restoreTask(TaskId(999L)) }
        assertFailsWith<BoardError.NotRestorable> { store.restoreTask(active) }
        assertEquals(baseline, repository.appliedBatches.size)
        store.archiveTask(done)
        assertFailsWith<BoardError.NotRestorable> { store.archiveTask(done) }
    }

    @Test
    fun `archived tasks are ordered newest first`() = runTest {
        val (store, _) = openedStore()
        val endId = store.snapshot().columns.last().id
        store.createTask(endId, "A")
        store.createTask(endId, "B")
        val ids = store.snapshot().tasks.getValue(endId).map { it.id }
        store.archiveTask(ids[0])
        store.archiveTask(ids[1])
        val archived = store.snapshot().archivedTasks
        assertEquals(setOf("A", "B"), archived.map { it.title }.toSet())
        assertEquals(2, archived.size)
    }
}
