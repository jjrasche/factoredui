package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Point
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val WIDTH = 800
private const val HEIGHT = 600

private const val BOARD = """
{"units":"mm","level_feet":1,"tile_feet":1.0,"cols":20,"rows":20,
 "window":{"x0":0,"y0":0,"x1":6000,"y1":6000},
 "types":[{"id":"box","label":"Box","tags":[],"footprint_mm":[300,300],"height_mm":null},
          {"id":"panel","label":"Panel","tags":[],"footprint_mm":[3000,6000],"height_mm":0}],
 "stack_levels":[{"stack_level":0,"z_base_mm":0},{"stack_level":1,"z_base_mm":1500},{"stack_level":2,"z_base_mm":2000}],
 "footprints":{"version":1,"provenance":{"tag":"computed","kind":"derived"},"items":[
   {"id":"post","type":"box","col":2,"row":2,"width":1,"height":1,"height_mm":800,"stack_level":0,"z_mm":0},
   {"id":"short","type":"box","col":8,"row":8,"width":2,"height":2,"height_mm":1000,"stack_level":0,"z_mm":0},
   {"id":"tall","type":"box","col":10,"row":7,"width":2,"height":5,"height_mm":1500,"stack_level":0,"z_mm":0},
   {"id":"cap","type":"box","col":10,"row":7,"width":2,"height":5,"height_mm":300,"stack_level":1,"z_mm":1500},
   {"id":"west_panel","type":"panel","col":0,"row":0,"width":10,"height":20,"height_mm":0,"stack_level":2,"z_mm":2000},
   {"id":"east_panel","type":"panel","col":10,"row":0,"width":10,"height":20,"height_mm":0,"stack_level":2,"z_mm":2000}]}}
"""

private class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val z0: Double, val z1: Double)

private val BOXES = mapOf(
    "post" to Box(600.0, 600.0, 900.0, 900.0, 0.0, 800.0),
    "short" to Box(2400.0, 2400.0, 3000.0, 3000.0, 0.0, 1000.0),
    "tall" to Box(3000.0, 2100.0, 3600.0, 3600.0, 0.0, 1500.0),
    "cap" to Box(3000.0, 2100.0, 3600.0, 3600.0, 1500.0, 1800.0),
    "west_panel" to Box(0.0, 0.0, 3000.0, 6000.0, 2000.0, 2000.0),
    "east_panel" to Box(3000.0, 0.0, 6000.0, 6000.0, 2000.0, 2000.0),
)

private val TOP_LEVEL = listOf("west_panel", "east_panel")

private fun specOf(root: String): Spec = json.decodeFromString(Spec.serializer(), """{"spec_version":1,"renderer_min":1,"root":$root}""")

private fun boundScene(view: String) =
    """{"id":"board","type":"scene","props":{"view_state":{"renderer":"stacked_scene"},"scene_view":"{view.scene}","view":"$view",
       "quarter_turns":"{view.turns}","zoom":"{view.zoom}","pan_x":"{view.pan_x}","pan_y":"{view.pan_y}","selected":"{view.selected}"}}"""

private val ISO = specOf(boundScene("iso"))

private const val ROW = """{"id":"row-card","type":"card","props":{"padding":12},"children":[{"id":"row-text","type":"text","props":{"value":"{row.name}"}}]}"""

private fun pageWith(scene: String): Spec = specOf("""{"id":"page","type":"scrollview","children":[$scene,{"id":"rows","type":"list","props":{"data":"{rows}","itemTemplate":$ROW}}]}""")

private const val ZOOM_ONLY_SCENE = """{"id":"board","type":"scene","props":{"view_state":{"renderer":"stacked_scene"},"scene_view":"{view.scene}","view":"iso","zoom":"{view.zoom}"}}"""

private const val PLAIN_SCENE = """{"id":"board","type":"scene","props":{"view_state":{"renderer":"stacked_scene"},"scene_view":"{view.scene}","view":"iso"}}"""

private fun store(): Map<String, Any?> = mapOf(
    "view" to mapOf("scene" to BOARD, "turns" to 0, "zoom" to 1.0, "pan_x" to 0.0, "pan_y" to 0.0, "selected" to emptyList<String>()),
    "rows" to (0 until 40).map { mapOf("name" to "row $it") },
)

