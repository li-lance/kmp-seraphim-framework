package __PACKAGE_NAME__.android

import android.app.Application
import __PACKAGE_NAME__.localdata.taskBoardRepository
import __PACKAGE_NAME__.taskboard.TaskBoardStore

class DailyBoardApplication : Application() {
    val boardStore: TaskBoardStore by lazy {
        TaskBoardStore(taskBoardRepository(this))
    }
}
