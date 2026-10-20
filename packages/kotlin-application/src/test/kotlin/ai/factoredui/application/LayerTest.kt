package ai.factoredui.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun geometryWith(layers: String) = readGeometry(
    """{ "pieces": [
        { "id": "west", "system": "walls", "x": 0, "y": 0, "z": 0, "dx": 114, "dy": 5000, "dz": 2400 $layers },
        { "id": "east", "system": "walls", "x": 2886, "y": 0, "z": 0, "dx": 114, "dy": 5000, "dz": 2400 $layers },
        { "id": "floor", "system": "floor", "x": 0, "y": 0, "z": -114, "dx": 3000, "dy": 5000, "dz": 114 $layers } ],
      "contacts": [], "cuts": [], "unmeasured": [] }""",
)

private const val SIP = """, "layers": [ { "name": "outer skin", "material": "OSB", "thickness_mm": 11, "source": "research row a" }, { "name": "core", "material": "EPS", "thickness_mm": 92, "source": null }, { "name": "inner skin", "material": "OSB", "thickness_mm": 11, "source": null } ]"""

private fun List<Map<String, Any?>>.ids(prefix: String) = filter { (it["id"] as String).startsWith(prefix) }

class LayerTest {
    @Test
    fun a_piece_is_read_with_its_layers_outside_to_inside() {
        val wall = geometryWith(SIP).pieces.first()
        assertEquals(listOf("OSB", "EPS", "OSB"), wall.layers.map { it.material })
        assertEquals(114.0, wall.layers.sumOf { it.thicknessMm })
        assertEquals("research row a", wall.layers.first().source)
    }

    @Test
    fun a_piece_with_no_layers_keeps_the_total_thickness_label() {
        val shapes = sectionDrawing(geometryWith(""))!!.shapes
        assertTrue(shapes.ids("size:thickness:west").isNotEmpty())
        assertTrue(shapes.ids("layer:").isEmpty())
    }

    @Test
    fun seven_joists_close_together_are_named_by_one_label_and_two_far_walls_by_two() {
        val joists = (0 until 7).joinToString(",") { """{ "id": "joist$it", "system": "joists", "x": ${1000 + it * 406}, "y": 0, "z": 2300, "dx": 38, "dy": 5000, "dz": 140 }""" }
        val geometry = readGeometry(
            """{ "pieces": [
                { "id": "west", "system": "walls", "x": 0, "y": 0, "z": 0, "dx": 114, "dy": 5000, "dz": 2400 },
                { "id": "east", "system": "walls", "x": 2886, "y": 0, "z": 0, "dx": 114, "dy": 5000, "dz": 2400 },
                { "id": "deck", "system": "loft_floor", "x": 0, "y": 0, "z": 2440, "dx": 3000, "dy": 5000, "dz": 18 }, $joists ],
              "contacts": [], "cuts": [], "unmeasured": [] }""",
        )
        val labels = sectionDrawing(geometry)!!.shapes.ids("size:thickness:")
        assertEquals(listOf("size:thickness:deck", "size:thickness:east", "size:thickness:joist3", "size:thickness:west"), labels.map { it["id"] as String }.sorted())
        assertTrue((labels.first { (it["id"] as String).endsWith("deck") }["dy_dp"] as Int) < 0)
        assertTrue((labels.first { (it["id"] as String).endsWith("joist3") }["text"] as String).contains("7 alike"))
    }

    @Test
    fun the_section_draws_a_line_between_layers_and_a_label_for_each() {
        val shapes = sectionDrawing(geometryWith(SIP))!!.shapes
        assertEquals(2, shapes.ids("layer:west/boundary").size)
        assertEquals(3, shapes.ids("size:layer:west/").size)
        assertTrue(shapes.ids("size:thickness:west").isEmpty())
    }

    @Test
    fun the_first_layer_is_the_outermost_on_each_side_of_the_building() {
        val shapes = sectionDrawing(geometryWith(SIP))!!.shapes
        fun boundaryX(piece: String) = shapes.ids("layer:$piece/boundary").map { (it["x_mm"] as Double) }
        assertEquals(listOf(11.0, 103.0), boundaryX("west"))
        assertEquals(listOf(2886.0 + 114 - 11, 2886.0 + 114 - 103), boundaryX("east"))
    }

    @Test
    fun a_layer_list_that_is_not_a_list_is_refused() {
        assertFailsWith<GeometryFormatException> { geometryWith(""", "layers": "OSB" """) }
    }
}
