package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }
private const val MAP_SIZE = 400

private const val RAMP = """{"stops":[[0,"#2E86DE"],[1,"#E67E22"]],"no_figure":"#FF00FF"}"""

private val SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "shade_by": "{map.shade}", "shade_out": "{map.out}", "shade_top": 5, "selected": "{map.picked}",
      "layers": [{"id":"parcels","kind":"fill","features":[
        {"id":"wide","values":{"a":1.0,"b":0.0},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]},
        {"id":"narrow","values":{"a":0.0,"b":1.0},"rings":[[[-85.20,42.80],[-85.00,42.80],[-85.00,43.10],[-85.20,43.10]]]}
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

private fun shade(weights: String, minimum: String? = null): Any? =
    json.parseToJsonElement("""{"weights":$weights,"ramp":$RAMP${minimum?.let { ""","minimum":$it""" }.orEmpty()}}""").toPlain()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.at(vararg path: String): Any? =
    path.fold(this as Any?) { node, key -> (node as? Map<String, Any?>)?.get(key) }

private fun rowIds(store: Map<String, Any?>): List<String> =
    ((store.at("map", "out", "top") as? List<*>) ?: emptyList<Any?>()).map { (it as Map<*, *>)["id"] as String }

class GeomapShadeOutTest {
    @Test
    fun the_top_parcels_by_the_live_score_are_written_best_first_and_follow_a_weight() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}"""))
            screen.frame()
            assertEquals(listOf("wide", "narrow"), rowIds(screen.store()))
            screen.type("map.shade", shade("""{"a":0,"b":1}"""))
            screen.frame()
            assertEquals(listOf("narrow", "wide"), rowIds(screen.store()))
        }
    }

    @Test
    fun a_row_carries_its_rank_its_score_and_the_bounds_to_frame_it() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}"""))
            screen.frame()
            val row = (screen.store().at("map", "out", "top") as List<*>).first() as Map<*, *>
            assertEquals(1.0, (row["rank"] as Number).toDouble())
            assertEquals(1.0, (row["score"] as Number).toDouble(), 1e-9)
            val bounds = (row["bounds"] as List<*>).map { (it as Number).toDouble() }
            listOf(-85.90, 42.80, -85.30, 43.10).zip(bounds).forEach { (expected, actual) -> assertEquals(expected, actual, 1e-6) }
        }
    }

    @Test
    fun a_minimum_keeps_the_hidden_parcels_out_of_the_list() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}""", minimum = "0.5"))
            screen.frame()
            assertEquals(listOf("wide"), rowIds(screen.store()))
        }
    }

    @Test
    fun a_tapped_parcel_is_described_part_by_part_and_the_parts_sum_to_its_score() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":3,"b":1}"""))
            screen.frame()
            screen.tapAt(100f, 200f)
            screen.frame()
            val selected = screen.store().at("map", "out", "selected") as Map<*, *>
            assertEquals("wide", selected["id"])
            val parts = (selected["parts"] as List<*>).map { it as Map<*, *> }
            assertEquals(listOf("a", "b"), parts.map { it["field"] })
            assertEquals(0.75, parts.sumOf { (it["contribution"] as Number).toDouble() }, 1e-9)
        }
    }

    @Test
    fun what_a_feature_says_about_itself_in_info_comes_out_with_its_row_and_its_card() {
        val named = json.decodeFromString(
            Spec.serializer(),
            """{"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
              "shade_by": "{map.shade}", "shade_out": "{map.out}", "selected": "{map.picked}",
              "layers": [{"id":"parcels","kind":"fill","features":[
                {"id":"wide","values":{"a":1.0},"info":{"address":"6625 PRATT LAKE AVE SE","acres":9.5,"skipped":null},
                 "rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]}]}]}}}""",
        )
        driveScreen(named, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1}"""))
            screen.frame()
            screen.tapAt(200f, 200f)
            screen.frame()
            val row = (screen.store().at("map", "out", "top") as List<*>).first() as Map<*, *>
            assertEquals(mapOf("address" to "6625 PRATT LAKE AVE SE", "acres" to 9.5), row["info"])
            assertEquals("6625 PRATT LAKE AVE SE", screen.store().at("map", "out", "selected", "info", "address"))
        }
    }

    @Test
    fun a_parts_share_of_the_score_is_a_percent_with_one_decimal_so_a_small_one_does_not_read_zero() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0.004}"""))
            screen.frame()
            screen.tapAt(100f, 200f)
            screen.frame()
            val parts = (screen.store().at("map", "out", "selected", "parts") as List<*>).map { it as Map<*, *> }
            assertEquals(listOf("100.0%", "0.0%"), parts.map { it["share_text"] })
        }
        val small = json.decodeFromString(
            Spec.serializer(),
            """{"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
              "shade_by": "{map.shade}", "shade_out": "{map.out}", "selected": "{map.picked}",
              "layers": [{"id":"parcels","kind":"fill","features":[
                {"id":"wide","values":{"a":0.5,"b":0.5},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]}]}]}}}""",
        )
        driveScreen(small, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0.004}"""))
            screen.frame()
            screen.tapAt(200f, 200f)
            screen.frame()
            val parts = (screen.store().at("map", "out", "selected", "parts") as List<*>).map { it as Map<*, *> }
            assertEquals(listOf("99.6%", "0.4%"), parts.map { it["share_text"] })
        }
    }

    @Test
    fun with_nothing_tapped_nothing_is_selected() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1,"b":0}"""))
            screen.frame()
            assertNull(screen.store().at("map", "out", "selected"))
            assertNotNull(screen.store().at("map", "out", "top"))
        }
    }

    @Test
    fun the_list_stops_at_the_top_count_the_node_names() {
        val many = (0 until 12).joinToString(",") { index ->
            """{"id":"p$index","values":{"a":${index / 12.0}},"rings":[[[-85.${index}0,42.80],[-85.${index}5,42.80],[-85.${index}5,42.85]]]}"""
        }
        val spec = json.decodeFromString(
            Spec.serializer(),
            """{"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
              "shade_by": "{map.shade}", "shade_out": "{map.out}", "shade_top": 5,
              "layers": [{"id":"parcels","kind":"fill","features":[$many]}]}}}""",
        )
        driveScreen(spec, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade("""{"a":1}"""))
            screen.frame()
            assertTrue(rowIds(screen.store()).size == 5, "the list should stop at 5, got ${rowIds(screen.store())}")
        }
    }
}
