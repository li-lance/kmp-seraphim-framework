package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskBoardTest {
    @Test
    fun `creates a trimmed task`() {
        val board = TaskBoard()
        assertEquals("Read", board.createTask("  Read  ").tasks.single().title)
    }
}
