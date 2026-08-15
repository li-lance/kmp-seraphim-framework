package com.seraphim.dailyboard.localdata

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import com.seraphim.dailyboard.taskboard.BoardDelta
import com.seraphim.dailyboard.taskboard.Column
import com.seraphim.dailyboard.taskboard.ColumnId
import com.seraphim.dailyboard.taskboard.ColumnKind
import com.seraphim.dailyboard.taskboard.EpochDay
import com.seraphim.dailyboard.taskboard.TaskBoardRepository
import com.seraphim.dailyboard.taskboard.TaskId
import com.seraphim.dailyboard.taskboard.TaskItem

expect fun testRepository(): TaskBoardRepository

// 每个测试方法独占一个数据库名（@BeforeTest 重置），保证合约测试在
// 测试内可重开共享同一存储、测试间互不污染；平台 actual 首次调用时生成并缓存。
internal var contractDatabaseName: String? = null

open class RepositoryContractTest {
    @BeforeTest
    fun resetContractDatabase() {
        contractDatabaseName = null
    }

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
        val repository = testRepository()
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertColumn(column(2)),
                BoardDelta.UpsertTask(task(1, 1)),
                BoardDelta.UpsertTask(task(2, 2)),
            ),
        )
        val reopened = testRepository().open()
        assertEquals(listOf(1L, 2L), reopened.columns.map { it.id.value })
        assertEquals(listOf(1L, 2L), reopened.tasks.map { it.id.value })
        assertEquals(3L, reopened.nextColumnId)
        assertEquals(3L, reopened.nextTaskId)
        val stored = reopened.tasks.single { it.id == TaskId(1L) }
        assertEquals(EpochDay(11L), stored.dueDate)
        assertEquals(Instant.fromEpochMilliseconds(1_001L), stored.createdAt)
    }

    @Test
    fun `apply is atomic when a delta violates foreign keys`() = runTest {
        val repository = testRepository()
        repository.open()
        assertFailsWith<Exception> {
            repository.apply(
                listOf(
                    BoardDelta.UpsertColumn(column(1)),
                    BoardDelta.UpsertTask(task(1, 999)),
                ),
            )
        }
        val after = testRepository().open()
        assertEquals(0, after.columns.size)
        assertEquals(0, after.tasks.size)
    }

    @Test
    fun `upserts are idempotent and deletes of absent rows are no-ops`() = runTest {
        val repository = testRepository()
        repository.open()
        repository.apply(listOf(BoardDelta.UpsertColumn(column(1, rank = 5))))
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1, rank = 0)),
                BoardDelta.DeleteColumn(ColumnId(77L)),
                BoardDelta.DeleteTask(TaskId(88L)),
            ),
        )
        val after = testRepository().open()
        assertEquals(1, after.columns.size)
        assertEquals(0, after.columns.single().rank)
        assertEquals(0, after.tasks.size)
    }

    @Test
    fun `archived tasks persist with a null column`() = runTest {
        val repository = testRepository()
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertTask(task(1, 1).copy(archivedAt = Instant.fromEpochMilliseconds(9_000L), rank = 0)),
            ),
        )
        val archived = testRepository().open().tasks.single()
        assertEquals(Instant.fromEpochMilliseconds(9_000L), archived.archivedAt)
        assertEquals(ColumnId(0L), archived.columnId)
    }
}
