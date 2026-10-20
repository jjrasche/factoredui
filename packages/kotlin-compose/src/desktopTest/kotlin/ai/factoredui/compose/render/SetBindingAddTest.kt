package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RENDERER_ERROR_PATH
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.dispatch
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun number(value: Double) = SpecValue.NumberValue(value)

private fun stepping(context: RenderContext, vararg params: Pair<String, SpecValue>) =
    runBlocking { context.dispatch("next", ActionRef("set_binding", params.toMap())) }

private fun RenderContext.step() = (data["ui"] as Map<*, *>)["step"]

class SetBindingAddTest {
    @Test
    fun add_moves_the_number_at_the_path_and_keeps_it_between_min_and_max() {
        val context = RenderContext(initialData = mapOf("ui" to mapOf("step" to 3.0)))
        stepping(context, "path" to SpecValue.StringValue("ui.step"), "add" to number(1.0), "max" to number(4.0))
        assertEquals(4.0, context.step())
        stepping(context, "path" to SpecValue.StringValue("ui.step"), "add" to number(1.0), "max" to number(4.0))
        assertEquals(4.0, context.step())
        stepping(context, "path" to SpecValue.StringValue("ui.step"), "add" to number(-9.0), "min" to number(0.0))
        assertEquals(0.0, context.step())
    }

    @Test
    fun add_starts_from_min_when_the_path_holds_no_number() {
        val context = RenderContext()
        stepping(context, "path" to SpecValue.StringValue("ui.step"), "add" to number(1.0), "min" to number(5.0))
        assertEquals(6.0, context.step())
    }

    @Test
    fun add_without_a_number_is_refused_on_screen_and_writes_nothing() {
        val context = RenderContext()
        stepping(context, "path" to SpecValue.StringValue("ui.step"), "add" to SpecValue.StringValue("one"))
        assertEquals("set_binding on 'next' was refused: 'add' must be a number, not 'one'", context.data["renderer"].let { (it as Map<*, *>)["error"] })
        assertNull(context.data["ui"])
    }
}
