package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class BoardModelTest {
    @Test
    fun `single column is both start and end`() {
        assertEquals(ColumnKind.START, columnKindAt(0, 1))
    }

    @Test
    fun `first and last columns are fixed kinds`() {
        assertEquals(ColumnKind.START, columnKindAt(0, 3))
        assertEquals(ColumnKind.MIDDLE, columnKindAt(1, 3))
        assertEquals(ColumnKind.END, columnKindAt(2, 3))
    }

    @Test
    fun `epoch day truncates to the utc calendar day`() {
        val noon = Instant.fromEpochMilliseconds(86_400_000L * 3 + 12 * 3_600_000L)
        assertEquals(3L, EpochDay.fromInstant(noon).value)
        assertEquals(86_400_000L * 3, EpochDay.fromInstant(noon).epochMillis)
    }

    @Test
    fun `epoch day handles pre-epoch instants`() {
        val before = Instant.fromEpochMilliseconds(-86_400_000L + 1_000L)
        assertEquals(-1L, EpochDay.fromInstant(before).value)
    }

    @Test
    fun `all board errors carry their payload`() {
        assertEquals(ColumnId(4L), BoardError.ColumnNotFound(ColumnId(4L)).id)
        assertEquals(TaskId(2L), BoardError.NotArchivable(TaskId(2L)).id)
        assertEquals("x", BoardError.InvalidTitle("x").title)
    }
}
