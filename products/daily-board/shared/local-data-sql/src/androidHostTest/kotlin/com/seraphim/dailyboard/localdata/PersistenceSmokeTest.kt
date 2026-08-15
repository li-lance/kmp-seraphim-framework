package com.seraphim.dailyboard.localdata

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.seraphim.dailyboard.taskboard.TaskBoardStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenceSmokeTest {
    @Test
    fun `tasks survive a full store reopen`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("smoke.db")
        val store1 = TaskBoardStore(taskBoardRepository(context, "smoke.db"))
        store1.open()
        val doing = store1.createColumn("Doing").columns[1].id
        store1.createTask(doing, "persisted", notes = "n")
        val doomed = store1.createColumn("Doomed").columns[2].id
        store1.createTask(doomed, "cascade-archived")
        store1.deleteColumn(doomed)   // FK 下删除含任务列：归档 upsert 先于列删除
        val endId = store1.snapshot().columns.last().id
        val doneId = store1.createTask(endId, "done").tasks.getValue(endId).single().id
        store1.archiveTask(doneId)

        val store2 = TaskBoardStore(taskBoardRepository(context, "smoke.db"))
        store2.open()
        val snapshot = store2.snapshot()
        assertEquals(listOf("开始", "Doing", "结束"), snapshot.columns.map { it.name })
        assertEquals(listOf("persisted"), snapshot.tasks.getValue(doing).map { it.title })
        assertEquals(setOf("done", "cascade-archived"), snapshot.archivedTasks.map { it.title }.toSet())
        context.deleteDatabase("smoke.db")
    }
}
