package __PACKAGE_NAME__.localdata

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

fun taskBoardRepository(context: Context, name: String = "daily-board.db"): TaskBoardRepository {
    val storedVersion = try {
        context.openOrCreateDatabase(name, 0, null).use { database ->
            database.rawQuery("PRAGMA user_version", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
    } catch (failure: Exception) {
        throw StorageError("Cannot open the daily-board database", failure)
    }
    if (storedVersion > TaskBoardDatabase.Schema.version) {
        throw StorageError(
            "Database version " + storedVersion + " is newer than supported " + TaskBoardDatabase.Schema.version,
        )
    }
    val driver = try {
        AndroidSqliteDriver(
            schema = TaskBoardDatabase.Schema,
            context = context,
            name = name,
            callback = object : AndroidSqliteDriver.Callback(TaskBoardDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
    } catch (failure: Exception) {
        throw StorageError("Cannot open the daily-board database", failure)
    }
    return SqlDelightTaskBoardRepository(driver)
}
