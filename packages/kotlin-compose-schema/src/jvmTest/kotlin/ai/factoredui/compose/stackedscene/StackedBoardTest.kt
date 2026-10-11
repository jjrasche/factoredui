package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.SceneViewFormatException
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.scene.readSceneViewValue
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun boardText(): String = StackedBoardTest::class.java.getResource("/stacked-board.scene.json")!!.readText()

private fun board(): SceneView = readSceneView(boardText())

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
private const val CHANNEL = "crowding"

private val BLOCKS = setOf("b1", "b2", "b3", "b4", "b5", "b6")
private val BARS = setOf("r1", "r2")

private fun everyPart(scene: SceneView) = visibleParts(scene, LevelFocus.AllLevels, showLower = true)

private fun drawn(scene: SceneView, drawing: StackedDrawing, highlight: Set<String> = emptySet()) =
    partsToDraw(scene, drawing, LevelFocus.AllLevels, showLower = true, hideTopCount = 0, highlightIds = highlight)

private fun SceneView.part(id: String) = footprints!!.items.first { it.id == id }

class StackedBoardTest {

    @Test
    fun the_scene_reads_the_same_from_its_json_text_and_from_a_store_of_maps_and_lists() {
        assertEquals(board(), readSceneViewValue(storeValueOf(Json.parseToJsonElement(boardText()), ::wholeAsLong)))
        assertEquals(board(), readSceneViewValue(boardText()))
    }

    @Test
    fun a_store_that_holds_a_64_bit_layer_version_as_a_double_is_refused_naming_the_version() {
        val failure = assertFailsWith<SceneViewFormatException> { readSceneViewValue(storeValueOf(Json.parseToJsonElement(boardText())) { it.doubleOrNull }) }
        assertEquals("version must be a whole number, not 7.179409882978187E18", failure.message)
    }

    @Test
    fun a_store_that_holds_every_number_as_a_double_still_reads_every_part_and_value() {
        val fromDoubles = readSceneViewValue(storeValueOf(withoutLayerVersions(Json.parseToJsonElement(boardText()))) { it.doubleOrNull })
        assertEquals(board().footprints!!.items, fromDoubles.footprints!!.items)
        assertEquals(board().types, fromDoubles.types)
        assertEquals(partValues(board(), CHANNEL), partValues(fromDoubles, CHANNEL))
    }

    @Test
    fun a_scene_view_that_is_neither_an_object_nor_its_text_fails_with_a_message_a_person_can_read() {
        assertEquals("no scene is bound to scene_view", assertFailsWith<SceneViewFormatException> { readSceneViewValue(null) }.message)
        assertTrue(assertFailsWith<SceneViewFormatException> { readSceneViewValue(42.0) }.message!!.contains("scene object or its JSON text"))
        assertEquals("the scene states no units", assertFailsWith<SceneViewFormatException> { readSceneViewValue(mapOf("cols" to 1.0)) }.message)
    }

    @Test
    fun five_parts_carry_a_value_on_the_named_channel_and_a_value_on_another_channel_is_not_read() {
        val values = partValues(board(), CHANNEL)
        assertEquals(setOf("r1", "r2", "p1", "p2", "p3"), values.keys)
        assertEquals(1.3, values.getValue("p3").value)
        assertEquals(setOf("b1"), partValues(board(), "note").keys)
    }

    @Test
    fun the_board_has_three_levels_and_a_level_of_two_equally_common_types_is_named_for_both() {
        val scene = board()
        assertEquals(listOf(0, 1, 2), levelsPresent(scene))
        assertEquals(listOf("Block", "Bar and Wide bar", "Plate"), listOf(0, 1, 2).map { levelName(scene, it) })
    }