private fun portCall(port: Int, route: String, vararg query: Pair<String, String>): Pair<Int, String> {
    val encoded = query.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val connection = URI("http://127.0.0.1:$port/$route?$encoded").toURL().openConnection() as HttpURLConnection
    val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
    return connection.responseCode to stream.bufferedReader().readText()
}

private fun <T> driving(spec: Spec, width: Int = WIDTH, height: Int = HEIGHT, act: (port: Int, context: RenderContext, surface: SceneSurface) -> T): T {
    val context = RenderContext(initialData = store())
    SceneSurface(spec, context, width, height).use { surface ->
        val server = WindowControl(context, surface).start(0)
        try {
            return act(server.address.port, context, surface)
        } finally {
            server.stop(0)
        }
    }
}

private fun nodeAt(port: Int, id: String): JsonObject {
    val (status, body) = portCall(port, "tree", "id" to id)
    assertEquals(200, status, body)
    return json.parseToJsonElement(body).jsonObject
}

private fun sceneAt(port: Int): JsonObject = nodeAt(port, "board").getValue("scene").jsonObject

private fun JsonObject.part(id: String): JsonObject = getValue("parts").jsonArray.map { it.jsonObject }.first { it.getValue("id").jsonPrimitive.content == id }

private fun JsonObject.number(key: String): Double = getValue(key).jsonPrimitive.double

private fun JsonObject.turns(): Int = getValue("quarter_turns").jsonPrimitive.int

private fun JsonObject.box(): List<Int> = getValue("bounds").jsonObject.let { b -> listOf("x", "y", "w", "h").map { b.getValue(it).jsonPrimitive.int } }

private fun JsonObject.centre(): Point = getValue("centre").jsonObject.let { Point(it.getValue("x").jsonPrimitive.int, it.getValue("y").jsonPrimitive.int) }

private fun JsonObject.coveredBy(): List<String> = getValue("covered_by").jsonArray.map { it.jsonPrimitive.content }

private fun JsonObject.paintOrder(): List<String> = getValue("parts").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }

private fun setView(port: Int, key: String, value: String) = assertEquals(200, portCall(port, "set", "path" to "view.$key", "value" to value).first)

private fun viewValue(context: RenderContext, key: String): Any? = (context.data["view"] as Map<*, *>)[key]

private fun awaitScene(port: Int, isDrawn: (JsonObject) -> Boolean): JsonObject {
    val deadline = System.nanoTime() + 2_000_000_000L
    while (System.nanoTime() < deadline) {
        val scene = sceneAt(port)
        if (isDrawn(scene)) return scene
        Thread.sleep(10)
    }
    return sceneAt(port)
}

/**
 * Where the iso draws [part] at [quarterTurns], from the framing rule rather than the renderer: azimuth 45 plus 90 per quarter turn,
 * elevation 35.264; the frame is the tight box of every part's corners plus 24% each side and 6% top and bottom, fitted inside a 24 px margin.
 */
private fun expectedIsoBounds(part: String, quarterTurns: Int): List<Int> {
    val azimuth = (45.0 + 90.0 * quarterTurns) * PI / 180
    val elevation = 35.264 * PI / 180
    fun corners(box: Box) = listOf(box.x0, box.x1).flatMap { x -> listOf(box.y0, box.y1).flatMap { y -> listOf(box.z0, box.z1).map { z -> Triple(x, y, z) } } }
    fun u(x: Double, y: Double) = x * cos(azimuth) - y * sin(azimuth)
    fun v(x: Double, y: Double, z: Double) = z * cos(elevation) + (x * sin(azimuth) + y * cos(azimuth)) * sin(elevation)
    val every = BOXES.values.flatMap(::corners)
    val spanLeft = every.minOf { (x, y, _) -> u(x, y) }
    val spanRight = every.maxOf { (x, y, _) -> u(x, y) }
    val spanBottom = every.minOf { (x, y, z) -> v(x, y, z) }
    val spanTop = every.maxOf { (x, y, z) -> v(x, y, z) }
    val left = spanLeft - (spanRight - spanLeft) * 0.24
    val right = spanRight + (spanRight - spanLeft) * 0.24
    val bottom = spanBottom - (spanTop - spanBottom) * 0.06
    val top = spanTop + (spanTop - spanBottom) * 0.06
    val scale = minOf((WIDTH - 48.0) / (right - left), (HEIGHT - 48.0) / (top - bottom))
    val offsetX = (WIDTH - scale * (right - left)) / 2
    val offsetY = (HEIGHT - scale * (top - bottom)) / 2
    val own = corners(BOXES.getValue(part))
    val xs = own.map { (x, y, _) -> offsetX + (u(x, y) - left) * scale }
    val ys = own.map { (x, y, z) -> offsetY + (top - v(x, y, z)) * scale }
    return listOf(xs.min().roundToInt(), ys.min().roundToInt(), (xs.max() - xs.min()).roundToInt(), (ys.max() - ys.min()).roundToInt())
}

