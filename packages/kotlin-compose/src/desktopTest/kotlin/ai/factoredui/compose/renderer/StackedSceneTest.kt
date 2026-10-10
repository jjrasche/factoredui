package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.stackedscene.DRAWING_MARGIN_PX
import ai.factoredui.compose.stackedscene.FlatView
import ai.factoredui.compose.stackedscene.StackedDrawing
import ai.factoredui.compose.stackedscene.exaggerationFor
import ai.factoredui.compose.stackedscene.fitDrawing
import ai.factoredui.compose.stackedscene.mmToCanvas
import ai.factoredui.compose.stackedscene.partBox
import ai.factoredui.compose.stackedscene.partRectMm
import ai.factoredui.compose.stackedscene.projectFaces
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

internal fun stackedBoardText(): String =
    checkNotNull(StackedSceneTest::class.java.getResource("/stacked-board.scene.json")) { "stacked-board.scene.json is not on the test classpath" }.readText()

private const val WIDTH = 800
private const val HEIGHT = 600

private const val SCALE = """{"channel":"crowding","stops":[{"up_to":0.25,"color":"#3a7ca5"},{"up_to":1,"color":"#e0a030"},{"color":"#b03a2e"}],"unknown_color":"#9aa4ad","format":"percent"}"""

private fun stackedSceneSpec(selected: String, colorBy: String = "null") = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"board","type":"scene","props":{
      "view_state": {"renderer": "stacked_scene"},
      "scene_view": "{board.scene}",
      "view": "{board.view}",
      "highlight": "{board.picked}",
      "selected": $selected,
      "on_part_tap": "part_tapped",
      "color_by": $colorBy
    }}}
    """.trimIndent(),
)

private val BOUND = stackedSceneSpec("\"{board.selected}\"")

private val COLOURED = stackedSceneSpec("\"{board.selected}\"", SCALE)

private fun storeFor(view: String, picked: List<String> = emptyList(), scene: Any? = stackedBoardText()) = mapOf(
    "board" to mapOf("scene" to scene, "view" to view, "picked" to picked),
)

private fun selectedIn(store: Map<String, Any?>): Any? = (store["board"] as? Map<*, *>)?.get("selected")

private fun centreOf(scene: SceneView, partId: String, drawing: StackedDrawing): Pair<Float, Float> {
    val fit = fitDrawing(scene, drawing, WIDTH.toDouble(), HEIGHT.toDouble())
    val part = scene.footprints!!.items.first { it.id == partId }
    val centre = if (drawing == StackedDrawing.ISO) {
        val top = projectFaces(partBox(scene, part)).top.map { (u, v) -> mmToCanvas(fit, u, v) }
        top.sumOf { it.first } / top.size to top.sumOf { it.second } / top.size
    } else {
        val rect = partRectMm(scene, part)
        mmToCanvas(fit, (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2)
    }
    return centre.first.toFloat() to centre.second.toFloat()
}

private fun pixelAt(png: ByteArray, at: Pair<Float, Float>): Int = ImageIO.read(ByteArrayInputStream(png)).getRGB(at.first.toInt(), at.second.toInt())

class StackedSceneTest {

    private val board = readSceneView(stackedBoardText())
    private val blocks = board.footprints!!.items.filter { it.type == "block" }.map { it.id }

    @Test
    fun the_board_draws_all_twelve_parts_in_each_of_the_four_views() {
        listOf("plan", "iso", "south", "west").forEach { view ->
            driveScreen(BOUND, storeFor(view), WIDTH, HEIGHT).use { screen ->
                assertEquals("drawn=12 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")), view)
            }
        }
    }

    @Test
    fun highlighting_the_blocks_outlines_six_parts_and_fades_the_other_six() {
        listOf("plan", "iso").forEach { view ->
            driveScreen(BOUND, storeFor(view, picked = blocks), WIDTH, HEIGHT).use { screen ->
                assertEquals("drawn=12 highlighted=6 faded=6 selected=", screen.stateDescriptionOf(partsTag("board")), view)
            }
        }
    }

    @Test
    fun tapping_a_plate_in_plan_selects_it_names_it_in_a_popup_and_tells_the_host() {
        driveScreen(BOUND, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p2", StackedDrawing.PLAN)
            val intents = screen.tapAt(x, y)
            assertEquals(listOf("p2"), selectedIn(screen.store()))
            assertEquals(listOf("part_tapped"), intents.map { it.action })
            assertEquals("p2", intents.single().params["id"])
            assertEquals(listOf("Plate", "p2"), screen.textsUnder(popupTag("board")))
            assertTrue(screen.stateDescriptionOf(partsTag("board"))!!.endsWith("selected=p2"))
        }
    }

    @Test
    fun with_a_colour_scale_the_popup_shows_the_parts_value_through_the_scales_format() {
        driveScreen(COLOURED, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p2", StackedDrawing.PLAN)
            screen.tapAt(x, y)
            assertEquals(listOf("Plate", "p2 · 50%"), screen.textsUnder(popupTag("board")))
        }
    }

    @Test
    fun with_no_colour_scale_two_parts_holding_different_values_are_painted_alike() {
        val png = driveScreen(BOUND, storeFor("plan"), WIDTH, HEIGHT).use { it.frame().png }
        assertEquals(pixelAt(png, centreOf(board, "p1", StackedDrawing.PLAN)), pixelAt(png, centreOf(board, "p3", StackedDrawing.PLAN)))
    }

    @Test
    fun the_spec_scale_paints_parts_in_different_stops_differently_and_parts_in_the_same_stop_alike() {
        val png = driveScreen(COLOURED, storeFor("plan"), WIDTH, HEIGHT).use { it.frame().png }
        val quiet = pixelAt(png, centreOf(board, "p1", StackedDrawing.PLAN))
        val busy = pixelAt(png, centreOf(board, "p2", StackedDrawing.PLAN))
        val overFull = pixelAt(png, centreOf(board, "p3", StackedDrawing.PLAN))
        val noValue = pixelAt(png, centreOf(board, "p4", StackedDrawing.PLAN))
        assertEquals(4, setOf(quiet, busy, overFull, noValue).size, "quiet=$quiet busy=$busy over=$overFull none=$noValue")
        val unscaled = driveScreen(BOUND, storeFor("plan"), WIDTH, HEIGHT).use { it.frame().png }
        assertEquals(pixelAt(unscaled, centreOf(board, "p4", StackedDrawing.PLAN)), noValue, "a part with no value takes the unknown colour, here the neutral grey")
        assertNotEquals(pixelAt(unscaled, centreOf(board, "p2", StackedDrawing.PLAN)), busy)
    }

    @Test
    fun tapping_the_selected_part_again_clears_the_selection_and_the_popup() {
        driveScreen(BOUND, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p2", StackedDrawing.PLAN)
            screen.tapAt(x, y)
            screen.tapAt(x, y)
            assertEquals(emptyList<String>(), selectedIn(screen.store()))
            assertEquals(emptyList(), screen.textsUnder(popupTag("board")))
        }
    }

    @Test
    fun tapping_empty_space_clears_the_selection() {
        driveScreen(BOUND, mapOf("board" to mapOf("scene" to stackedBoardText(), "view" to "plan", "selected" to listOf("p2"))), WIDTH, HEIGHT).use { screen ->
            screen.tapAt(4f, 4f)
            assertEquals(emptyList<String>(), selectedIn(screen.store()))
        }
    }

    @Test
    fun tapping_a_plate_top_in_iso_selects_that_plate() {
        driveScreen(BOUND, storeFor("iso"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p3", StackedDrawing.ISO)
            screen.tapAt(x, y)
            assertEquals(listOf("p3"), selectedIn(screen.store()))
        }
    }

    @Test
    fun a_stacked_scene_with_no_bound_selection_still_selects_on_tap() {
        driveScreen(stackedSceneSpec("[]"), storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p2", StackedDrawing.PLAN)
            screen.tapAt(x, y)
            assertTrue(screen.stateDescriptionOf(partsTag("board"))!!.endsWith("selected=p2"))
        }
    }

    @Test
    fun a_payload_that_is_not_a_scene_says_why_on_screen() {
        driveScreen(BOUND, storeFor("plan", scene = "{not json"), WIDTH, HEIGHT).use { screen ->
            assertEquals(listOf("The stacked scene cannot be drawn: the scene is not valid JSON"), screen.textsUnder("board"))
        }
    }

    @Test
    fun props_the_renderer_cannot_read_are_each_named_on_screen() {
        val badProps = json.decodeFromString(
            Spec.serializer(),
            """
            {"spec_version":1,"renderer_min":1,"root":{"id":"board","type":"scene","props":{
              "view_state": {"renderer": "stacked_scene"},
              "scene_view": "{board.scene}",
              "view": "isometric", "focus": "top", "highlight": "b1", "show_lower": "yes", "hide_top": 1.5, "color_by": "crowding"
            }}}
            """.trimIndent(),
        )
        driveScreen(badProps, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val texts = screen.textsUnder("board")
            listOf(
                "view 'isometric' is not plan, iso, south or west; the plan is drawn.",
                "focus 'top' is not all or a stack level number; every level is shown.",
                "highlight must be a list of part ids, not 'b1'; nothing is highlighted.",
                "show_lower must be true or false, not 'yes'; lower levels are hidden.",
                "hide_top must be a whole number of levels, not '1.5'; no level is hidden.",
                "color_by must be an object naming a channel and its stops, not 'crowding'; parts are drawn in one colour.",
            ).forEach { note -> assertTrue(note in texts, "missing on screen: $note; drawn: $texts") }
            assertEquals("drawn=12 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")))
        }
    }

    @Test
    fun an_elevation_of_a_scene_with_no_heights_says_its_parts_are_drawn_flat() {
        driveScreen(BOUND, storeFor("south", scene = FLAT_SCENE), WIDTH, HEIGHT).use { screen ->
            assertTrue(FLAT_NOTICE in screen.textsUnder("board"))
        }
    }

    @Test
    fun the_board_states_its_heights_so_no_drawing_says_its_parts_are_flat() {
        listOf("iso", "south", "west").forEach { view ->
            driveScreen(BOUND, storeFor(view), WIDTH, HEIGHT).use { screen ->
                assertFalse(FLAT_NOTICE in screen.textsUnder("board"), view)
            }
        }
    }

    @Test
    fun the_south_and_west_elevations_say_how_much_the_heights_are_stretched_and_the_iso_says_nothing() {
        listOf("south" to FlatView.SOUTH, "west" to FlatView.WEST).forEach { (view, flat) ->
            val times = exaggerationFor(board, flat, WIDTH.toDouble(), HEIGHT.toDouble(), DRAWING_MARGIN_PX)
            driveScreen(BOUND, storeFor(view), WIDTH, HEIGHT).use { screen ->
                assertTrue("Heights are drawn $times times their true scale." in screen.textsUnder("board"), view)
            }
        }
        driveScreen(BOUND, storeFor("iso"), WIDTH, HEIGHT).use { screen ->
            assertFalse(screen.textsUnder("board").any { it.startsWith("Heights are drawn") })
        }
    }
}

private const val FLAT_NOTICE = "The scene states no part heights, so every part is drawn flat at its base."

private val FLAT_SCENE = """
    {"units":"mm","level_feet":1,"tile_feet":1.0,"cols":2,"rows":2,"window":{"x0":0,"y0":0,"x1":610,"y1":610},
     "types":[{"id":"slab","label":"Slab"}],
     "footprints":{"items":[{"id":"s1","type":"slab","col":0,"row":0,"width":2,"height":2}]}}
""".trimIndent()
