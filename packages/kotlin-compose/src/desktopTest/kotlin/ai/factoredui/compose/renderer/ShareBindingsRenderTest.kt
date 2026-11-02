package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.share.ShareHost
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private val SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,
     "state":{"map":{"cutoff":5}},
     "share":[{"path":"map.cutoff","query":"cut","store":"cut-store","format":"number"}],
     "root":{"id":"page","type":"column","children":[
       {"id":"shown","type":"text","props":{"value":"cutoff {map.cutoff}"}},
       {"id":"set","type":"button","props":{"label":"set"},"action":{"action":"set_binding","params":{"path":"map.cutoff","value":45}}}
     ]}}
    """.trimIndent(),
)

private class FakeHost(
    var query: Map<String, String> = emptyMap(),
    val store: MutableMap<String, String> = mutableMapOf(),
    private val storeThrows: Boolean = false,
) : ShareHost {
    override fun readQuery(): Map<String, String> = query
    override fun replaceQuery(query: Map<String, String>) { this.query = query }
    override fun readStore(key: String): String? = if (storeThrows) error("blocked") else store[key]
    override fun writeStore(key: String, value: String?) {
        if (storeThrows) error("blocked")
        if (value == null) store.remove(key) else store[key] = value
    }
}

private const val WRITE_WAIT_MILLISECONDS = 600L

private fun shownText(host: ShareHost): String = driveScreen(SPEC, shareHost = host).use { it.tree().toString() }

class ShareBindingsRenderTest {
    @Test
    fun the_address_sets_the_binding_the_page_starts_with() {
        val tree = shownText(FakeHost(query = mapOf("cut" to "43"), store = mutableMapOf("cut-store" to "10")))
        assertTrue(tree.contains("cutoff 43"), tree)
    }

    @Test
    fun a_bare_visit_takes_the_stored_value() {
        assertTrue(shownText(FakeHost(store = mutableMapOf("cut-store" to "10"))).contains("cutoff 10"))
    }

    @Test
    fun with_nothing_shared_the_specs_own_start_stands() {
        assertTrue(shownText(FakeHost()).contains("cutoff 5"))
    }

    @Test
    fun a_malformed_address_value_leaves_the_start_standing() {
        assertTrue(shownText(FakeHost(query = mapOf("cut" to "forty"))).contains("cutoff 5"))
    }

    @Test
    fun storage_that_throws_does_not_stop_the_page_drawing() {
        val tree = shownText(FakeHost(query = mapOf("cut" to "43"), storeThrows = true))
        assertTrue(tree.contains("cutoff 43"), tree)
    }

    @Test
    fun a_change_is_written_to_the_address_and_the_store_and_other_parameters_stay() {
        val host = FakeHost(query = mapOf("spec" to "/land.spec.json"))
        driveScreen(SPEC, shareHost = host).use { screen ->
            screen.tap("set")
            Thread.sleep(WRITE_WAIT_MILLISECONDS)
            screen.tree()
        }
        assertEquals(mapOf("spec" to "/land.spec.json", "cut" to "45"), host.query)
        assertEquals("45", host.store["cut-store"])
    }
}
