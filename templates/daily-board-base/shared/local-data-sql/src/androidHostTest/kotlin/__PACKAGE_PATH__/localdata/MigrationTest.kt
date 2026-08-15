package __PACKAGE_NAME__.localdata

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a fresh database is created at the schema version`() = runTest {
        context.deleteDatabase("migration.db")
        // AndroidSqliteDriver 惰性建库：open() 才真正应用 schema 并写入 user_version
        taskBoardRepository(context, "migration.db").open()
        val version = context.openOrCreateDatabase("migration.db", 0, null).use { database ->
            database.rawQuery("PRAGMA user_version", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
        assertEquals(TaskBoardDatabase.Schema.version, version)
        context.deleteDatabase("migration.db")
    }

    @Test
    fun `an empty v0 database migrates to the current schema`() = runTest {
        context.deleteDatabase("migration.db")
        context.openOrCreateDatabase("migration.db", 0, null).close()
        val board = taskBoardRepository(context, "migration.db").open()
        assertEquals(0, board.columns.size)
        assertEquals(0, board.tasks.size)
        context.deleteDatabase("migration.db")
    }

    @Test
    fun `a database newer than the supported schema is rejected`() {
        context.deleteDatabase("migration.db")
        context.openOrCreateDatabase("migration.db", 0, null).use { database ->
            database.execSQL("PRAGMA user_version = 99")
        }
        val error = assertFailsWith<StorageError> { taskBoardRepository(context, "migration.db") }
        assertTrue(error.message!!.contains("newer"))
        context.deleteDatabase("migration.db")
    }
}
