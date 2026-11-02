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
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }
private const val MAP_SIZE = 400

private const val RAMP = """{"stops":[[0,"#2E86DE"],[1,"#E67E22"]],"no_figure":"#FF00FF"}"""
private const val FIGURE_INFO =
    """{"spring":{"label":"Spring likelihood","unit":"/100","decimals":0,"source":"terrain model","evidence":"spring.source.json"},
       "drive":{"label":"Drive to the campus","unit":"min","decimals":0,"source":"OSRM","evidence":"drive-times.json"}}"""
private const val SCALING = """{"spring":{"good":"high"},"drive":{"good":"low"}}"""

private val SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {"spec_version":1,"renderer_min":1,"root":{"id":"map","type":"geomap","props":{
      "shade_by": "{map.shade}", "shade_out": "{map.out}", "shade_top": 5, "selected": "{map.picked}",
      "layers": [{"id":"parcels","kind":"fill","features":[
        {"id":"best","figures":{"spring":90.0,"drive":5.0},"rings":[[[-85.90,42.80],[-85.30,42.80],[-85.30,43.10],[-85.90,43.10]]]},
        {"id":"unsure","figures":{"spring":90.0,"drive":null},"rings":[[[-85.20,42.80],[-85.00,42.80],[-85.00,43.10],[-85.20,43.10]]]},
        {"id":"worst","figures":{"spring":10.0,"drive":40.0},"rings":[[[-84.90,42.80],[-84.70,42.80],[-84.70,43.10],[-84.90,43.10]]]}
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

private fun shade(minimum: String = "null"): Any? =
    json.parseToJsonElement(
        """{"weights":{"spring":1,"drive":1},"figure_scaling":$SCALING,"minimum":$minimum,"ramp":$RAMP,"figure_info":$FIGURE_INFO}""",
    ).toPlain()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.at(vararg path: String): Any? =
    path.fold(this as Any?) { node, key -> (node as? Map<String, Any?>)?.get(key) }

private fun rows(store: Map<String, Any?>): List<Map<*, *>> =
    ((store.at("map", "out", "top") as? List<*>) ?: emptyList<Any?>()).map { it as Map<*, *> }

class GeomapRawScoreTest {
    @Test
    fun things_are_scored_from_their_raw_figures_and_one_with_a_missing_figure_is_listed_with_bounds() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade())
            screen.frame()
            val top = rows(screen.store())
            assertEquals(listOf("best", "unsure", "worst"), top.map { it["id"] })
            assertEquals(listOf(1.0, 2.0, 3.0), top.map { it["rank"] })
            assertEquals(listOf(true, false, true), top.map { it["settled"] })
            assertEquals(0.5, top[1]["score_low"])
            assertEquals(1.0, top[1]["score_high"])
        }
    }

    @Test
    fun a_minimum_leaves_a_thing_listed_while_its_best_case_could_still_reach_it() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade(minimum = "0.8"))
            screen.frame()
            assertEquals(listOf("best", "unsure"), rows(screen.store()).map { it["id"] })
        }
    }

    @Test
    fun the_selected_thing_names_each_figure_with_its_good_end_unit_source_evidence_and_what_is_missing() {
        driveScreen(SPEC, widthDp = MAP_SIZE, heightDp = MAP_SIZE).use { screen ->
            screen.type("map.shade", shade())
            screen.type("map.picked", listOf("unsure"))
            screen.frame()
            val selected = assertNotNull(screen.store().at("map", "out", "selected")) as Map<*, *>
            assertEquals(false, selected["settled"])
            assertEquals(2.0, selected["rank"])
            assertEquals(listOf("drive"), selected["missing"])
            val drive = (selected["parts"] as List<*>).map { it as Map<*, *> }.first { it["field"] == "drive" }
            assertEquals("low", drive["good"])
            assertEquals("min", drive["unit"])
            assertEquals("OSRM", drive["source"])
            assertEquals("drive-times.json", drive["evidence"])
            assertEquals("no figure", drive["value_text"])
            assertTrue((selected["parts"] as List<*>).map { (it as Map<*, *>)["field"] }.containsAll(listOf("spring", "drive")))
        }
    }
}