    @Test
    fun hiding_top_levels_peels_the_plates_then_the_bars_and_a_focus_shows_one_level_or_it_and_those_below() {
        val scene = board()
        fun count(focus: LevelFocus, showLower: Boolean, hideTop: Int) = visibleParts(scene, focus, showLower, hideTop).size
        assertEquals(12, count(LevelFocus.AllLevels, true, 0))
        assertEquals(8, count(LevelFocus.AllLevels, true, 1))
        assertEquals(6, count(LevelFocus.AllLevels, true, 2))
        assertEquals(2, count(LevelFocus.OneLevel(1), false, 0))
        assertEquals(8, count(LevelFocus.OneLevel(1), true, 0))
        assertEquals(6, visibleParts(scene, LevelFocus.OneLevel(1), true, 0).count { it.isDimmed })
    }

    @Test
    fun every_part_stands_at_its_own_z_with_its_own_height_and_the_stack_tops_out_at_900_mm() {
        val scene = board()
        val boxes = scene.footprints!!.items.associate { it.id to partBox(scene, it) }
        assertEquals(0.0 to 400.0, boxes.getValue("b1").let { it.z0 to it.z1 })
        assertEquals(400.0 to 700.0, boxes.getValue("r1").let { it.z0 to it.z1 })
        assertEquals(400.0 to 700.0, boxes.getValue("r2").let { it.z0 to it.z1 })
        assertEquals(700.0 to 900.0, boxes.getValue("p1").let { it.z0 to it.z1 })
        assertEquals(900.0, boxes.values.maxOf { it.z1 })
        assertFalse(isEveryPartFlat(scene, everyPart(scene)))
    }

    @Test
    fun a_block_is_one_tile_by_two_and_a_bar_runs_the_whole_6000_mm_width() {
        val scene = board()
        assertEquals(RectMm(2700.0, 600.0, 3000.0, 1200.0), partRectMm(scene, scene.part("b2")))
        assertEquals(RectMm(0.0, 600.0, 6000.0, 1200.0), partRectMm(scene, scene.part("r1")))
    }

    @Test
    fun the_iso_order_draws_every_part_exactly_once() {
        val scene = board()
        val ordered = orderForIso(scene, everyPart(scene)).map { it.footprint.id }
        assertEquals(scene.footprints!!.items.map { it.id }.sorted(), ordered.sorted())
    }

    @Test
    fun in_iso_every_part_is_painted_after_each_part_it_stands_on() {
        val scene = board()
        val ordered = orderForIso(scene, everyPart(scene)).map { it.footprint }
        val position = ordered.withIndex().associate { it.value.id to it.index }
        val boxes = ordered.associate { it.id to partBox(scene, it) }
        val standsOn = ordered.flatMap { upper ->
            ordered.filter { lower ->
                val a = boxes.getValue(lower.id)
                val b = boxes.getValue(upper.id)
                a.z1 == b.z0 && a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1
            }.map { it.id to upper.id }
        }
        assertEquals(6, standsOn.count { (_, upper) -> upper in BARS }, "blocks under bars: $standsOn")
        assertEquals(8, standsOn.count { (lower, _) -> lower in BARS }, "bars under plates: $standsOn")
        standsOn.forEach { (lower, upper) -> assertTrue(position.getValue(lower) < position.getValue(upper), "$lower is painted after $upper, which stands on it") }
    }

    @Test
    fun highlighting_the_six_blocks_fades_the_other_six_parts_in_every_drawing() {
        val scene = board()
        StackedDrawing.entries.forEach { drawing ->
            val parts = drawn(scene, drawing, BLOCKS)
            assertEquals(12, parts.size)
            assertEquals(BLOCKS, parts.filter { !it.isDimmed }.map { it.footprint.id }.toSet(), "$drawing")
            assertEquals("drawn=12 highlighted=6 faded=6 selected=b1", drawnPartsSummary(parts, BLOCKS, setOf("b1")))
        }
        assertEquals("drawn=12 highlighted=0 faded=0 selected=", drawnPartsSummary(drawn(scene, StackedDrawing.PLAN), emptySet(), emptySet()))
    }

