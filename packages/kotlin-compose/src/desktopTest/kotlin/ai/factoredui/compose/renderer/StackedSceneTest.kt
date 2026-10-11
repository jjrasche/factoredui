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
import androidx.compose.ui.unit.DpRect
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

private const val CROWDING_SCALE = """{"channel":"crowding","stops":[{"up_to":0.25,"color":"#3a7ca5","label":"quiet"},{"up_to":1,"color":"#e0a030","label":"busy"},{"color":"#b03a2e","label":"over full"}],"unknown_color":"#9aa4ad","unknown_label":"no count","format":"percent"}"""

private const val WEAR_SCALE = """{"channel":"wear","stops":[{"up_to":0.5,"color":"#2a9d8f","label":"fresh"},{"color":"#6a4c93","label":"worn"}],"unknown_label":"no wear figure"}"""

private const val SCHEMES = """{"scheme":"{board.scheme}","schemes":{"crowding":$CROWDING_SCALE,"wear":$WEAR_SCALE}}"""

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

private val SCROLLED = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"page","type":"scrollview","children":[
      {"id":"board","type":"scene","props":{"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"{board.view}"}}
    ]}}
    """.trimIndent(),
)

private val SCHEMED =stackedSceneSpec("\"{board.selected}\"", SCHEMES)

private val WEAR_ONLY = stackedSceneSpec("\"{board.selected}\"", WEAR_SCALE)

private fun storeFor(view: String, picked: List<String> = emptyList(), scene: Any? = stackedBoardText()) = mapOf(
    "board" to mapOf("scene" to scene, "view" to view, "picked" to picked),
)

private fun storeWithScheme(scheme: Any?) = mapOf(
    "board" to mapOf("scene" to stackedBoardText(), "view" to "plan", "picked" to emptyList<String>(), "scheme" to scheme),
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

private const val WHITE_SCALE = """{"channel":"crowding","stops":[{"color":"#ffffff","label":"white"}],"unknown_color":"#ffffff","unknown_label":"also white"}"""

private val WHITE_LEGEND = stackedSceneSpec("\"{board.selected}\"", WHITE_SCALE)

private const val WHITE_RGB = 0xFFFFFF

private fun DpRect.centreX(): Float = (left.value + right.value) / 2

private fun DpRect.centreY(): Float = (top.value + bottom.value) / 2

private fun assertWhiteSwatchIsOutlined(theme: SpecTheme) {
    driveScreen(WHITE_LEGEND, storeFor("plan"), WIDTH, HEIGHT, theme).use { screen ->
        val png = screen.frame().png
        val swatch = checkNotNull(screen.boundsOf(legendSwatchTag("board", 0))) { "the white swatch drew no region" }
        val border = pixelAt(png, swatch.left.value to swatch.centreY()) and 0xFFFFFF
        val beside = pixelAt(png, swatch.left.value - 2 to swatch.centreY()) and 0xFFFFFF
        val interior = pixelAt(png, swatch.centreX() to swatch.centreY()) and 0xFFFFFF
        assertEquals(WHITE_RGB, interior, "the swatch keeps the stop's own colour inside its outline")
        assertNotEquals(WHITE_RGB, border, "the swatch border is not the white fill")
        assertNotEquals(beside, border, "the swatch border differs from the legend behind it")
        assertNotEquals(theme.ground.toRgbInt(), border, "the swatch border differs from the page")
    }
}

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
    fun a_scene_directly_inside_a_scrollview_takes_a_height_and_draws_its_parts() {
        listOf("plan", "south").forEach { view ->
            driveScreen(SCROLLED, storeFor(view), WIDTH, HEIGHT).use { screen ->
                val box = checkNotNull(screen.boundsOf("board")) { "the scene drew no region" }
                assertTrue(box.bottom.value - box.top.value >= 200f, "$view height ${box.bottom.value - box.top.value}")
                assertEquals("drawn=12 highlighted=0 faded=0 selected=", screen.stateDescriptionOf(partsTag("board")), view)
                val image = ImageIO.read(ByteArrayInputStream(screen.frame().png))
                val ground = SpecTheme.LIGHT.ground.toRgbInt()
                val inked = (box.top.value.toInt() until minOf(box.bottom.value.toInt(), image.height)).sumOf { y ->
                    (0 until WIDTH).count { x -> image.getRGB(x, y) and 0xFFFFFF != ground }
                }
                assertTrue(inked > 1000, "$view drew $inked non-page pixels")
            }
        }
    }

    @Test
    fun a_white_legend_swatch_on_a_light_page_is_outlined_and_keeps_its_white_fill() = assertWhiteSwatchIsOutlined(SpecTheme.LIGHT)

    @Test
    fun a_white_legend_swatch_on_a_dark_page_is_outlined_and_keeps_its_white_fill() = assertWhiteSwatchIsOutlined(SpecTheme.DARK)

    @Test
    fun the_unknown_swatch_is_outlined_too() {
        driveScreen(WHITE_LEGEND, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val png = screen.frame().png
            val swatch = checkNotNull(screen.boundsOf(legendSwatchTag("board", 1))) { "the unknown swatch drew no region" }
            assertEquals(SpecTheme.LIGHT.hairline.toRgbInt(), pixelAt(png, swatch.left.value to swatch.centreY()) and 0xFFFFFF)
        }
    }

    @Test
    fun the_popup_follows_the_formatted_value_with_the_label_of_its_stop() {
        driveScreen(SCHEMED, storeWithScheme("crowding"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p2", StackedDrawing.PLAN)
            screen.tapAt(x, y)
            assertEquals(listOf("Plate", "p2 · 50% · busy"), screen.textsUnder(popupTag("board")))
        }
    }

    @Test
    fun a_part_with_no_value_in_the_channel_shows_the_unknown_label_in_its_popup() {
        driveScreen(SCHEMED, storeWithScheme("crowding"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p4", StackedDrawing.PLAN)
            screen.tapAt(x, y)
            assertEquals(listOf("Plate", "p4 · no count"), screen.textsUnder(popupTag("board")))
        }
    }

    @Test
    fun a_part_with_no_value_and_a_scale_with_no_unknown_label_shows_only_its_id() {
        driveScreen(COLOURED, storeFor("plan"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p4", StackedDrawing.PLAN)
            screen.tapAt(x, y)
            assertEquals(listOf("Plate", "p4"), screen.textsUnder(popupTag("board")))
        }
    }

    @Test
    fun the_open_popup_follows_the_scheme_when_the_bound_scheme_changes() {
        driveScreen(SCHEMED, storeWithScheme("crowding"), WIDTH, HEIGHT).use { screen ->
            val (x, y) = centreOf(board, "p2", StackedDrawing.PLAN)
            screen.tapAt(x, y)
            screen.type("board.scheme", "wear")
            assertEquals(listOf("Plate", "p2 · 0.9 · worn"), screen.textsUnder(popupTag("board")))
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
    fun switching_the_bound_scheme_repaints_the_part_in_the_second_scales_colour_and_swaps_the_legend() {
        val wearPixel = driveScreen(WEAR_ONLY, storeFor("plan"), WIDTH, HEIGHT).use { pixelAt(it.frame().png, centreOf(board, "p2", StackedDrawing.PLAN)) }
        driveScreen(SCHEMED, storeWithScheme("crowding"), WIDTH, HEIGHT).use { screen ->
            val p2 = centreOf(board, "p2", StackedDrawing.PLAN)
            val crowdingPixel = pixelAt(screen.frame().png, p2)
            assertEquals(listOf("quiet", "busy", "over full", "no count"), screen.textsUnder(legendTag("board")))
            screen.type("board.scheme", "wear")
            val switchedPixel = pixelAt(screen.frame().png, p2)
            assertNotEquals(crowdingPixel, switchedPixel)
            assertEquals(wearPixel, switchedPixel, "the wear scheme paints p2 as the single wear scale does")
            assertEquals(listOf("fresh", "worn", "no wear figure"), screen.textsUnder(legendTag("board")))
        }
    }

    @Test
    fun an_unbound_scheme_colours_by_the_first_scheme_and_says_nothing() {
        driveScreen(SCHEMED, storeWithScheme(null), WIDTH, HEIGHT).use { screen ->
            assertEquals(listOf("quiet", "busy", "over full", "no count"), screen.textsUnder(legendTag("board")))
            assertFalse(screen.textsUnder("board").any { it.startsWith("color_by") })
        }
    }

    @Test
    fun a_scheme_the_spec_does_not_hold_paints_one_colour_and_names_the_schemes_on_screen() {
        val uncoloured = driveScreen(BOUND, storeFor("plan"), WIDTH, HEIGHT).use { pixelAt(it.frame().png, centreOf(board, "p2", StackedDrawing.PLAN)) }
        driveScreen(SCHEMED, storeWithScheme("heat"), WIDTH, HEIGHT).use { screen ->
            assertTrue("color_by scheme 'heat' is not one of the schemes crowding, wear; parts are drawn in one colour." in screen.textsUnder("board"))
            assertEquals(uncoloured, pixelAt(screen.frame().png, centreOf(board, "p2", StackedDrawing.PLAN)))
            assertEquals(emptyList(), screen.textsUnder(legendTag("board")))
        }
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
