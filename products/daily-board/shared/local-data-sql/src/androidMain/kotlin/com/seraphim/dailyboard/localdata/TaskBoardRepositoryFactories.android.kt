package com.seraphim.dailyboard.localdata

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.seraphim.dailyboard.taskboard.StorageError
import com.seraphim.dailyboard.taskboard.TaskBoardRepository

fun taskBoardRepository(context: Context, name: String = "daily-board.db"): TaskBoardRepository {
    val storedVersion = context.openOrCreateDatabase(name, 0, null).use { database ->
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }
    if (storedVersion > TaskBoardDatabase.Schema.version) {
        throw StorageError(
            "Database version " + storedVersion + " is newer than supported " + TaskBoardDatabase.Schema.version,
        )
    }
    val driver = AndroidSqliteDriver(
        schema = TaskBoardDatabase.Schema,
        context = context,
        name = name,
        callback = object : AndroidSqliteDriver.Callback(TaskBoardDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )
    return SqlDelightTaskBoardRepository(driver)
}