private fun assertNear(expected: List<Int>, actual: List<Int>, tolerance: Int, label: String) =
    assertTrue(expected.zip(actual).all { (e, a) -> abs(e - a) <= tolerance }, "$label: drawn $actual, expected $expected")

class SceneFramingTest {

    @Test
    fun the_scene_record_gives_the_quarter_turns_zoom_and_pan_it_drew_with() {
        driving(ISO) { port, _, _ ->
            val scene = sceneAt(port)
            assertEquals(0, scene.turns())
            assertEquals(listOf(1.0, 0.0, 0.0), listOf("zoom", "pan_x", "pan_y").map { scene.number(it) })
        }
    }

    @Test
    fun one_quarter_turn_moves_a_part_to_where_the_quarter_turn_puts_it() {
        driving(ISO) { port, _, _ ->
            assertNear(expectedIsoBounds("short", 0), sceneAt(port).part("short").box(), 1, "at 0")
            listOf(1, 2, 3).forEach { turns ->
                setView(port, "turns", "$turns")
                val scene = sceneAt(port)
                assertEquals(turns, scene.turns())
                listOf("short", "tall").forEach { id -> assertNear(expectedIsoBounds(id, turns), scene.part(id).box(), 1, "$id at $turns") }
            }
            assertNotEquals(expectedIsoBounds("short", 0), expectedIsoBounds("short", 1))
        }
    }

    @Test
    fun quarter_turns_of_4_draw_the_first_aspect_and_minus_1_the_fourth() {
        driving(ISO) { port, _, _ ->
            val first = sceneAt(port).part("short").box()
            setView(port, "turns", "3")
            val fourth = sceneAt(port).part("short").box()
            setView(port, "turns", "4")
            assertEquals(0, sceneAt(port).turns())
            assertEquals(first, sceneAt(port).part("short").box())
            setView(port, "turns", "-1")
            assertEquals(3, sceneAt(port).turns())
            assertEquals(fourth, sceneAt(port).part("short").box())
        }
    }

