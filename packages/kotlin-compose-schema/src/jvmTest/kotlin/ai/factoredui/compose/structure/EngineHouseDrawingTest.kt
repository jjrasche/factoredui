package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.SceneViewFormatException
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.scene.readSceneViewValue
import ai.factoredui.compose.schema.resolveStructureProps
import ai.factoredui.compose.schema.selectionAfterTap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun houseText(): String = EngineHouseDrawingTest::class.java.getResource("/house-floor.scene.json")!!.readText()

private fun engineHouse(): SceneView = readSceneView(houseText())

private fun storeValueOf(element: JsonElement, number: (JsonPrimitive) -> Any?): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { storeValueOf(it.value, number) }
    is JsonArray -> element.map { storeValueOf(it, number) }
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: number(element)
}

private fun wholeAsLong(primitive: JsonPrimitive): Any? = primitive.longOrNull ?: primitive.doubleOrNull

private fun withoutLayerVersions(scene: JsonElement): JsonObject =
    JsonObject(scene.jsonObject.mapValues { (_, field) -> (field as? JsonObject)?.let { layer -> JsonObject(layer - "version") } ?: field })

private const val CANVAS_WIDTH = 800.0
private const val CANVAS_HEIGHT = 600.0

private fun idsOfType(scene: SceneView, type: String) = scene.footprints!!.items.filter { it.type == type }.map { it.id }.toSet()

private fun drawn(scene: SceneView, drawing: StructureDrawing, highlight: Set<String> = emptySet()) =
    partsToDraw(scene, drawing, LevelFocus.AllLevels, showLower = true, hideTopCount = 0, highlightIds = highlight)

class EngineHouseDrawingTest {

    @Test
    fun the_scene_reads_the_same_from_its_json_text_and_from_a_store_of_maps_and_lists() {
        assertEquals(engineHouse(), readSceneViewValue(storeValueOf(Json.parseToJsonElement(houseText()), ::wholeAsLong)))
        assertEquals(engineHouse(), readSceneViewValue(houseText()))
    }

