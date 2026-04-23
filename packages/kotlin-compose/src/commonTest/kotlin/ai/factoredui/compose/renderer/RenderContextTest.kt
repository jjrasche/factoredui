package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that [RenderContext.setBinding] mutates the reactive store at the
 * correct nested path and that the resulting data snapshot reflects the write.
 * These tests do NOT exercise Compose recomposition — they only verify the
 * data-plane contract.
 */
class RenderContextTest {

    @Test
    fun setBindingWritesTopLevelKey() {
        val ctx = RenderContext(initialData = mapOf("foo" to "old"))
        ctx.setBinding("foo", "new")
        assertEquals("new", ctx.data["foo"])
    }

    @Test
    fun setBindingWritesNestedPath() {
        val ctx = RenderContext(initialData = mapOf("shell" to mapOf("composeText" to "")))
        ctx.setBinding("shell.composeText", "hello")
        val shell = ctx.data["shell"] as Map<*, *>
        assertEquals("hello", shell["composeText"])
    }

    @Test
    fun setBindingCreatesIntermediateMaps() {
        val ctx = RenderContext(initialData = emptyMap())
        ctx.setBinding("a.b.c", 42)
        val a = ctx.data["a"] as Map<*, *>
        val b = a["b"] as Map<*, *>
        assertEquals(42, b["c"])
    }

    @Test
    fun setBindingPreservesSiblingKeys() {
        val ctx = RenderContext(initialData = mapOf(
            "shell" to mapOf("composeText" to "", "recipientIds" to listOf("a", "b")),
        ))
        ctx.setBinding("shell.composeText", "typed")
        val shell = ctx.data["shell"] as Map<*, *>
        assertEquals("typed", shell["composeText"])
        assertEquals(listOf("a", "b"), shell["recipientIds"])
    }

    @Test
    fun setBindingIgnoresEmptyPath() {
        val ctx = RenderContext(initialData = mapOf("foo" to "unchanged"))
        ctx.setBinding("", "ignored")
        assertEquals("unchanged", ctx.data["foo"])
    }

    @Test
    fun setBindingWritesNull() {
        val ctx = RenderContext(initialData = mapOf("shell" to mapOf("value" to "present")))
        ctx.setBinding("shell.value", null)
        val shell = ctx.data["shell"] as Map<*, *>
        assertNull(shell["value"])
        assertTrue(shell.containsKey("value"))
    }

    @Test
    fun withAdditionalDataOverlaysWithoutMutatingStore() {
        val ctx = RenderContext(initialData = mapOf("foo" to "base"))
        val scoped = ctx.withAdditionalData(mapOf("item" to mapOf("id" to 1)))
        val scopedItem = scoped.data["item"] as Map<*, *>
        assertEquals(1, scopedItem["id"])
        assertEquals("base", scoped.data["foo"])
        // Overlay does not leak back to the parent context snapshot
        assertNull(ctx.data["item"])
    }

    @Test
    fun setBindingOnScopedContextWritesToSharedStore() {
        val parent = RenderContext(initialData = mapOf("shell" to mapOf("composeText" to "")))
        val scoped = parent.withAdditionalData(mapOf("item" to mapOf("id" to 5)))
        scoped.setBinding("shell.composeText", "from-scope")
        val parentShell = parent.data["shell"] as Map<*, *>
        assertEquals("from-scope", parentShell["composeText"])
    }
}
