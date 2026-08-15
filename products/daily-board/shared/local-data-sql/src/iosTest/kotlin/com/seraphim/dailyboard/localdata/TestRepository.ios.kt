package com.seraphim.dailyboard.localdata

import kotlin.random.Random
import com.seraphim.dailyboard.taskboard.TaskBoardRepository

actual fun testRepository(): TaskBoardRepository {
    val name = contractDatabaseName ?: ("contract-" + Random.nextLong().toString(16) + ".db")
        .also { contractDatabaseName = it }
    return taskBoardRepository(name)
}
