@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package __PACKAGE_NAME__.localdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import __PACKAGE_NAME__.taskboard.BoardDelta
import __PACKAGE_NAME__.taskboard.Column
import __PACKAGE_NAME__.taskboard.ColumnId
import __PACKAGE_NAME__.taskboard.ColumnKind
import __PACKAGE_NAME__.taskboard.EpochDay
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardStore
import __PACKAGE_NAME__.taskboard.TaskId
import __PACKAGE_NAME__.taskboard.TaskItem

class WebRepositoryContractTest {
    private fun column(id: Long, rank: Int = id.toInt() - 1) =
        Column(ColumnId(id), "c" + id, ColumnKind.MIDDLE, rank)

    private fun task(id: Long, columnId: Long) = TaskItem(
        id = TaskId(id),
        columnId = ColumnId(columnId),
        title = "t" + id,
        notes = null,
        dueDate = EpochDay(10L + id),
        rank = id.toInt() - 1,
        createdAt = Instant.fromEpochMilliseconds(1_000L + id),
        archivedAt = null,
    )

    @Test
    fun `deltas survive a reopen`() = runTest {
        val factory = FakeIndexedDb()
        val repository = WebTaskBoardRepository(factory.idbFactory)
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertColumn(column(2)),
                BoardDelta.UpsertTask(task(1, 1)),
                BoardDelta.UpsertTask(task(2, 2)),
            ),
        )
        val reopened = WebTaskBoardRepository(factory.idbFactory).open()
        assertEquals(listOf(1L, 2L), reopened.columns.map { it.id.value })
        assertEquals(listOf(1L, 2L), reopened.tasks.map { it.id.value })
        assertEquals(3L, reopened.nextColumnId)
        assertEquals(3L, reopened.nextTaskId)
        assertEquals(EpochDay(11L), reopened.tasks.single { it.id == TaskId(1L) }.dueDate)
    }

    @Test
    fun `apply is atomic when a put fails`() = runTest {
        val factory = FakeIndexedDb()
        val repository = WebTaskBoardRepository(factory.idbFactory)
        repository.open()
        factory.failOnPut = 2
        assertFailsWith<StorageError> {
            repository.apply(
                listOf(
                    BoardDelta.UpsertColumn(column(1)),
                    BoardDelta.UpsertColumn(column(2)),
                ),
            )
        }
        assertEquals(0, WebTaskBoardRepository(factory.idbFactory).open().columns.size)
    }

    @Test
    fun `archived tasks round-trip with a null column`() = runTest {
        val factory = FakeIndexedDb()
        val repository = WebTaskBoardRepository(factory.idbFactory)
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertTask(task(1, 1).copy(archivedAt = Instant.fromEpochMilliseconds(9_000L), rank = 0)),
            ),
        )
        val archived = WebTaskBoardRepository(factory.idbFactory).open().tasks.single()
        assertEquals(Instant.fromEpochMilliseconds(9_000L), archived.archivedAt)
        assertEquals(ColumnId(0L), archived.columnId)
    }

    @Test
    fun `a newer database version fails to open`() = runTest {
        val factory = FakeIndexedDb()
        factory.databases["daily-board"] = FakeDatabase(version = 2)
        assertFailsWith<Exception> { WebTaskBoardRepository(factory.idbFactory).open() }
    }

    @Test
    fun `the full store persists over the indexeddb repository`() = runTest {
        val factory = FakeIndexedDb()
        val store = TaskBoardStore(WebTaskBoardRepository(factory.idbFactory))
        store.open()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "persisted")
        val reopened = TaskBoardStore(WebTaskBoardRepository(factory.idbFactory))
        reopened.open()
        assertEquals(listOf("开始", "Doing", "结束"), reopened.snapshot().columns.map { it.name })
        assertEquals(listOf("persisted"), reopened.snapshot().tasks.getValue(doing).map { it.title })
    }
}
