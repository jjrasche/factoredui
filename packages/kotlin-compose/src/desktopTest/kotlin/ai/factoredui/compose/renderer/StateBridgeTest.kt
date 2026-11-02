package ai.factoredui.compose.renderer

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun bridgeOver(initial: Map<String, Any?>): Pair<RenderContext, StateBridge> = RenderContext(initialData = initial).let { it to StateBridge(it) }

class StateBridgeTest {

    @Test
    fun a_value_written_by_path_reads_back_and_is_in_the_screens_state() {
        val (context, bridge) = bridgeOver(mapOf("heat" to mapOf("hour" to 0)))
        assertNull(bridge.setState("heat.hour", "7"))
        assertEquals("7", bridge.getState("heat.hour"))
        assertEquals(7L, (context.data["heat"] as Map<*, *>)["hour"])
    }

    @Test
    fun a_path_that_does_not_exist_yet_is_made() {
        val (_, bridge) = bridgeOver(emptyMap())
        bridge.setState("heat.field.style", "\"cells\"")
        assertEquals("{\"style\":\"cells\"}", bridge.getState("heat.field"))
    }

    @Test
    fun a_path_ending_in_an_index_replaces_that_slot_and_leaves_the_others() {
        val (_, bridge) = bridgeOver(mapOf("heat" to mapOf("field" to mapOf("values" to listOf(listOf(1, 2), null, listOf(5, 6))))))
        bridge.setState("heat.field.values.1", "[3,4]")
        assertEquals("[[1,2],[3,4],[5,6]]", bridge.getState("heat.field.values"))
    }

    @Test
    fun an_index_past_the_end_grows_the_list_with_not_arrived_slots() {
        val (_, bridge) = bridgeOver(mapOf("heat" to mapOf("field" to mapOf("values" to listOf(listOf(1))))))
        bridge.setState("heat.field.values.3", "[9]")
        assertEquals("[[1],null,null,[9]]", bridge.getState("heat.field.values"))
    }

    @Test
    fun text_that_is_not_json_and_an_empty_segment_are_refused_and_change_nothing() {
        val (_, bridge) = bridgeOver(mapOf("heat" to mapOf("hour" to 1)))
        assertNotNull(bridge.setState("heat.hour", "{not json"))
        assertNotNull(bridge.setState("heat..hour", "2"))
        assertEquals("1", bridge.getState("heat.hour"))
    }

    @Test
    fun a_value_with_nothing_there_reads_as_null() {
        assertEquals("null", bridgeOver(emptyMap()).second.getState("no.such.path"))
    }

    @Test
    fun a_watcher_hears_the_value_at_its_path_change_and_not_a_write_elsewhere() = runBlocking {
        val (_, bridge) = bridgeOver(mapOf("heat" to mapOf("hour" to 0), "other" to 0))
        val heard = async(start = CoroutineStart.UNDISPATCHED) { bridge.changes("heat.hour").drop(1).first() }
        bridge.setState("other", "5")
        bridge.setState("heat.hour", "3")
        assertEquals("3", withTimeout(2000) { heard.await() })
    }
}
