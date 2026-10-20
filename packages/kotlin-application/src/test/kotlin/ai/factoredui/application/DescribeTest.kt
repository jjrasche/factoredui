package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val SYSTEM_FILE = """{ "id": "house", "title": "A house", "systems": { "pier": { "solution": "post-a", "settings": {} } } }"""

private val EXPANSION = """{ "extent_mm": [1000, 1000], "objects": [], "instances": [], "supplied": {} }"""

private val DESCRIBED = """
{ "domains": ["construction"], "solvers": [], "rules": [], "overlays": [],
  "systems": [
    { "id": "pier", "title": "Holds the floor up", "kind": "pier", "solution": "post-a", "solution_title": "Post post-a", "parts": ["footing"], "places": true,
      "settings": [
        { "id": "embedment_mm", "label": "embedment_mm", "unit": "mm", "type": "number", "min": null, "max": null, "choices": null, "value": 1219, "reason": null },
        { "id": "section_mm", "label": "section_mm", "unit": "mm", "type": "number", "min": null, "max": null, "choices": null, "value": null, "reason": "nobody drew a post size" } ],
      "alternatives": [
        { "id": "post-b", "title": "Post post-b", "places": true, "settings": [ { "id": "life", "label": "life", "unit": "years", "type": "number", "min": null, "max": null, "choices": null } ] } ],
      "solvers": ["gravity-load-path"], "rules": ["posts-under-floor"] } ] }
"""

private val RANKING = """{"system": "pier", "kind": "pier", "weights": {"first_cost": 1.0, "life": 1.0}, "ranked": [{"rank": 1, "solution": "post-b", "title": "Post post-b", "chosen": false, "score_low": 0.5, "score_high": 1.0, "settled": true, "missing": ["life"], "figures": {"first_cost": {"value": 100.0, "unit": "usd", "good": "low", "score": 1.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}, "life": {"value": null, "unit": "years", "good": "high", "score": null, "missing": true, "origin": "spec", "source": null, "evidence": null}}}, {"rank": 2, "solution": "post-a", "title": "Post post-a", "chosen": true, "score_low": 0.5, "score_high": 0.5, "settled": false, "missing": [], "figures": {"first_cost": {"value": 300.0, "unit": "usd", "good": "low", "score": 0.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}, "life": {"value": 30.0, "unit": "years", "good": "high", "score": 1.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}}}, {"rank": 3, "solution": "post-c", "title": "Post post-c", "chosen": false, "score_low": 0.25, "score_high": 0.25, "settled": true, "missing": [], "figures": {"first_cost": {"value": 200.0, "unit": "usd", "good": "low", "score": 0.5, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}, "life": {"value": 10.0, "unit": "years", "good": "high", "score": 0.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}}}]}"""

private fun page(describing: Describing): JsonObject = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), describing = describing)

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String): JsonObject = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.texts(): List<String> = nodes().mapNotNull { (it["props"]?.jsonObject?.get("value") as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }

private fun described(weighted: Boolean): Describing {
    val systems = readDescribed(DESCRIBED)
    return if (weighted) Describing(systems, mapOf("pier" to Ranked(readRanking(RANKING))), true) else Describing(systems)
}

class DescribeTest {
    @Test
    fun a_system_card_says_its_solution_parts_settings_with_values_and_reasons() {
        val card = page(described(true))["root"]!!.jsonObject.byId("describe.pier.card")
        assertTrue("Made of footing." in card.texts())
        assertTrue("1219 mm" in card.texts())
        assertEquals("– not given", card.byId("describe.pier.section_mm.value")["props"]!!.jsonObject["missing"]!!.jsonPrimitive.content)
        assertTrue("nobody drew a post size" in card.texts())
        assertEquals("Post post-a", card.byId("describe.pier.solution")["props"]!!.jsonObject["label"]!!.jsonPrimitive.content)
    }

    @Test
    fun an_alternative_opens_on_a_tap_and_shows_what_it_asks_for() {
        val root = page(described(true))["root"]!!.jsonObject
        val switch = root.byId("describe.pier.alt.post-b.switch")["props"]!!.jsonObject["value"]!!.jsonPrimitive.content
        val detail = root.byId("describe.pier.alt.post-b.detail")
        assertEquals(switch, detail["visible"]!!.jsonPrimitive.content)
        assertTrue(Regex("""\{[A-Za-z0-9_.]+}""").matches(switch))
        assertTrue("life (years)" in detail.texts())
    }

    @Test
    fun a_system_with_weights_lists_its_solutions_ranked_with_the_chosen_one_marked_and_gaps_shown() {
        val card = page(described(true))["root"]!!.jsonObject.byId("describe.pier.card")
        val order = card.texts().filter { it.startsWith("#") }
        assertEquals(listOf("#1 Post post-b", "#2 Post post-a", "#3 Post post-c"), order)
        assertEquals("chosen", card.byId("describe.pier.ranked.post-a.chosen")["props"]!!.jsonObject["label"]!!.jsonPrimitive.content)
        assertEquals("rank not settled", card.byId("describe.pier.ranked.post-a.unsettled")["props"]!!.jsonObject["label"]!!.jsonPrimitive.content)
        assertEquals("– no figure", card.byId("describe.pier.ranked.post-b.life.value")["props"]!!.jsonObject["missing"]!!.jsonPrimitive.content)
        assertTrue(card.texts().any { it.contains("rank.fixture") })
    }

    @Test
    fun without_ranking_weights_the_system_says_no_priorities_are_set() {
        val card = page(described(false))["root"]!!.jsonObject.byId("describe.pier.card")
        assertTrue(card.texts().any { it.startsWith("No priorities set") })
        assertTrue(card.texts().none { it.startsWith("#") })
    }

    @Test
    fun a_description_that_could_not_be_read_is_said_on_screen_and_the_old_cards_stay() {
        val root = page(Describing(emptyList(), failure = "no describe"))["root"]!!.jsonObject
        assertTrue("The systems could not be described: no describe" in root.byId("describe-failure").texts())
        assertTrue(root.nodes().any { it["id"]!!.jsonPrimitive.content == "cards-title" })
    }

    @Test
    fun a_refusal_from_the_engine_is_read_as_its_message() {
        assertEquals("no config", assertFailsWith<DescribeFormatException> { readDescribed("""{"refused": {"rule": "x", "message": "no config"}}""") }.message)
    }
}
