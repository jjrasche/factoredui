package ai.factoredui.compose.renderer

import ai.factoredui.compose.render.renderSpecToPng
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PHONE_WIDTH_PX = 780
private const val PHONE_HEIGHT_PX = 1560
private const val PHONE_DENSITY = 2f
private const val COLUMN_ROWS = 5
private const val COLUMN_COLUMNS = 4
private const val BAY_MM = 4000
private const val STOREY_MM = 3000

private fun utilisationOf(index: Int): Any? = when (index) {
    3, 11 -> null
    7, 19 -> 1.3
    else -> 0.15 + (index * 37 % 80) / 100.0
}

private fun sceneObject(id: String, type: String, index: Int, x: Int, y: Int, z: Int, w: Int, d: Int, h: Int): Map<String, Any?> {
    val value = utilisationOf(index)
    val source: Any? = if (value == null) mapOf("unknown" to "no load case") else "row-$index"
    return mapOf(
        "id" to id, "type" to type, "system" to "frame",
        "box_mm" to mapOf("x" to x, "y" to y, "z" to z, "w" to w, "d" to d, "h" to h),
        "fields" to mapOf("utilisation" to mapOf("value" to value, "unit" to "ratio", "source" to source)),
    )
}

private fun frameObjects(): List<Map<String, Any?>> {
    val grid = (0 until COLUMN_COLUMNS).flatMap { column -> (0 until COLUMN_ROWS).map { row -> column to row } }
    val columns = grid.mapIndexed { index, (column, row) -> sceneObject("col-$column-$row", "column", index, column * BAY_MM, row * BAY_MM, 0, 300, 300, STOREY_MM) }
    val alongX = grid.filter { it.first < COLUMN_COLUMNS - 1 }.mapIndexed { index, (column, row) ->
        sceneObject("beam-x-$column-$row", "beam", index + columns.size, column * BAY_MM + BAY_MM / 2, row * BAY_MM, STOREY_MM, BAY_MM - 300, 250, 400)
    }
    val alongY = grid.filter { it.second < COLUMN_ROWS - 1 }.mapIndexed { index, (column, row) ->
        sceneObject("beam-y-$column-$row", "beam", index + columns.size + alongX.size, column * BAY_MM, row * BAY_MM + BAY_MM / 2, STOREY_MM, 250, BAY_MM - 300, 400)
    }
    return columns + alongX + alongY
}

private fun frameAnswer(): Map<String, Any?> = mapOf(
    "objects" to frameObjects(),
    "limits" to mapOf("utilisation" to mapOf("high" to 1.0, "source" to "setting:frame.utilisation_limit")),
    "page" to mapOf("region" to "frame", "next" to null),
)

private fun sceneSpec(colourBy: String, extra: String): String = """
    { "spec_version": 1, "renderer_min": 1,
      "root": { "id": "root", "type": "stack", "props": { "fill": true }, "children": [
        { "id": "frame", "type": "volume", "props": { "fill": true, "anchor": "fill", $extra "scene": "{engine.scene}", "colour_by": "$colourBy", "colour_caption": "demand over capacity", "value_ramp": "heat", "lighting": "sun", "fit": true, "fov_deg": 12 } } ] } }
"""

private fun pictureOf(colourBy: String, extra: String = ""): ByteArray = renderSpecToPng(
    specJson = sceneSpec(colourBy, extra),
    width = PHONE_WIDTH_PX,
    height = PHONE_HEIGHT_PX,
    density = PHONE_DENSITY,
    data = mapOf("engine" to mapOf("scene" to frameAnswer())),
)

private fun printPicture(name: String, png: ByteArray) {
    println("SCENE-PNG $name ${Base64.getEncoder().encodeToString(png)}")
    File("build/scene-volume-$name.png").apply { parentFile.mkdirs() }.writeBytes(png)
}

class SceneVolumeRenderTest {

    @Test
    fun a_scene_answer_draws_its_members_coloured_by_the_named_field_and_is_shown_as_a_picture() {
        val coloured = pictureOf("utilisation")
        val plain = pictureOf("no_such_field")
        printPicture("utilisation", coloured)
        printPicture("plain", plain)
        printPicture("side", pictureOf("utilisation", """ "camera": "side", """))
        assertTrue(coloured.isNotEmpty())
        assertFalse(coloured.contentEquals(plain))
    }

    @Test
    fun a_side_elevation_seen_from_the_east_or_the_west_differs_from_the_south_and_each_other() {
        val south = pictureOf("utilisation", """ "camera": "side", """)
        val east = pictureOf("utilisation", """ "camera": "side", "view": "east", """)
        val west = pictureOf("utilisation", """ "camera": "side", "view": "west", """)
        printPicture("side-east", east)
        assertFalse(south.contentEquals(east))
        assertFalse(east.contentEquals(west))
    }
}
