package __PACKAGE_NAME__.localdata

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RobolectricSmokeTest {
    @Test
    fun `robolectric provides a context`() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertNotNull(context)
    }

    @Test
    fun `repository opens against a real sqlite database`() = kotlinx.coroutines.runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("smoke.db")
        val repository = taskBoardRepository(context, "smoke.db")
        val board = repository.open()
        assertNotNull(board)
        context.deleteDatabase("smoke.db")
    }
}
