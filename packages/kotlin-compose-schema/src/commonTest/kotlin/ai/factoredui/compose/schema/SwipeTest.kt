package ai.factoredui.compose.schema

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun swipeOf(props: String): SwipeActions? {
    val node = Json.decodeFromString(SpecNode.serializer(), """{"id":"n","type":"column","props":$props}""")
    return swipeActionsOf(node.props)
}

class SwipeTest {
    @Test
    fun the_left_and_right_actions_are_read() {
        val swipe = swipeOf("""{"on_swipe":{"left":{"action":"next"},"right":{"action":"back"}}}""")
        assertEquals(listOf("next", "back"), listOf(swipe?.left?.action, swipe?.right?.action))
    }

    @Test
    fun one_side_alone_is_enough() {
        val swipe = swipeOf("""{"on_swipe":{"left":{"action":"next"}}}""")
        assertEquals("next", swipe?.left?.action)
        assertNull(swipe?.right)
    }

    @Test
    fun a_node_with_no_swipe_or_an_empty_one_has_none() {
        assertNull(swipeOf("{}"))
        assertNull(swipeOf("""{"on_swipe":{}}"""))
        assertNull(swipeOf("""{"on_swipe":"next"}"""))
    }

    @Test
    fun a_drag_of_a_step_or_more_swipes_that_way_and_a_shorter_one_does_not() {
        assertEquals(SwipeSide.LEFT, swipeSideAfter(-48f, 48f))
        assertEquals(SwipeSide.RIGHT, swipeSideAfter(60f, 48f))
        assertNull(swipeSideAfter(47f, 48f))
        assertNull(swipeSideAfter(-10f, 48f))
    }
}
