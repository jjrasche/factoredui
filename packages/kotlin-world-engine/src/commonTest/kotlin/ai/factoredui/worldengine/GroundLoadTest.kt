package ai.factoredui.worldengine

import ai.factoredui.worldengine.world.WorldLoadException
import ai.factoredui.worldengine.world.WorldLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroundLoadTest {
    private fun groundDemoWithHeights(edit: (List<JsonPrimitive>) -> List<JsonPrimitive>): String {
        val document = Json.parseToJsonElement(GROUND_DEMO_WORLD_JSON).jsonObject
        val ground = document.getValue("ground").jsonObject
        val heights = edit(ground.getValue("heights_mm").jsonArray.map { it as JsonPrimitive })
        return JsonObject(document + ("ground" to JsonObject(ground + ("heights_mm" to JsonArray(heights))))).toString()
    }

    @Test
    fun the_loader_refuses_a_ground_with_one_vertex_missing() {
        val refused = assertFailsWith<WorldLoadException> { WorldLoader.loadFromJson(groundDemoWithHeights { it.drop(1) }, GROUND_DEMO_FILE) }
        assertEquals("ground: ground: 80 heights, but a 8 x 8 grid has (cols + 1) x (rows + 1) = 81 vertices", refused.message)
    }

    @Test
    fun the_loader_refuses_a_ground_height_above_the_ceiling() {
        val refused = assertFailsWith<WorldLoadException> { WorldLoader.loadFromJson(groundDemoWithHeights { listOf(JsonPrimitive(5_000_001)) + it.drop(1) }, GROUND_DEMO_FILE) }
        assertEquals("ground: ground: heights at vertex indices [0] lie outside -500000 to 5000000 mm", refused.message)
    }
}
