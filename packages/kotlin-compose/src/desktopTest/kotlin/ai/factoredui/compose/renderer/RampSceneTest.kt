package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.DrivenScreen
import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.rampColourFor
import ai.factoredui.compose.ramp.readColourRamp
import ai.factoredui.compose.scene.readSceneView
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.stackedscene.StackedDrawing
import ai.factoredui.compose.stackedscene.fitDrawing
import ai.factoredui.compose.stackedscene.mmToCanvas
import ai.factoredui.compose.stackedscene.partRectMm
import androidx.compose.ui.unit.DpRect
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val WIDTH = 800
private const val HEIGHT = 400

private val VALUES = listOf("a" to "0", "b" to "0.25", "c" to "0.5", "d" to "0.75", "e" to "1", "f" to "1.3")

private val ROW_SCENE = """
    {"units":"mm","level_feet":1,"tile_feet":1.0,"cols":7,"rows":1,"window":{"x0":0,"y0":0,"x1":7000,"y1":1000},
     "types":[{"id":"slab","label":"Slab"}],
     "footprints":{"items":[${"abcdefg".mapIndexed { col, id -> """{"id":"$id","type":"slab","col":$col,"row":0,"width":1,"height":1}""" }.joinToString(",")}]},
     "annotations":{"items":[${VALUES.joinToString(",") { (id, value) -> """{"ref_kind":"FOOTPRINT","ref_id":"$id","kind":"hp","text":"$value","source":null,"value":$value}""" }}]}}
""".trimIndent()

private const val RAMP = """{"stops":[[0,"#3b7dd8"],[0.5,"#e6a93a"],[1,"#b5382a"]],"over":"#5a1e8c","no_figure":"#9aa3ab","legend":[{"at":0,"text":"0"},{"at":1,"text":"full"}],"over_text":"over","no_figure_text":"no figure"}"""

private const val RAMP_SCALE = """{"channel":"hp","ramp":$RAMP,"format":"percent"}"""

private const val EXACT_STOPS_SCALE = """{"channel":"hp","stops":[{"up_to":0,"color":"#3b7dd8"},{"up_to":0.25,"color":"#919389"},{"up_to":0.5,"color":"#e6a93a"},{"up_to":0.75,"color":"#ce7132"},{"up_to":1,"color":"#b5382a","label":"stops"},{"color":"#5a1e8c"}],"unknown_color":"#9aa3ab"}"""

private fun sceneSpec(colorBy: String) = json.decodeFromString(
    Spec.serializer(),
    """{"spec_version":1,"renderer_min":1,"root":{"id":"board","type":"scene","props":{"view_state":{"renderer":"stacked_scene"},"scene_view":"{board.scene}","view":"plan","color_by":$colorBy}}}""",
)

private fun store(scheme: String? = null) = mapOf("board" to mapOf("scene" to ROW_SCENE, "scheme" to scheme))

private fun channels(rgb: Int): List<Int> = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

private fun assertWithin(expected: Int, actual: Int, levels: Int, what: String) {
    channels(expected).zip(channels(actual)).forEach { (e, a) -> assertTrue(abs(e - a) <= levels, "$what: expected ${expected.toString(16)}, drew ${actual.toString(16)}") }
}

private fun partPixels(screen: DrivenScreen): Map<String, Int> {
    val drawing = checkNotNull(screen.boundsOf(partsTag("board"))) { "the scene drew no drawing" }
    val scene = readSceneView(ROW_SCENE)
    val fit = fitDrawing(scene, StackedDrawing.PLAN, drawing.width(), drawing.height())
    val image = ImageIO.read(ByteArrayInputStream(screen.frame().png))
    return scene.footprints!!.items.associate { part ->
        val rect = partRectMm(scene, part)
        val (x, y) = mmToCanvas(fit, (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2)
        part.id to (image.getRGB((drawing.left.value + x).toInt(), (drawing.top.value + y).toInt()) and 0xFFFFFF)
    }
}

private fun DpRect.width(): Double = (right.value - left.value).toDouble()

private fun DpRect.height(): Double = (bottom.value - top.value).toDouble()

private val RAMP_MODEL = (readColourRamp("ramp", json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), RAMP).let(::plainOf)) as RampReading.Readable).ramp

private fun plainOf(element: kotlinx.serialization.json.JsonElement): Any? = when (element) {
    is kotlinx.serialization.json.JsonObject -> element.mapValues { plainOf(it.value) }
    is kotlinx.serialization.json.JsonArray -> element.map(::plainOf)
    is kotlinx.serialization.json.JsonPrimitive -> if (element.isString) element.content else element.content.toDoubleOrNull()
    else -> null
}

class RampSceneTest {

    @Test
    fun each_part_takes_the_ramp_colour_of_its_value_exactly_as_a_stop_of_that_colour_draws_it() {
        val ramped = driveScreen(sceneSpec(RAMP_SCALE), store(), WIDTH, HEIGHT).use(::partPixels)
        val exact = driveScreen(sceneSpec(EXACT_STOPS_SCALE), store(), WIDTH, HEIGHT).use(::partPixels)
        "abcdefg".map { it.toString() }.forEach { id -> assertWithin(exact.getValue(id), ramped.getValue(id), 1, "part $id") }
        assertEquals(7, ramped.values.toSet().size, "0, 0.25, 0.5, 0.75, 1, over and no figure are seven colours")
    }

