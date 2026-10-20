package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun recorded(name: String) = checkNotNull(GlazingPlacementTest::class.java.getResource("/$name")).readText()

private val GEOMETRY = readGeometry(recorded("live-geometry.json"))

private val SYSTEM_FILE = readSystemFile("""{ "id": "house", "title": "House", "systems": { "walls": { "settings": {} }, "glazing": { "settings": {} }, "doors": { "settings": {} }, "floor": { "settings": {} } } }""")

private class Span(val low: List<Double>, val high: List<Double>)

private fun GeoPiece.extent() = Span(listOf(xMm, yMm, zMm), listOf(xMm + dxMm, yMm + dyMm, zMm + dzMm))

private fun Span.contains(inner: Span) = low.indices.all { low[it] <= inner.low[it] + 0.5 && inner.high[it] <= high[it] + 0.5 }

private fun JsonObject.number(key: String) = this[key]!!.jsonPrimitive.content.toDouble()

private fun shadedBoxes(): List<JsonObject> {
    val volume = buildLensPageSpec(SYSTEM_FILE, readExpansion(recorded("live-expansion.json")), geometried = Geometried(GEOMETRY))["root"]!!.jsonObject
    fun find(node: JsonObject): JsonObject? = if (node["id"]!!.jsonPrimitive.content == "volume") node else node["children"]!!.jsonArray.firstNotNullOfOrNull { find(it.jsonObject) }
    return find(volume)!!["props"]!!.jsonObject["boxes"]!!.jsonArray.map { it.jsonObject }
}

private fun JsonObject.extent() = Span(
    listOf(number("x_mm") - number("width_mm") / 2, number("y_mm") - number("depth_mm") / 2, number("z_mm")),
    listOf(number("x_mm") + number("width_mm") / 2, number("y_mm") + number("depth_mm") / 2, number("z_mm") + number("height_mm")),
)

private fun union(spans: List<Span>) = Span(spans.map { it.low }.reduce { a, b -> a.indices.map { i -> minOf(a[i], b[i]) } }, spans.map { it.high }.reduce { a, b -> a.indices.map { i -> maxOf(a[i], b[i]) } })

class GlazingPlacementTest {
    @Test
    fun every_opening_the_engine_cut_lies_inside_the_boxes_of_the_hosts_it_is_cut_from_in_the_geometry() {
        val pieces = GEOMETRY.pieces.associateBy { it.id }
        assertTrue(GEOMETRY.cuts.isNotEmpty())
        GEOMETRY.cuts.groupBy { it.opening }.forEach { (opening, cuts) ->
            val hosts = union(cuts.map { pieces.getValue(it.host).extent() })
            assertTrue(hosts.contains(pieces.getValue(opening).extent()), "$opening is not inside its hosts")
        }
    }

    @Test
    fun the_shaded_world_draws_each_measured_piece_where_the_geometry_has_it() {
        val boxes = shadedBoxes().associateBy { it["id"]!!.jsonPrimitive.content }
        GEOMETRY.pieces.filter { piece -> GEOMETRY.cuts.none { it.host == piece.id } }.forEach { piece ->
            val extent = boxes.getValue(piece.id).extent()
            assertEquals(piece.extent().low, extent.low, piece.id)
            assertEquals(piece.extent().high, extent.high, piece.id)
        }
    }

    @Test
    fun the_glazing_in_the_shaded_world_is_inside_the_walls_it_is_cut_from() {
        val boxes = shadedBoxes()
        val cutHosts = GEOMETRY.cuts.filter { it.opening.startsWith("glazing") }.groupBy { it.opening }
        assertTrue(cutHosts.isNotEmpty())
        cutHosts.forEach { (opening, cuts) ->
            val hosts = union(cuts.map { cut -> GEOMETRY.pieces.first { it.id == cut.host }.extent() })
            val pane = boxes.first { it["id"]!!.jsonPrimitive.content == opening }.extent()
            assertTrue(hosts.contains(pane), "$opening sticks out of its walls")
        }
    }

    @Test
    fun a_world_with_embedded_pieces_has_a_grade_plane_and_its_posts_stand_below_it() {
        val volume = buildLensPageSpec(SYSTEM_FILE, readExpansion(recorded("live-expansion.json")), geometried = Geometried(GEOMETRY))["root"]!!.jsonObject
        fun find(node: JsonObject): JsonObject? = if (node["id"]!!.jsonPrimitive.content == "volume") node else node["children"]!!.jsonArray.firstNotNullOfOrNull { find(it.jsonObject) }
        val props = find(volume)!!["props"]!!.jsonObject
        assertEquals("0.0", props["grade_mm"]!!.jsonPrimitive.content)
        val post = props["boxes"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content.startsWith("post@") }
        assertEquals(-1219.0, post.number("z_mm"))
    }
}
