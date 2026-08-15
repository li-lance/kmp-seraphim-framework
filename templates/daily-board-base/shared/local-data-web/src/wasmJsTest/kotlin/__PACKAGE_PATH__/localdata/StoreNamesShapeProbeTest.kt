@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package __PACKAGE_NAME__.localdata

import kotlin.JsFun
import kotlin.js.JsAny
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@JsFun("(a) => Array.isArray(a[0])")
external fun probeFirstIsArray(a: JsAny): Boolean

@JsFun("(a) => a.length")
external fun probeLength(a: JsAny): Int

@JsFun("(a) => a[0]")
external fun probeFirst(a: JsAny): String

@JsFun("(a) => a[1]")
external fun probeSecond(a: JsAny): String

class StoreNamesShapeProbeTest {
    @Test
    fun `storeNamesArray produces a flat array of names`() {
        val names = storeNamesArray("columns", "tasks")
        assertEquals(2, probeLength(names), "storeNamesArray must have length 2")
        assertFalse(probeFirstIsArray(names), "storeNamesArray must not nest its varargs")
        assertEquals("columns", probeFirst(names))
        assertEquals("tasks", probeSecond(names))
    }
}