    @Test
    fun the_legend_is_a_gradient_bar_whose_ends_are_the_first_and_last_stop_colours_with_labels_at_their_values() {
        driveScreen(sceneSpec(RAMP_SCALE), store(), WIDTH, HEIGHT).use { screen ->
            val bar = checkNotNull(screen.boundsOf(rampBarTag("board"))) { "no gradient bar was drawn" }
            val image = ImageIO.read(ByteArrayInputStream(screen.frame().png))
            val y = ((bar.top.value + bar.bottom.value) / 2).toInt()
            val left = bar.left.value.toInt() + 1
            val right = bar.right.value.toInt() - 2
            val valueAt = { x: Int -> (x + 0.5 - bar.left.value) / bar.width() }
            assertWithin((rampColourFor(RAMP_MODEL, valueAt(left)) and 0xFFFFFF).toInt(), image.getRGB(left, y) and 0xFFFFFF, 2, "left end")
            assertWithin((rampColourFor(RAMP_MODEL, valueAt(right)) and 0xFFFFFF).toInt(), image.getRGB(right, y) and 0xFFFFFF, 2, "right end")
            assertWithin(0x3B7DD8, image.getRGB(left, y) and 0xFFFFFF, 8, "the left end is the first stop's colour")
            assertWithin(0xB5382A, image.getRGB(right, y) and 0xFFFFFF, 8, "the right end is the last stop's colour")
            val zero = checkNotNull(screen.boundsOfText("0"))
            val full = checkNotNull(screen.boundsOfText("full"))
            assertTrue(abs((zero.left.value + zero.right.value) / 2 - bar.left.value) <= 2, "the 0 label sits at the bar's start: $zero vs $bar")
            assertTrue(abs((full.left.value + full.right.value) / 2 - bar.right.value) <= 2, "the full label sits at the bar's end: $full vs $bar")
            assertTrue(zero.top.value >= bar.bottom.value, "labels sit under the bar")
            val legendTexts = screen.textsUnder(legendTag("board"))
            assertTrue("over" in legendTexts && "no figure" in legendTexts, "over and no figure have swatches: $legendTexts")
        }
    }

    @Test
    fun the_ramp_legend_labels_are_readable_on_either_theme() {
        listOf(SpecTheme.LIGHT, SpecTheme.DARK).forEach { theme ->
            driveScreen(sceneSpec(RAMP_SCALE), store(), WIDTH, HEIGHT, theme).use { screen ->
                val png = screen.frame().png
                listOf("full", "over", "no figure").forEach { label ->
                    val ratio = inkContrastIn(png, checkNotNull(screen.boundsOfText(label)))
                    assertTrue(ratio >= 4.5, "dark=${theme.isDark} '$label' $ratio:1")
                }
            }
        }
    }

    @Test
    fun a_scheme_of_ramp_and_a_scheme_of_stops_switch_live_with_their_legends() {
        val schemes = """{"scheme":"{board.scheme}","schemes":{"gradient":$RAMP_SCALE,"banded":$EXACT_STOPS_SCALE}}"""
        driveScreen(sceneSpec(schemes), store("gradient"), WIDTH, HEIGHT).use { screen ->
            val gradientPixels = partPixels(screen)
            assertTrue(screen.boundsOf(rampBarTag("board")) != null)
            screen.type("board.scheme", "banded")
            assertNull(screen.boundsOf(rampBarTag("board")), "the banded scheme draws no gradient bar")
            assertEquals(listOf("stops"), screen.textsUnder(legendTag("board")))
            assertWithin(gradientPixels.getValue("b"), partPixels(screen).getValue("b"), 1, "b is the same colour both ways")
        }
    }

    @Test
    fun a_ramp_the_renderer_cannot_read_is_named_on_screen_and_the_parts_are_grey() {
        val bad = """{"channel":"hp","ramp":{"stops":[[0,"#000000"]]}}"""
        driveScreen(sceneSpec(bad), store(), WIDTH, HEIGHT).use { screen ->
            assertTrue("color_by ramp needs at least 2 stops, not 1; parts are drawn in one colour." in screen.textsUnder("board"))
            assertEquals(1, partPixels(screen).values.toSet().size)
        }
    }

    @Test
    fun a_tapped_part_at_a_legend_value_names_that_legend_text_and_one_between_names_nothing() {
        driveScreen(sceneSpec(RAMP_SCALE), store(), WIDTH, HEIGHT).use { screen ->
            val drawing = checkNotNull(screen.boundsOf(partsTag("board")))
            val scene = readSceneView(ROW_SCENE)
            val fit = fitDrawing(scene, StackedDrawing.PLAN, drawing.width(), drawing.height())
            fun tap(id: String) {
                val rect = partRectMm(scene, scene.footprints!!.items.first { it.id == id })
                val (x, y) = mmToCanvas(fit, (rect.x0 + rect.x1) / 2, (rect.y0 + rect.y1) / 2)
                screen.tapAt((drawing.left.value + x).toFloat(), (drawing.top.value + y).toFloat())
            }
            tap("e")
            assertEquals(listOf("Slab", "e · 100% · full"), screen.textsUnder(popupTag("board")))
            tap("e")
            tap("b")
            assertEquals(listOf("Slab", "b · 25%"), screen.textsUnder(popupTag("board")))
        }
    }
}
