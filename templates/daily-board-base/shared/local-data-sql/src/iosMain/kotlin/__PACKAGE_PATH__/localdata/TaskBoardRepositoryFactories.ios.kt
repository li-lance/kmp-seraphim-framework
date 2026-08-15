package __PACKAGE_NAME__.localdata

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

fun taskBoardRepository(name: String = "daily-board.db"): TaskBoardRepository {
    val driver = try {
        NativeSqliteDriver(
            schema = TaskBoardDatabase.Schema,
            name = name,
            onConfiguration = { foreign_keys(true) },
        )
    } catch (failure: IllegalStateException) {
        throw StorageError("Cannot open the daily-board database", failure)
    }
    return SqlDelightTaskBoardRepository(driver)
}
