package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.structure.StructureDrawing
import ai.factoredui.compose.structure.fitDrawing
import ai.factoredui.compose.structure.mmToCanvas
import ai.factoredui.compose.structure.partBox
import ai.factoredui.compose.structure.partRectMm
import ai.factoredui.compose.structure.projectFaces
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

internal fun engineHouseText(): String =
    checkNotNull(StructureSceneTest::class.java.getResource("/house-floor.scene.json")) { "house-floor.scene.json is not on the test classpath" }.readText()

private const val WIDTH = 800
private const val HEIGHT = 600

private fun structureSpec(selected: String) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"house","type":"scene","props":{
      "view_state": {"renderer": "structure"},
      "scene_view": "{house.scene}",
      "view": "{house.view}",
      "highlight": "{bom.picked}",
      "selected": $selected,
      "on_part_tap": "part_tapped"
    }}}
    """.trimIndent(),
)

private val BOUND = structureSpec("\"{house.selected}\"")

private fun storeFor(view: String, picked: List<String> = emptyList(), scene: Any? = engineHouseText()) = mapOf(
    "house" to mapOf("scene" to scene, "view" to view),
    "bom" to mapOf("picked" to picked),
)

private fun selectedIn(store: Map<String, Any?>): Any? = (store["house"] as? Map<*, *>)?.get("selected")

private fun centreOf(scene: SceneView, partId: String, drawing: StructureDrawing): Pair<Float, Float> {
    val fit = fitDrawing(scene, drawing, WIDTH.toDouble(), HEIGHT.toDouble())
    val part = scene.footprints!!.items.first { it.id == partId }
    val centre = if (drawing == StructureDrawing.ISO) {
        val top = projectFaces(partBox(scene, part)).top.map { (u, v) -> mmToCanvas(fit, u, v) }
        top.sumOf { it.first } / top.size to top.sumOf { it.second } / top.size
    } else {
        val rect = partRectMm(scene, part)
        mmToCanvas(fit, (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2)
    }
    return centre.first.toFloat() to centre.second.toFloat()
}

class StructureSceneTest {

    private val house = readSceneView(engineHouseText())
    private val piers = house.footprints!!.items.filter { it.type == "cast_pier" }.map { it.id }

    @Test
    fun the_engine_house_draws_all_thirty_five_parts_in_each_of_the_four_views() {
        listOf("plan", "iso", "south", "west").forEach { view ->
            driveScreen(BOUND, storeFor(view), WIDTH, HEIGHT).use { screen ->
                assertEquals("drawn=35 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("house")), view)
            }
        }
    }

    @Test
    fun highlighting_the_piers_outlines_twelve_parts_and_fades_the_other_twenty_three() {
        listOf("plan", "iso").forEach { view ->
            driveScreen(BOUND, storeFor(view, picked = piers), WIDTH, HEIGHT).use { screen ->
                assertEquals("drawn=35 highlighted=12 faded=23 selected=", screen.stateDescriptionOf(partsTag("house")), view)
            }
        }
    }

    @Test
    fun tapping_a_panel_in_plan_selects_it_names_it_in_a_popup_and_tells_the_host() {
        driveScreen(BOUND, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(house, "e21", StructureDrawing.PLAN)
            val intents = screen.tapAt(x, y)
            assertEquals(listOf("e21"), selectedIn(screen.store()))
            assertEquals(listOf("part_tapped"), intents.map { it.action })
            assertEquals("e21", intents.single().params["id"])
            assertEquals(listOf("Site-built SIP floor panel", "e21 · 76% of capacity"), screen.textsUnder(popupTag("house")))
            assertTrue(screen.stateDescriptionOf(partsTag("house"))!!.endsWith("selected=e21"))
        }
    }

    @Test
    fun tapping_the_selected_part_again_clears_the_selection_and_the_popup() {
        driveScreen(BOUND, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(house, "e21", StructureDrawing.PLAN)
            screen.tapAt(x, y)
            screen.tapAt(x, y)
            assertEquals(emptyList<String>(), selectedIn(screen.store()))
            assertEquals(emptyList(), screen.textsUnder(popupTag("house")))
        }
    }

    @Test
    fun tapping_empty_space_clears_the_selection() {
        driveScreen(BOUND, storeFor("plan") + mapOf("house" to mapOf("scene" to engineHouseText(), "view" to "plan", "selected" to listOf("e21"))), WIDTH, HEIGHT).use { screen ->
            screen.tapAt(4f, 4f)
            assertEquals(emptyList<String>(), selectedIn(screen.store()))
        }
    }

    @Test
    fun tapping_a_panel_top_in_iso_selects_that_panel() {
        driveScreen(BOUND, storeFor("iso"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(house, "e28", StructureDrawing.ISO)
            screen.tapAt(x, y)
            assertEquals(listOf("e28"), selectedIn(screen.store()))
        }
    }

    @Test
    fun a_structure_with_no_bound_selection_still_selects_on_tap() {
        driveScreen(structureSpec("[]"), storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(house, "e21", StructureDrawing.PLAN)
            screen.tapAt(x, y)
            assertTrue(screen.stateDescriptionOf(partsTag("house"))!!.endsWith("selected=e21"))
        }
    }

    @Test
    fun a_payload_that_is_not_a_scene_says_why_on_screen() {
        driveScreen(BOUND, storeFor("plan", scene = "{not json"), WIDTH, HEIGHT).use { screen ->
            assertEquals(listOf("The structure cannot be drawn: the scene is not valid JSON"), screen.textsUnder("house"))
        }
    }

    @Test
    fun an_elevation_of_a_scene_with_no_heights_says_its_parts_are_drawn_flat() {
        driveScreen(BOUND, storeFor("south"), WIDTH, HEIGHT).use { screen ->
            assertTrue("The scene states no part heights, so every part is drawn flat at its base." in screen.textsUnder("house"))
        }
    }
}
