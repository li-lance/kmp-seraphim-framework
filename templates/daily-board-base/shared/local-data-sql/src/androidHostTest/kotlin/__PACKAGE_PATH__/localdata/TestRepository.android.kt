package __PACKAGE_NAME__.localdata

import android.content.Context
import kotlin.random.Random
import org.robolectric.RuntimeEnvironment
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

actual fun testRepository(): TaskBoardRepository {
    val context: Context = RuntimeEnvironment.getApplication()
    val name = contractDatabaseName ?: ("contract-" + Random.nextLong().toString(16) + ".db")
        .also { contractDatabaseName = it }
    return taskBoardRepository(context, name)
}
