package com.seraphim.dailyboard.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class DeleteColumnTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `deleteColumn archives its tasks and removes the column`() = runTest {
        val (store, _) = openedStore()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "A")
        store.createTask(doing, "B")

        store.deleteColumn(doing)

        val snapshot = store.snapshot()
        assertEquals(listOf("开始", "结束"), snapshot.columns.map { it.name })
        assertEquals(emptyList(), snapshot.tasks.values.flatten())
        assertEquals(listOf("A", "B"), snapshot.archivedTasks.sortedBy { it.createdAt }.map { it.title })
        assertNotNull(snapshot.archivedTasks.first().archivedAt)
    }

    @Test
    fun `deleteColumn emits delete and archive deltas in one batch`() = runTest {
        val (store, repository) = openedStore()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "A")

        val baseline = repository.appliedBatches.size
        store.deleteColumn(doing)

        val batch = repository.appliedBatches.drop(baseline).single()
        assertEquals(1, batch.filterIsInstance<BoardDelta.DeleteColumn>().size)
        assertEquals(1, batch.filterIsInstance<BoardDelta.UpsertTask>().size)
    }

    @Test
    fun `start and end columns cannot be deleted`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        assertFailsWith<BoardError.ColumnNotDeletable> { store.deleteColumn(startId) }
        assertFailsWith<BoardError.ColumnNotDeletable> { store.deleteColumn(endId) }
    }

    @Test
    fun `deleting the only middle column leaves ranks dense`() = runTest {
        val (store, _) = openedStore()
        val a = store.createColumn("A").columns[1].id
        store.createColumn("B")
        store.deleteColumn(a)
        val snapshot = store.snapshot()
        assertEquals(listOf(0, 1, 2), snapshot.columns.map { it.rank })
        assertEquals(ColumnKind.MIDDLE, snapshot.columns[1].kind)
    }

    @Test
    fun `archived tasks survive without their column`() = runTest {
        val (store, _) = openedStore()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "A")
        store.deleteColumn(doing)
        // 恢复应回到 START 列（Task 7 的命令，此处仅断言归档任务存在且无活动列引用）
        val snapshot = store.snapshot()
        assertNull(snapshot.tasks.values.flatten().firstOrNull { it.title == "A" })
        assertEquals("A", snapshot.archivedTasks.single().title)
    }
}
