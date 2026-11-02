package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.ActionAnswer
import ai.factoredui.compose.schema.BindingResolver
import kotlin.test.Test
import kotlin.test.assertEquals

private fun RenderContext.bound(path: String): Any? = BindingResolver.resolveBinding("{$path}", data)

class ActionAnswerApplyTest {
    @Test
    fun the_bindings_an_answer_sets_are_written_in_one_step() {
        val context = RenderContext(initialData = mapOf("pushback" to mapOf("guide" to "old")))
        context.applyActionAnswer(ActionAnswer(mapOf("pushback.guide" to "Slow down.", "pushback.audio" to "/x.mp3"), null))
        assertEquals(listOf<Any?>("Slow down.", "/x.mp3"), listOf(context.bound("pushback.guide"), context.bound("pushback.audio")))
    }

    @Test
    fun a_text_that_looks_like_a_binding_is_stored_as_it_is_not_resolved() {
        val context = RenderContext()
        context.applyActionAnswer(ActionAnswer(mapOf("a.t" to "{pushback.guide}"), null))
        assertEquals("{pushback.guide}", (context.data["a"] as Map<*, *>)["t"])
    }

    @Test
    fun an_error_is_written_to_renderer_error_and_nothing_else_changes() {
        val context = RenderContext(initialData = mapOf("pushback" to mapOf("guide" to "kept")))
        context.applyActionAnswer(ActionAnswer(emptyMap(), "action 'interrupt' was refused with status 500: boom"))
        assertEquals("action 'interrupt' was refused with status 500: boom", context.bound(RENDERER_ERROR_PATH))
        assertEquals("kept", context.bound("pushback.guide"))
    }

    @Test
    fun an_answer_with_nothing_to_say_changes_nothing() {
        val context = RenderContext(initialData = mapOf("a" to 1.0))
        context.applyActionAnswer(ActionAnswer(emptyMap(), null))
        assertEquals(mapOf<String, Any?>("a" to 1.0), context.data)
    }
}
