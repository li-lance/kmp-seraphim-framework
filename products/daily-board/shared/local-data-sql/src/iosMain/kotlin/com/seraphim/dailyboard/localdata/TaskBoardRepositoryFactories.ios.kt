package com.seraphim.dailyboard.localdata

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.seraphim.dailyboard.taskboard.StorageError
import com.seraphim.dailyboard.taskboard.TaskBoardRepository

fun taskBoardRepository(name: String = "daily-board.db"): TaskBoardRepository {
    val driver = try {
        NativeSqliteDriver(
            schema = TaskBoardDatabase.Schema,
            name = name,
            onConfiguration = { it.copy(extendedConfig = it.extendedConfig.copy(foreignKeyConstraints = true)) },
        )
    } catch (failure: IllegalStateException) {
        throw StorageError("Cannot open the daily-board database", failure)
    }
    return SqlDelightTaskBoardRepository(driver)
}
