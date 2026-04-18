package ai.factoredui.compose.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BindingResolverTest {

    private val context = mapOf<String, Any?>(
        "shell" to mapOf("inputText" to "hello", "hasResult" to true),
        "user" to mapOf("name" to "Alice"),
        "count" to 42,
        "flag" to false,
    )

    @Test
    fun isBindingRefDetectsCorrectFormat() {
        assertTrue(BindingResolver.isBindingRef("{shell.inputText}"))
        assertFalse(BindingResolver.isBindingRef("plain string"))
        assertFalse(BindingResolver.isBindingRef("{unclosed"))
        assertFalse(BindingResolver.isBindingRef("no{braces}here wrapped"))
    }

    @Test
    fun resolveBindingWalksNestedPath() {
        assertEquals("hello", BindingResolver.resolveBinding("{shell.inputText}", context))
        assertEquals("Alice", BindingResolver.resolveBinding("{user.name}", context))
    }

    @Test
    fun resolveBindingReturnsTrueBoolean() {
        assertEquals(true, BindingResolver.resolveBinding("{shell.hasResult}", context))
    }

    @Test
    fun resolveBindingReturnsNullForMissingPath() {
        assertNull(BindingResolver.resolveBinding("{missing.path}", context))
    }

    @Test
    fun resolveTextInterpolatesInlineBindings() {
        val result = BindingResolver.resolveText("Hello {user.name}!", context)
        assertEquals("Hello Alice!", result)
    }

    @Test
    fun isVisibleReturnsTrueWhenNoRef() {
        assertTrue(BindingResolver.isVisible(null, context))
    }

    @Test
    fun isVisibleResolvesToFalseForFalseyBinding() {
        assertFalse(BindingResolver.isVisible("{flag}", context))
    }

    @Test
    fun isVisibleResolvesToTrueForTruthyBinding() {
        assertTrue(BindingResolver.isVisible("{shell.hasResult}", context))
    }
}
