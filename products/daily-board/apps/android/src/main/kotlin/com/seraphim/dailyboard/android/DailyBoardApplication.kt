package com.seraphim.dailyboard.android

import android.app.Application
import com.seraphim.dailyboard.localdata.taskBoardRepository
import com.seraphim.dailyboard.taskboard.TaskBoardStore

class DailyBoardApplication : Application() {
    val boardStore: TaskBoardStore by lazy {
        TaskBoardStore(taskBoardRepository(this))
    }
}
