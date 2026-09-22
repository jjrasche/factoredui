package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.renderScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private const val LAND = 0x9FD8C8

private fun ring(vararg lonLat: Double): DoubleArray = DoubleArray(lonLat.size).also { flat ->
    for (index in lonLat.indices step 2) {
        flat[index] = lonToWorldX(lonLat[index])
        flat[index + 1] = latToWorldY(lonLat[index + 1])
    }
}

// Keweenaw's shape: a large mainland part and a small island far off, so the bounding box's
// centre is open water.
private val MAINLAND = ring(-88.60, 46.90, -88.00, 46.90, -88.00, 47.30, -88.60, 47.30)
private val FAR_ISLAND = ring(-89.20, 47.90, -89.00, 47.90, -89.00, 48.00, -89.20, 48.00)

// A C-shape: the bounding box centre is in the bay the shape wraps around.
private val C_SHAPE = ring(
    -86.00, 43.00, -85.00, 43.00, -85.00, 43.20, -85.80, 43.20,
    -85.80, 43.80, -85.00, 43.80, -85.00, 44.00, -86.00, 44.00,
)

private fun ringsJson(vararg rings: List<Pair<Double, Double>>) =
    rings.joinToString(",", "[", "]") { ring -> ring.joinToString(",", "[", "]") { (lon, lat) -> "[$lon,$lat]" } }

private val KEWEENAW_RINGS = ringsJson(
    listOf(-88.60 to 46.90, -88.00 to 46.90, -88.00 to 47.30, -88.60 to 47.30),
    listOf(-89.20 to 47.90, -89.00 to 47.90, -89.00 to 48.00, -89.20 to 48.00),
)

private fun countySpec(rings: String, label: String?) = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "layers":[{"id":"counties","kind":"fill","features":[
        {"id":"k","fill":"#9FD8C8","rings":$rings ${label?.let { ",\"label\":\"$it\"" } ?: ""}}
      ]}]
    }}}
    """.trimIndent(),
)

private fun isDark(rgb: Int): Boolean =
    ((rgb shr 16) and 0xFF) < 110 && ((rgb shr 8) and 0xFF) < 110 && (rgb and 0xFF) < 110

class GeomapLabelPlacementTest {

    @Test
    fun a_label_anchor_for_a_county_with_a_far_island_sits_on_the_mainland() {
        val anchor = assertNotNull(geomapLabelAnchorOf(listOf(MAINLAND, FAR_ISLAND)))
        assertTrue(isPointInRings(anchor.x, anchor.y, listOf(MAINLAND)), "the label anchor is not on the mainland")
    }

    @Test
    fun a_label_anchor_for_a_c_shape_is_on_land_not_in_its_bay() {
        val anchor = assertNotNull(geomapLabelAnchorOf(listOf(C_SHAPE)))
        assertTrue(isPointInRings(anchor.x, anchor.y, listOf(C_SHAPE)), "the label anchor fell in the bay the shape wraps")
    }

    @Test
    fun no_label_ink_ever_lands_outside_its_county() {
        val land = mutableSetOf<Pair<Int, Int>>()
        ImageIO.read(ByteArrayInputStream(renderScreen(countySpec(KEWEENAW_RINGS, null), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png)).let { image ->
            for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == LAND) land += x to y
        }
        val labelled = ImageIO.read(
            ByteArrayInputStream(renderScreen(countySpec(KEWEENAW_RINGS, "Keweenaw"), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png),
        )
        var ink = 0
        var inWater = 0
        for (y in 0 until labelled.height) for (x in 0 until labelled.width) {
            if (!isDark(labelled.getRGB(x, y) and 0xFFFFFF)) continue
            ink++
            val nearLand = (-3..3).any { dx -> (-3..3).any { dy -> (x + dx) to (y + dy) in land } }
            if (!nearLand) inWater++
        }
        assertTrue(ink > 20, "the label did not draw at all, so the check proves nothing")
        assertEquals(0, inWater, "$inWater label pixels were drawn in the water")
    }

    // A plain rectangle: every row is equally wide, so the first row scanned — the one hard
    // against the northern edge — used to win, and the label straddled the county above.
    private val WIDE_BASE_COUNTY = """[[[-86.00,43.00],[-85.00,43.00],[-85.00,43.10],[-86.00,43.10]]]"""
    private val COUNTY_BELOW = """[[[-86.00,43.10],[-85.00,43.10],[-85.00,43.40],[-86.00,43.40]]]"""

    private fun neighboursSpec(label: String?) = json.decodeFromString(
        Spec.serializer(),
        """
        {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
          "layers":[{"id":"counties","kind":"fill","features":[
            {"id":"below","fill":"#F5E6CC","rings":$COUNTY_BELOW},
            {"id":"labelled","fill":"#9FD8C8","rings":$WIDE_BASE_COUNTY ${label?.let { ",\"label\":\"$it\"" } ?: ""}}
          ]}]
        }}}
        """.trimIndent(),
    )

    @Test
    fun a_label_stays_inside_its_own_county_and_never_crosses_into_a_neighbour() {
        val own = mutableSetOf<Pair<Int, Int>>()
        ImageIO.read(ByteArrayInputStream(renderScreen(neighboursSpec(null), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png)).let { image ->
            for (y in 0 until image.height) for (x in 0 until image.width) if ((image.getRGB(x, y) and 0xFFFFFF) == LAND) own += x to y
        }
        val labelled = ImageIO.read(
            ByteArrayInputStream(renderScreen(neighboursSpec("Iron County"), emptyMap(), 400, 400, theme = SpecTheme.LIGHT).png),
        )
        var ink = 0
        var crossed = 0
        for (y in 0 until labelled.height) for (x in 0 until labelled.width) {
            if (!isDark(labelled.getRGB(x, y) and 0xFFFFFF)) continue
            ink++
            val insideOwn = (-2..2).any { dx -> (-2..2).any { dy -> (x + dx) to (y + dy) in own } }
            if (!insideOwn) crossed++
        }
        assertTrue(ink > 20, "the label did not draw at all, so the check proves nothing")
        assertEquals(0, crossed, "$crossed label pixels crossed into the neighbouring county")
    }
}
