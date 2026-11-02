package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.score.FigureRange
import ai.factoredui.compose.score.ScoreShade
import ai.factoredui.compose.ramp.RampReading
import ai.factoredui.compose.ramp.readColourRamp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

private val json = Json { ignoreUnknownKeys = true }
private const val MAP_SIZE = 400

private const val RAMP = """{"stops":[[0,"#2E86DE"],[1,"#E67E22"]],"no_figure":"#FF00FF"}"""
private const val FIGURE_INFO =
    """{"acres":{"label":"Parcel acres","unit":"acres","decimals":1},"drive":{"label":"Drive to the campus","unit":"min","decimals":0}}"""

private val SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "shade_by": "{map.shade}", "shade_out": "{map.out}", "shade_top": 5, "selected": "{map.picked}",
      "layers": [{"id":"parcels","kind":"fill","features":[
        {"id":"big","values":{"a":1.0},"figures":{"acres":40.0,"drive":15.0},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]},
        {"id":"small","values":{"a":0.5},"figures":{"acres":2.0,"drive":10.0},"rings":[[[-85.20,42.80],[-85.00,42.80],[-85.00,43.10],[-85.20,43.10]]]},
        {"id":"far","values":{"a":0.2},"figures":{"acres":30.0,"drive":null},"rings":[[[-84.90,42.80],[-84.70,42.80],[-84.70,43.10],[-84.90,43.10]]]}
      ]}]
    }}}
    """.trimIndent(),
)

private fun JsonElement.toPlain(): Any? = when (this) {
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else content.toBooleanStrictOrNull() ?: content.toDouble()
}

private fun shade(ranges: String = "{}"): Any? =
    json.parseToJsonElement(
        """{"weights":{"a":1},"ramp":$RAMP,"ranges":$ranges,"figure_info":$FIGURE_INFO,"field_figures":{"a":"acres"},"labels":{"a":"Score field"}}""",
    ).toPlain()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.at(vararg path: String): Any? =
    path.fold(this as Any?) { node, key -> (node as? Map<String, Any?>)?.get(key) }

private fun rowIds(store: Map<String, Any?>): List<String> =
    ((store.at("map", "out", "top") as? List<*>) ?: emptyList<Any?>()).map { (it as Map<*, *>)["id"] as String }

class GeomapRangeFilterTest {
    @Test
    fun a_minimum_on_a_figure_keeps_the_things_under_it_out_of_the_list() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"acres":{"min":null,"max":null}}"""))
            screen.frame()
            assertEquals(listOf("big", "small", "far"), rowIds(screen.store()))
            screen.type("map.shade", shade("""{"acres":{"min":10.0,"max":null}}"""))
            screen.frame()
            assertEquals(listOf("big", "far"), rowIds(screen.store()))
        }
    }

    @Test
    fun a_maximum_on_a_figure_drops_the_things_over_it_and_the_things_with_no_number() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"drive":{"min":null,"max":12.0}}"""))
            screen.frame()
            assertEquals(listOf("small"), rowIds(screen.store()))
        }
    }

    @Test
    fun the_things_a_range_cannot_judge_are_counted_in_words() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"drive":{"min":null,"max":30.0}}"""))
            screen.frame()
            assertEquals("1 have no Drive to the campus", screen.store().at("map", "out", "unmeasured_text"))
            screen.type("map.shade", shade("""{"drive":{"min":null,"max":null}}"""))
            screen.frame()
            assertEquals("", screen.store().at("map", "out", "unmeasured_text"))
        }
    }

    @Test
    fun the_tapped_thing_card_gives_every_figure_in_its_units_and_each_weighted_field_beside_its_own_figure() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade())
            screen.type("map.picked", listOf("big"))
            screen.frame()
            val selected = assertNotNull(screen.store().at("map", "out", "selected")) as Map<*, *>
            val figures = (selected["figures"] as List<*>).map { (it as Map<*, *>)["text"] }
            assertEquals(listOf("40.0 acres", "15 min"), figures)
            val part = (selected["parts"] as List<*>).first() as Map<*, *>
            assertEquals("40.0 acres", part["figure_text"])
        }
    }

    @Test
    fun a_feature_outside_a_range_draws_in_the_dim_colour_and_one_inside_keeps_its_ramp_colour() {
        val ramp = (readColourRamp("ramp", json.parseToJsonElement(RAMP).toPlain()) as RampReading.Readable).ramp
        val record = ScoreShade(mapOf("a" to 1.0), null, ramp, ranges = mapOf("acres" to FigureRange(10.0, null)))
        val dim = 0x11223344
        assertEquals(dim, featureFillArgb(featureWith(mapOf("acres" to 2.0)), record, dim))
        val insideFill = assertNotNull(featureFillArgb(featureWith(mapOf("acres" to 40.0)), record, dim))
        assertNotEquals(dim, insideFill)
    }

    private fun featureWith(figures: Map<String, Double?>) = TessellatedFeature(
        featureId = "x", fillArgb = null, strokeArgb = null, strokeWidth = 1f, worldRings = emptyList(),
        triangleWorld = DoubleArray(0), bounds = null, pattern = null, label = null, dash = null, labelCandidates = emptyList(),
        values = mapOf("a" to 0.9), figures = figures,
    )
}
