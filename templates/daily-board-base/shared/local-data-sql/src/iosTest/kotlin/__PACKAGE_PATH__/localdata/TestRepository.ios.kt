package __PACKAGE_NAME__.localdata

import kotlin.random.Random
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

actual fun testRepository(): TaskBoardRepository {
    val name = contractDatabaseName ?: ("contract-" + Random.nextLong().toString(16) + ".db")
        .also { contractDatabaseName = it }
    return taskBoardRepository(name)
}