    @Test
    fun a_store_that_holds_a_64_bit_layer_version_as_a_double_is_refused_naming_the_version() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneViewValue(storeValueOf(Json.parseToJsonElement(houseText())) { it.doubleOrNull }) }
        assertEquals("version must be a whole number, not 7.179409882978187E18", failure.message)
    }

    @Test
    fun a_store_that_holds_every_number_as_a_double_still_reads_every_part_and_figure() {
        val fromDoubles = readSceneViewValue(storeValueOf(withoutLayerVersions(Json.parseToJsonElement(houseText()))) { it.doubleOrNull })
        assertEquals(engineHouse().footprints!!.items, fromDoubles.footprints!!.items)
        assertEquals(engineHouse().types, fromDoubles.types)
        assertEquals(utilisationByPart(engineHouse()), utilisationByPart(fromDoubles))
    }

    @Test
    fun a_scene_view_that_is_neither_an_object_nor_its_text_fails_with_a_message_a_person_can_read() {
        assertEquals("no scene is bound to scene_view", assertFailsWith<SceneViewFormatException> { readSceneViewValue(null) }.message)
        assertTrue(assertFailsWith<SceneViewFormatException> { readSceneViewValue(42.0) }.message!!.contains("scene object or its JSON text"))
        assertEquals("the scene states no units", assertFailsWith<SceneViewFormatException> { readSceneViewValue(mapOf("cols" to 1.0)) }.message)
    }

    @Test
    fun highlighting_the_twelve_piers_fades_the_other_nineteen_parts_in_every_drawing() {
        val scene = engineHouse()
        val piers = idsOfType(scene, "cast_pier")
        StructureDrawing.entries.forEach { drawing ->
            val parts = drawn(scene, drawing, piers)
            assertEquals(31, parts.size)
            assertEquals(piers, parts.filter { !it.isDimmed }.map { it.footprint.id }.toSet(), "$drawing")
            assertEquals("drawn=31 highlighted=12 faded=19 selected=e1", drawnPartsSummary(parts, piers, setOf("e1")))
        }
        assertEquals("drawn=31 highlighted=0 faded=0 selected=", drawnPartsSummary(drawn(scene, StructureDrawing.PLAN), emptySet(), emptySet()))
    }

    @Test
    fun a_tap_at_the_centre_of_a_drawn_part_finds_that_part_in_plan_and_in_iso() {
        val scene = engineHouse()
        listOf(StructureDrawing.PLAN, StructureDrawing.ISO).forEach { drawing ->
            val parts = drawn(scene, drawing)
            val fit = fitDrawing(scene, drawing, CANVAS_WIDTH, CANVAS_HEIGHT)
            val panel = scene.footprints!!.items.first { it.id == "e28" }
            val centre = if (drawing == StructureDrawing.PLAN) {
                val rect = partRectMm(scene, panel)
                mmToCanvas(fit, (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2)
            } else {
                val top = projectFaces(partBox(scene, panel)).top.map { (u, v) -> mmToCanvas(fit, u, v) }
                top.sumOf { it.first } / 4 to top.sumOf { it.second } / 4
            }
            assertEquals("e28", hitDrawnPart(scene, parts, drawing, fit, centre.first, centre.second)?.footprint?.id, "$drawing")
            assertEquals(null, hitDrawnPart(scene, parts, drawing, fit, 2.0, 2.0)?.footprint?.id, "$drawing corner")
        }
    }

    @Test
    fun the_popup_names_the_type_the_id_and_the_share_of_capacity_or_just_the_id_without_a_figure() {
        val scene = engineHouse()
        val figures = utilisationByPart(scene)
        val panel = scene.footprints!!.items.first { it.id == "e21" }
        val pier = scene.footprints!!.items.first { it.id == "e1" }
        assertEquals(listOf("Site-built SIP floor panel", "e21 · 76% of capacity"), partPopupLines(scene, panel, figures[panel.id]))
        assertEquals(listOf("Cast pier in a form tube", "e1"), partPopupLines(scene, pier, figures[pier.id]))
    }

    @Test
    fun the_south_elevation_stacks_a_pier_a_beam_and_a_panel_one_on_another() {
        val scene = engineHouse()
        assertTrue(!isEveryPartFlat(scene, drawn(scene, StructureDrawing.SOUTH)))
        val inView = listOf("e1", "e13", "e17").map { id -> partRectInView(scene, scene.footprints!!.items.first { it.id == id }, StructureView.SOUTH) }
        assertEquals(listOf(0.0 to 361.0, 361.0 to 663.0, 663.0 to 914.0), inView.map { it.y0 to it.y1 })
        assertEquals(listOf(0.0 to 305.0, 0.0 to 6405.0, 0.0 to 1220.0), inView.map { it.x0 to it.x1 })
    }

    @Test
    fun the_west_elevation_mirrors_north_to_the_left_and_stacks_the_same_three_levels() {
        val scene = engineHouse()
        val pierAtNorth = partRectInView(scene, scene.footprints!!.items.first { it.id == "e10" }, StructureView.WEST)
        val beamAtNorth = partRectInView(scene, scene.footprints!!.items.first { it.id == "e16" }, StructureView.WEST)
        assertEquals(RectMm(0.0, 0.0, 610.0, 361.0), pierAtNorth)
        assertEquals(RectMm(0.0, 361.0, 305.0, 663.0), beamAtNorth)
    }

    @Test
    fun structure_props_read_view_focus_highlight_and_the_tap_action() {
        val props = resolveStructureProps(
            mapOf("view" to "iso", "focus" to 0.0, "show_lower" to true, "hide_top" to 1.0, "highlight" to listOf("e1", "e2"), "on_part_tap" to "part_tapped"),
        )
        assertEquals(StructureDrawing.ISO, props.drawing)
        assertEquals(LevelFocus.OneLevel(0), props.focus)
        assertEquals(true, props.showLower)
        assertEquals(1, props.hideTopCount)
        assertEquals(setOf("e1", "e2"), props.highlightIds)
        assertEquals("part_tapped", props.onPartTap)
        val defaults = resolveStructureProps(mapOf("focus" to "all"))
        assertEquals(StructureDrawing.PLAN, defaults.drawing)
        assertEquals(LevelFocus.AllLevels, defaults.focus)
        assertEquals(emptyList(), defaults.notes)
    }

    @Test
    fun a_tap_selects_the_part_a_second_tap_clears_it_and_empty_ground_clears_everything() {
        assertEquals(listOf("e1"), selectionAfterTap(emptySet(), "e1"))
        assertEquals(emptyList(), selectionAfterTap(setOf("e1"), "e1"))
        assertEquals(listOf("e2"), selectionAfterTap(setOf("e1"), "e2"))
        assertEquals(emptyList(), selectionAfterTap(setOf("e1"), null))
    }
}
