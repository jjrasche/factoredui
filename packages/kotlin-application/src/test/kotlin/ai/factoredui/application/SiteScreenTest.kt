package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SLICE = """{"pieces": [{"id": "wall@0,0", "system": "walls", "x": 0, "y": 0, "z": 0, "dx": 3000, "dy": 100, "dz": 4000, "material": "SIP"}],
    "contacts": [], "cuts": [], "unmeasured": []}"""

private const val CABIN = """{"pieces": [{"id": "floor@0,0", "system": "floor", "x": 0, "y": 0, "z": 0, "dx": 2000, "dy": 3000, "dz": 100, "material": "SIP"}],
    "contacts": [], "cuts": [], "unmeasured": []}"""

private const val SITE = """{"title": "Both", "world": "shed", "assumed": ["the cabin sits 3 ft from the wall"],
    "styles": {"glass": {"stroke": "#9fd3e6", "opacity": 0.25}},
    "members": [
      {"label": "Shed", "world": "shed", "repeat": {"count": 3, "step_mm": 3000}},
      {"label": "Cabin", "world": "cabin", "at_mm": [1000, 500, 610]}]}"""

private val WORLDS = listOf(EngineWorld("shed", Path.of("shed.json")), EngineWorld("cabin", Path.of("cabin.json")))

private val ENGINE = object : EngineRuntime {
    override fun answer(arguments: List<String>, timeoutSeconds: Long): String = if (arguments[1].endsWith("shed.json")) SLICE else CABIN
}

private fun nodesIn(element: JsonElement): List<JsonObject> = when (element) {
    is JsonObject -> listOfNotNull(element.takeIf { it["id"] is JsonPrimitive && it["type"] is JsonPrimitive }) + element.values.flatMap(::nodesIn)
    is JsonArray -> element.flatMap(::nodesIn)
    else -> emptyList()
}

private fun volumeOf(site: String): JsonObject =
    nodesIn(siteSpec(ENGINE, WORLDS, readSiteScreen(site), Timings())).first { (it["id"] as JsonPrimitive).content == "volume" }["props"] as JsonObject

private fun boxesOf(site: String): Map<String, JsonObject> =
    (volumeOf(site)["boxes"] as JsonArray).map { it as JsonObject }.associateBy { (it["id"] as JsonPrimitive).content }

private fun JsonObject.number(key: String) = (this[key] as JsonPrimitive).content.toDouble()

class SiteScreenTest {
    @Test
    fun a_member_repeated_three_times_is_three_boxes_a_step_apart_each_with_its_own_id() {
        val boxes = boxesOf(SITE).filterKeys { it.startsWith("Shed/") }
        assertEquals(listOf("Shed/wall@0,0~1", "Shed/wall@0,0~2", "Shed/wall@0,0~3"), boxes.keys.sorted())
        assertEquals(listOf(1500.0, 4500.0, 7500.0), boxes.toSortedMap().values.map { it.number("x_mm") })
    }

    @Test
    fun a_member_that_is_not_repeated_is_moved_by_its_offset_in_all_three_directions() {
        val cabin = boxesOf(SITE).getValue("Cabin/floor@0,0")
        assertEquals(listOf(2000.0, 2000.0, 610.0), listOf(cabin.number("x_mm"), cabin.number("y_mm"), cabin.number("z_mm")))
    }

    @Test
    fun a_style_the_site_gives_reaches_the_volume_beside_the_stroke_it_would_have_had() {
        val styles = volumeOf(SITE.replace("\"glass\"", "\"SIP\""))["styles"] as JsonObject
        assertEquals("0.25", ((styles.getValue("SIP") as JsonObject)["opacity"] as JsonPrimitive).content)
        assertEquals("#9fd3e6", ((styles.getValue("SIP") as JsonObject)["stroke"] as JsonPrimitive).content)
    }

    @Test
    fun what_the_site_assumes_is_written_on_the_page() {
        val spec = siteSpec(ENGINE, WORLDS, readSiteScreen(SITE), Timings())
        assertTrue(nodesIn(spec).any { (((it["props"] as JsonObject)["value"] as? JsonPrimitive)?.content ?: "").contains("3 ft from the wall") })
    }

    @Test
    fun a_member_naming_a_world_the_engine_does_not_serve_is_named() {
        val failure = assertFailsWith<SiteFormatException> { siteSpec(ENGINE, WORLDS.take(1), readSiteScreen(SITE), Timings()) }
        assertTrue("cabin" in failure.message!!)
    }

    @Test
    fun a_site_with_no_members_is_refused() {
        assertFailsWith<SiteFormatException> { readSiteScreen("""{"world": "shed", "members": []}""") }
    }

    @Test
    fun a_repeat_with_no_step_is_refused() {
        assertFailsWith<SiteFormatException> { readSiteScreen("""{"world": "w", "members": [{"label": "a", "world": "w", "repeat": {"count": 2}}]}""") }
    }

    @Test
    fun lengths_read_in_feet_and_inches() {
        assertEquals("40 ft", feetAndInches(12192.0))
        assertEquals("3 ft 6 in", feetAndInches(1067.0))
        assertEquals("4 in", feetAndInches(102.0))
    }

    @Test
    fun a_picked_box_names_its_member_material_and_size_in_feet() {
        val details = volumeOf(SITE)["details"] as JsonObject
        val card = details.getValue("Cabin/floor@0,0") as JsonObject
        assertEquals("Cabin: floor", (card["title"] as JsonPrimitive).content)
        assertTrue(Json.encodeToString(JsonElement.serializer(), card).contains("6 ft 7 in × 9 ft 10 in × 4 in"))
    }
}