    @Test
    fun a_tap_at_the_centre_of_a_drawn_part_finds_that_part_in_plan_and_in_iso() {
        val scene = board()
        listOf(StackedDrawing.PLAN, StackedDrawing.ISO).forEach { drawing ->
            val parts = drawn(scene, drawing)
            val fit = fitDrawing(scene, drawing, CANVAS_WIDTH, CANVAS_HEIGHT)
            val plate = scene.part("p3")
            val centre = if (drawing == StackedDrawing.PLAN) {
                val rect = partRectMm(scene, plate)
                mmToCanvas(fit, (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2)
            } else {
                val top = projectFaces(partBox(scene, plate)).top.map { (u, v) -> mmToCanvas(fit, u, v) }
                top.sumOf { it.first } / 4 to top.sumOf { it.second } / 4
            }
            assertEquals("p3", hitDrawnPart(scene, parts, drawing, fit, centre.first, centre.second)?.footprint?.id, "$drawing")
            assertEquals(null, hitDrawnPart(scene, parts, drawing, fit, 2.0, 2.0)?.footprint?.id, "$drawing corner")
        }
    }

    @Test
    fun the_popup_names_the_type_the_id_and_the_value_text_or_just_the_id_without_one() {
        val scene = board()
        assertEquals(listOf("Plate", "p2 · 50%"), partPopupLines(scene, scene.part("p2"), "50%"))
        assertEquals(listOf("Block", "b1"), partPopupLines(scene, scene.part("b1"), null))
    }

    private fun planCentreY(scene: SceneView, id: String): Double {
        val fit = fitDrawing(scene, StackedDrawing.PLAN, CANVAS_WIDTH, CANVAS_HEIGHT)
        val rect = partRectMm(scene, scene.part(id))
        return mmToCanvas(fit, (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2).second
    }

    @Test
    fun under_y_up_the_part_at_the_smallest_y_is_drawn_at_the_bottom_of_the_plan() {
        val scene = sceneForYAxis(board(), YAxis.UP)
        assertEquals(board(), scene)
        assertTrue(planCentreY(scene, "r1") > CANVAS_HEIGHT / 2, "r1 sits at y 600 to 1200 of 4800")
        assertTrue(planCentreY(scene, "r2") < CANVAS_HEIGHT / 2)
    }

    @Test
    fun under_y_down_the_part_at_the_smallest_y_is_drawn_at_the_top_of_the_plan() {
        val scene = sceneForYAxis(board(), YAxis.DOWN)
        assertTrue(planCentreY(scene, "r1") < CANVAS_HEIGHT / 2)
        assertTrue(planCentreY(scene, "r2") > CANVAS_HEIGHT / 2)
    }

    @Test
    fun y_down_mirrors_each_part_across_the_window_and_keeps_its_size() {
        val up = board()
        val down = sceneForYAxis(up, YAxis.DOWN)
        listOf("r1", "r2", "b2", "p3").forEach { id ->
            val before = partRectMm(up, up.part(id))
            val after = partRectMm(down, down.part(id))
            assertEquals(RectMm(before.x0, 4800.0 - before.y1, before.x1, 4800.0 - before.y0), after, id)
        }
    }

    @Test
    fun the_west_elevation_puts_the_smallest_y_on_the_right_under_up_and_on_the_left_under_down() {
        val upR1 = partRectInView(board(), board().part("r1"), FlatView.WEST)
        val upR2 = partRectInView(board(), board().part("r2"), FlatView.WEST)
        assertTrue(upR1.x0 > upR2.x0, "under up the larger y is to the viewer's left looking along +x")
        val down = sceneForYAxis(board(), YAxis.DOWN)
        val downR1 = partRectInView(down, down.part("r1"), FlatView.WEST)
        val downR2 = partRectInView(down, down.part("r2"), FlatView.WEST)
        assertTrue(downR1.x0 < downR2.x0)
    }

    @Test
    fun the_south_elevation_keeps_every_part_where_it_was_because_its_axis_is_x() {
        val down = sceneForYAxis(board(), YAxis.DOWN)
        board().footprints!!.items.forEach { part ->
            val up = partRectInView(board(), part, FlatView.SOUTH)
            val flipped = partRectInView(down, down.part(part.id), FlatView.SOUTH)
            assertEquals(up, flipped, part.id)
        }
    }

    @Test
    fun with_no_height_given_the_plan_takes_the_height_its_window_shape_asks_for_at_that_width() {
        val expected = (400.0 - 2 * DRAWING_MARGIN_PX) * 4800.0 / 6000.0 + 2 * DRAWING_MARGIN_PX
        assertEquals(expected, intrinsicSceneHeight(board(), StackedDrawing.PLAN, 400.0), 1e-9)
    }

    @Test
    fun with_no_height_given_a_flat_elevation_takes_the_least_scene_height() {
        assertEquals(MIN_INTRINSIC_SCENE_HEIGHT, intrinsicSceneHeight(board(), StackedDrawing.SOUTH, 400.0), 1e-9)
    }

    @Test
    fun with_no_height_given_a_tall_plan_is_held_to_one_and_a_half_times_its_width() {
        val tall = board().copy(window = board().window.copy(x1 = 1000L, y1 = 20000L))
        assertEquals(600.0, intrinsicSceneHeight(tall, StackedDrawing.PLAN, 400.0), 1e-9)
    }

    @Test
    fun with_no_height_given_the_iso_takes_the_height_its_projected_window_asks_for() {
        val window = isoWindow(board())
        val expected = (400.0 - 2 * DRAWING_MARGIN_PX) * (window.y1 - window.y0) / (window.x1 - window.x0) + 2 * DRAWING_MARGIN_PX
        assertEquals(expected.coerceIn(MIN_INTRINSIC_SCENE_HEIGHT, 600.0), intrinsicSceneHeight(board(), StackedDrawing.ISO, 400.0), 1e-9)
    }

    @Test
    fun the_south_elevation_stacks_a_block_a_bar_and_a_plate_one_on_another() {
        val scene = board()
        assertFalse(isEveryPartFlat(scene, drawn(scene, StackedDrawing.SOUTH)))
        val inView = listOf("b1", "r1", "p1").map { partRectInView(scene, scene.part(it), FlatView.SOUTH) }
        assertEquals(listOf(0.0 to 400.0, 400.0 to 700.0, 700.0 to 900.0), inView.map { it.y0 to it.y1 })
        assertEquals(listOf(0.0 to 300.0, 0.0 to 6000.0, 0.0 to 1500.0), inView.map { it.x0 to it.x1 })
    }

    @Test
    fun an_elevation_stretches_the_heights_and_says_by_how_much_and_the_plan_and_iso_are_true_to_scale() {
        val scene = board()
        val south = fitDrawing(scene, StackedDrawing.SOUTH, CANVAS_WIDTH, CANVAS_HEIGHT)
        val times = exaggerationFor(scene, FlatView.SOUTH, CANVAS_WIDTH, CANVAS_HEIGHT, DRAWING_MARGIN_PX)
        assertTrue(times > 1, "a 900 mm stack on a 6000 mm width should be stretched, got $times")
        assertEquals("Heights are drawn $times times their true scale.", exaggerationNote(south))
        assertEquals(null, exaggerationNote(fitDrawing(scene, StackedDrawing.ISO, CANVAS_WIDTH, CANVAS_HEIGHT)))
        assertEquals(null, exaggerationNote(fitDrawing(scene, StackedDrawing.PLAN, CANVAS_WIDTH, CANVAS_HEIGHT)))
    }

    @Test
    fun the_west_elevation_mirrors_north_to_the_left_and_stacks_the_same_levels() {
        val scene = board()
        assertEquals(RectMm(300.0, 0.0, 900.0, 400.0), partRectInView(scene, scene.part("b4"), FlatView.WEST))
        assertEquals(RectMm(300.0, 400.0, 900.0, 700.0), partRectInView(scene, scene.part("r2"), FlatView.WEST))
    }

    @Test
    fun a_tap_selects_the_part_a_second_tap_clears_it_and_empty_ground_clears_everything() {
        assertEquals(listOf("b1"), selectionAfterTap(emptySet(), "b1"))
        assertEquals(emptyList(), selectionAfterTap(setOf("b1"), "b1"))
        assertEquals(listOf("b2"), selectionAfterTap(setOf("b1"), "b2"))
        assertEquals(emptyList(), selectionAfterTap(setOf("b1"), null))
    }
}
