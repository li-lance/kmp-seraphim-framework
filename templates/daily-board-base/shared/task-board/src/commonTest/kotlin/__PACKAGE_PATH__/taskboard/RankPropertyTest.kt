package __PACKAGE_NAME__.taskboard

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class RankPropertyTest {
    @Test
    fun `200 random operations preserve dense stable ordering`() = runTest {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        val random = Random(42)
        var counter = 0
        repeat(200) {
            val snapshot = store.snapshot()
            val middle = snapshot.columns.filter { it.kind == ColumnKind.MIDDLE }
            val activeTasks = snapshot.tasks.values.flatten()
            val archivedTasks = snapshot.archivedTasks
            when (random.nextInt(8)) {
                0 -> store.createTask(snapshot.columns.random(random).id, "t" + counter++)
                1 -> if (activeTasks.isNotEmpty()) {
                    val task = activeTasks.random(random)
                    val target = snapshot.columns.random(random).id
                    // moveTask's toIndex is relative to the target list after removing the moved
                    // task (plan Task 6), so a same-column move has one fewer valid position than
                    // the snapshot count, which still includes the task itself.
                    val targetSize = snapshot.tasks[target].orEmpty().size
                    val upperBound = if (task.columnId == target) targetSize else targetSize + 1
                    store.moveTask(task.id, target, random.nextInt(0, upperBound))
                }
                2 -> store.createColumn("c" + counter++)
                3 -> if (middle.isNotEmpty()) store.deleteColumn(middle.random(random).id)
                4 -> if (middle.isNotEmpty()) {
                    val column = middle.random(random)
                    store.moveColumn(column.id, random.nextInt(1, snapshot.columns.size - 1))
                }
                5 -> if (snapshot.columns.isNotEmpty()) store.renameColumn(snapshot.columns.random(random).id, "r" + counter++)
                6 -> if (archivedTasks.isNotEmpty()) store.restoreTask(archivedTasks.random(random).id)
                else -> {
                    val endId = snapshot.columns.last().id
                    val endTasks = snapshot.tasks[endId].orEmpty()
                    if (endTasks.isNotEmpty()) store.archiveTask(endTasks.random(random).id)
                }
            }
            assertInvariants(store)
        }
    }

    private suspend fun assertInvariants(store: TaskBoardStore) {
        val snapshot = store.snapshot()
        snapshot.columns.forEachIndexed { index, column ->
            assertEquals(index, column.rank, "column rank dense at " + index)
            assertEquals(columnKindAt(index, snapshot.columns.size), column.kind, "column kind at " + index)
        }
        snapshot.columns.forEach { column ->
            val items = snapshot.tasks[column.id].orEmpty()
            items.forEachIndexed { index, item ->
                assertEquals(index, item.rank, "task rank dense in column " + column.id.value)
                assertNull(item.archivedAt, "active task in column " + column.id.value)
                assertEquals(column.id, item.columnId)
            }
            val ordered = items.sortedWith(compareBy({ it.rank }, { it.id.value }))
            assertEquals(ordered, items, "(rank,id) total order in column " + column.id.value)
        }
        snapshot.archivedTasks.forEach { task ->
            assertNull(snapshot.tasks.values.flatten().find { it.id == task.id })
        }
    }
}