    @Test
    fun a_quarter_turns_that_is_not_a_whole_number_draws_the_first_aspect_and_a_notice_naming_the_scene() {
        driving(ISO) { port, _, _ ->
            val first = sceneAt(port).part("short").box()
            setView(port, "turns", "1.5")
            val scene = sceneAt(port)
            assertEquals(0, scene.turns())
            assertEquals(first, scene.part("short").box())
            assertEquals(listOf("scene 'board': quarter_turns must be a whole number, not '1.5'; the first aspect is drawn."), scene.getValue("notices").jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test
    fun two_wheel_notches_toward_the_screen_over_a_part_grow_it_by_the_square_of_the_zoom_per_notch_and_keep_the_point_under_the_pointer_in_iso_plan_and_south() {
        listOf("iso", "plan", "south").forEach { view ->
            driving(specOf(boundScene(view))) { port, context, _ ->
                val before = sceneAt(port).part("short")
                val pointer = before.centre()
                assertEquals(200, portCall(port, "wheel", "x" to "${pointer.x}", "y" to "${pointer.y}", "notches" to "-2").first)
                val after = sceneAt(port).part("short")
                val growth = 1.25 * 1.25
                assertEquals(growth, viewValue(context, "zoom") as Double, 1e-9, view)
                assertEquals(growth, sceneAt(port).number("zoom"), 1e-9, view)
                val (_, _, widthBefore, heightBefore) = before.box()
                val (_, _, widthAfter, heightAfter) = after.box()
                assertTrue(abs(widthAfter - widthBefore * growth) <= 2, "$view width $widthBefore -> $widthAfter")
                assertTrue(abs(heightAfter - heightBefore * growth) <= 2, "$view height $heightBefore -> $heightAfter")
                assertTrue(abs(after.centre().x - pointer.x) <= 1 && abs(after.centre().y - pointer.y) <= 1, "$view centre $pointer -> ${after.centre()}")
            }
        }
    }

    @Test
    fun a_ctrl_drag_pans_every_part_by_the_distance_dragged_and_writes_that_share_of_the_drawing_in_iso_plan_and_south() {
        listOf("iso", "plan", "south").forEach { view ->
            driving(specOf(boundScene(view))) { port, context, surface ->
                val before = sceneAt(port)
                surface.drag(Point(400, 300), Point(480, 330), isCtrlHeld = true)
                val after = awaitScene(port) { it.number("pan_x") != 0.0 }
                assertEquals(80.0 / WIDTH, after.number("pan_x"), 1e-9, view)
                assertEquals(30.0 / HEIGHT, after.number("pan_y"), 1e-9, view)
                assertEquals(80.0 / WIDTH, viewValue(context, "pan_x") as Double, 1e-9, view)
                assertEquals(30.0 / HEIGHT, viewValue(context, "pan_y") as Double, 1e-9, view)
                listOf("short", "tall").forEach { id ->
                    val (x0, y0) = before.part(id).box()
                    val (x1, y1) = after.part(id).box()
                    assertTrue(abs(x1 - x0 - 80) <= 1 && abs(y1 - y0 - 30) <= 1, "$view $id moved by ${x1 - x0},${y1 - y0}")
                }
                assertEquals(0, after.turns(), view)
                assertEquals(emptyList<String>(), viewValue(context, "selected"), view)
            }
        }
    }

    @Test
    fun the_top_panels_are_covered_by_nothing_at_any_aspect_and_what_covers_a_low_box_follows_the_aspect() {
        driving(ISO) { port, _, _ ->
            (0..3).forEach { turns ->
                setView(port, "turns", "$turns")
                val scene = sceneAt(port)
                TOP_LEVEL.forEach { id -> assertEquals(emptyList(), scene.part(id).coveredBy(), "$id at $turns") }
            }
            setView(port, "turns", "0")
            val first = sceneAt(port).part("short").coveredBy()
            setView(port, "turns", "2")
            val opposite = sceneAt(port).part("short").coveredBy()
            assertEquals(listOf("west_panel"), first)
            assertEquals(listOf("tall", "cap", "east_panel"), opposite)
        }
    }

    @Test
    fun a_long_drag_steps_exactly_one_aspect_in_its_direction_and_writes_it_as_0_to_3() {
        driving(ISO) { port, context, surface ->
            surface.drag(Point(200, 300), Point(600, 310), steps = 40)
            assertEquals(1, awaitScene(port) { it.turns() == 1 }.turns())
            assertEquals(1, viewValue(context, "turns"))
            surface.drag(Point(600, 300), Point(200, 300), steps = 40)
            assertEquals(0, awaitScene(port) { it.turns() == 0 }.turns())
            surface.drag(Point(600, 300), Point(500, 300))
            assertEquals(3, awaitScene(port) { it.turns() == 3 }.turns())
            assertEquals(3, viewValue(context, "turns"), "a step left from the first aspect writes 3, never -1")
            surface.drag(Point(200, 300), Point(300, 300))
            assertEquals(0, awaitScene(port) { it.turns() == 0 }.turns())
            assertEquals(0, viewValue(context, "turns"), "a step right from the fourth aspect writes 0, never 4")
            assertEquals(emptyList<String>(), viewValue(context, "selected"))
        }
    }

    @Test
    fun a_drag_past_the_touch_slop_but_short_of_the_step_neither_turns_nor_selects_and_a_tap_selects() {
        driving(ISO) { port, context, surface ->
            val panel = sceneAt(port).part("east_panel").centre()
            surface.drag(panel, Point(panel.x + 30, panel.y))
            assertEquals(200, portCall(port, "set", "path" to "probe", "value" to "0").first)
            assertEquals(0, sceneAt(port).turns())
            assertEquals(emptyList<String>(), viewValue(context, "selected"))
            assertEquals(200, portCall(port, "click", "x" to "${panel.x}", "y" to "${panel.y}").first)
            assertEquals(listOf("east_panel"), viewValue(context, "selected"))
            assertEquals("selected", sceneAt(port).part("east_panel").getValue("outline").jsonPrimitive.content)
        }
    }

    @Test
    fun after_forty_notches_in_the_bound_zoom_is_the_held_sixteen_the_scene_reports() {
        driving(ISO) { port, context, _ ->
            repeat(40) { assertEquals(200, portCall(port, "wheel", "x" to "300", "y" to "250", "notches" to "-1").first) }
            assertEquals(16.0, viewValue(context, "zoom"))
            assertEquals(16.0, sceneAt(port).number("zoom"))
            assertEquals(viewValue(context, "pan_x") as Double, sceneAt(port).number("pan_x"), 0.0)
            assertEquals(viewValue(context, "pan_y") as Double, sceneAt(port).number("pan_y"), 0.0)
        }
    }

    @Test
    fun the_plan_ignores_quarter_turns_and_a_plain_drag_there_steps_nothing() {
        driving(specOf(boundScene("plan"))) { port, context, surface ->
            val first = sceneAt(port)
            setView(port, "turns", "1")
            val turned = sceneAt(port)
            assertEquals(first.part("short").box(), turned.part("short").box())
            assertEquals(first.paintOrder(), turned.paintOrder())
            surface.drag(Point(200, 300), Point(600, 300))
            assertEquals(200, portCall(port, "set", "path" to "probe", "value" to "0").first)
            assertEquals(1.0, viewValue(context, "turns"), "the control port wrote 1 as a number; the drag left it alone")
        }
    }

    @Test
    fun a_scene_with_neither_quarter_turns_nor_zoom_bound_ignores_a_drag_and_leaves_the_wheel_to_the_page() {
        driving(pageWith(PLAIN_SCENE), 400, 300) { port, _, surface ->
            val rowBefore = nodeAt(port, "rows/0/row-card").box()
            surface.drag(Point(150, 60), Point(350, 60))
            assertEquals(200, portCall(port, "set", "path" to "probe", "value" to "0").first)
            assertEquals(0, sceneAt(port).turns())
            assertEquals(200, portCall(port, "wheel", "x" to "200", "y" to "60", "notches" to "3").first)
            val rowAfter = nodeAt(port, "rows/0/row-card").box()
            assertTrue(rowAfter[1] < rowBefore[1], "the page scrolled: row 0 at ${rowBefore[1]} then ${rowAfter[1]}")
            assertEquals(1.0, sceneAt(port).number("zoom"))
        }
    }

    @Test
    fun a_scene_with_its_zoom_bound_takes_the_wheel_and_the_page_stays_put() {
        driving(pageWith(ZOOM_ONLY_SCENE), 400, 300) { port, context, _ ->
            val rowBefore = nodeAt(port, "rows/0/row-card").box()
            assertEquals(200, portCall(port, "wheel", "x" to "200", "y" to "60", "notches" to "-1").first)
            assertEquals(rowBefore, nodeAt(port, "rows/0/row-card").box())
            assertEquals(1.25, viewValue(context, "zoom") as Double, 1e-9)
        }
    }

    @Test
    fun with_only_the_zoom_bound_a_drag_steps_the_aspect_the_scene_holds_and_leaves_the_store_alone() {
        driving(pageWith(ZOOM_ONLY_SCENE), 400, 300) { port, context, surface ->
            surface.drag(Point(100, 60), Point(300, 60))
            assertEquals(1, awaitScene(port) { it.turns() == 1 }.turns())
            assertEquals(0, viewValue(context, "turns"))
        }
    }
}
