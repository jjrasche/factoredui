package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.resolveGeomapLayers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun plain(element: JsonElement): Any? = when (element) {
    is JsonArray -> element.map(::plain)
    is JsonObject -> element.mapValues { plain(it.value) }
    is JsonPrimitive -> if (element.isString) element.content else element.content.toDoubleOrNull()
}

private const val LABEL_WIDTH_PX = 90.0
private const val LABEL_HEIGHT_PX = 16.0

class MichiganLabelPlacementTest {

    private val fixture = Json.parseToJsonElement(File("src/desktopTest/resources/geomap/michigan_label_counties.json").readText()).jsonObject

    private val counties = tessellateGeomapLayers(
        resolveGeomapLayers(listOf(mapOf("id" to "either", "kind" to "fill", "features" to plain(fixture["features"]!!)))),
    ).layers.single().features

    private val scale = geomapScalePx(fixture["viewport"]!!.jsonObject["zoom"]!!.jsonPrimitive.content.toFloat())

    private fun placed(feature: TessellatedFeature) = placeGeomapLabel(
        feature.labelCandidates,
        feature.worldRings,
        halfWidthWorld = LABEL_WIDTH_PX / 2 / scale,
        halfHeightWorld = LABEL_HEIGHT_PX / 2 / scale,
    )

    @Test
    fun the_fixture_is_the_real_counties_that_straddled_their_borders() {
        assertEquals(7, counties.size, "fixture should carry the seven real Michigan counties it was cut from")
    }

    @Test
    fun every_placed_label_box_sits_wholly_on_its_own_county() {
        val strays = counties.mapNotNull { county ->
            val anchor = placed(county) ?: return@mapNotNull null
            val halfWidth = LABEL_WIDTH_PX / 2 / scale
            val halfHeight = LABEL_HEIGHT_PX / 2 / scale
            // A 7x7 grid over the box — denser than the 3x3 the placement itself checks, so this
            // measures something the code under test did not already assert.
            val steps = (0..6).map { -1.0 + it / 3.0 }
            val outside = steps.sumOf { dx ->
                steps.count { dy -> !isPointInRings(anchor.x + dx * halfWidth, anchor.y + dy * halfHeight, county.worldRings) }
            }
            if (outside > 0) "${county.label}: $outside of 49 box points off its own land" else null
        }
        assertEquals(emptyList(), strays)
    }

    @Test
    fun wherever_a_label_fits_somewhere_in_a_county_the_placement_finds_it() {
        val halfWidth = LABEL_WIDTH_PX / 2 / scale
        val halfHeight = LABEL_HEIGHT_PX / 2 / scale
        val missed = counties.mapNotNull { county ->
            val bounds = county.bounds ?: return@mapNotNull null
            val roomExists = (0..60).any { column ->
                (0..60).any { row ->
                    val x = bounds.minX + (bounds.maxX - bounds.minX) * column / 60
                    val y = bounds.minY + (bounds.maxY - bounds.minY) * row / 60
                    listOf(-1.0, 0.0, 1.0).all { dx ->
                        listOf(-1.0, 0.0, 1.0).all { dy -> isPointInRings(x + dx * halfWidth, y + dy * halfHeight, county.worldRings) }
                    }
                }
            }
            if (roomExists && placed(county) == null) county.label else null
        }
        assertEquals(emptyList(), missed, "a brute-force search found room for these labels and the placement did not")
    }

    @Test
    fun the_big_counties_still_get_labels() {
        val labelled = counties.filter { placed(it) != null }.mapNotNull { it.label }
        assertTrue(
            listOf("Marquette County", "Chippewa County").all { it in labelled },
            "the largest counties lost their labels entirely: labelled were $labelled",
        )
    }
}
