package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val SYSTEM_FILE = """{ "id": "pier", "title": "A pier", "systems": { "pier": { "solution": "post-a", "settings": {} } } }"""

private val EXPANSION = """{ "extent_mm": [1000, 1000], "objects": [], "instances": [], "supplied": {} }"""

private val RANKING = """{"system": "pier", "kind": "pier", "weights": {"first_cost": 1.0, "life": 1.0}, "ranked": [{"rank": 1, "solution": "post-b", "title": "Post post-b", "chosen": false, "score_low": 0.5, "score_high": 1.0, "settled": true, "missing": ["life"], "figures": {"first_cost": {"value": 100.0, "unit": "usd", "good": "low", "score": 1.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}, "life": {"value": null, "unit": "years", "good": "high", "score": null, "missing": true, "origin": "spec", "source": null, "evidence": null}}}, {"rank": 2, "solution": "post-a", "title": "Post post-a", "chosen": true, "score_low": 0.5, "score_high": 0.5, "settled": false, "missing": [], "figures": {"first_cost": {"value": 300.0, "unit": "usd", "good": "low", "score": 0.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}, "life": {"value": 30.0, "unit": "years", "good": "high", "score": 1.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}}}, {"rank": 3, "solution": "post-c", "title": "Post post-c", "chosen": false, "score_low": 0.25, "score_high": 0.25, "settled": true, "missing": [], "figures": {"first_cost": {"value": 200.0, "unit": "usd", "good": "low", "score": 0.5, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}, "life": {"value": 10.0, "unit": "years", "good": "high", "score": 0.0, "missing": false, "origin": "spec", "source": "rank.fixture", "evidence": "published"}}}]}"""

private fun page(ranked: Ranked): JsonObject = buildPageSpec(readSystemFile(SYSTEM_FILE), readExpansion(EXPANSION), ranked = ranked)

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String): JsonObject = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.texts(): List<String> = nodes().mapNotNull { it["props"]?.jsonObject?.get("value")?.takeIf { value -> value is kotlinx.serialization.json.JsonPrimitive }?.jsonPrimitive?.content }

private fun ranked() = Ranked(readRanking(RANKING))

class RankingTest {
    @Test
    fun the_candidates_are_read_in_the_engines_order_with_their_bounds_and_gaps() {
        val ranking = readRanking(RANKING)
        assertEquals(listOf("post-b", "post-a", "post-c"), ranking.candidates.map { it.solution })
        val leader = ranking.candidates.first()
        assertEquals(0.5 to 1.0, leader.scoreLow to leader.scoreHigh)
        assertEquals(listOf("life"), leader.missing)
        assertNull(leader.figures.first { it.name == "life" }.value)
        assertEquals(mapOf("first_cost" to 1.0, "life" to 1.0), ranking.weights)
    }

    @Test
    fun a_card_per_candidate_explains_the_rank_from_its_figures() {
        val root = page(ranked())["root"]!!.jsonObject
        val texts = root.byId("rank.pier.post-a.card").texts()
        assertTrue("#2 Post post-a" in texts)
        assertTrue("30 years" in texts)
        assertTrue("higher is better; score 1 × weight 1; rank.fixture, published" in texts)
        assertNotNull(root.byId("rank.pier.post-c.card"))
    }

    @Test
    fun a_missing_figure_is_a_gap_and_not_a_zero() {
        val card = page(ranked())["root"]!!.jsonObject.byId("rank.pier.post-b.card")
        assertEquals("– no figure", card.byId("rank.pier.post-b.life.value")["props"]!!.jsonObject["missing"]!!.jsonPrimitive.content)
        assertTrue(card.texts().any { it.startsWith("counts as the worst figure for the low score") })
        assertTrue(card.texts().any { it == "score 0.5 to 1 (0 worst, 1 best)" })
    }

    @Test
    fun an_unsettled_rank_is_marked_and_says_why() {
        val root = page(ranked())["root"]!!.jsonObject
        assertEquals("rank not settled", root.byId("rank.pier.post-a.unsettled")["props"]!!.jsonObject["label"]!!.jsonPrimitive.content)
        assertTrue(root.byId("rank.pier.post-a.card").texts().any { "another candidate has a missing figure" in it })
        assertTrue(root.nodes().none { it["id"]!!.jsonPrimitive.content == "rank.pier.post-c.unsettled" })
        assertEquals("chosen", root.byId("rank.pier.post-a.chosen")["props"]!!.jsonObject["label"]!!.jsonPrimitive.content)
    }

    @Test
    fun a_ranking_that_could_not_be_run_is_said_on_screen_and_a_refusal_is_read_as_its_message() {
        val root = page(Ranked(null, failure = "no weights"))["root"]!!.jsonObject
        assertEquals("The ranking could not be run: no weights", root.byId("ranking.failure")["props"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        val refusal = assertFailsWith<RankingFormatException> { readRanking("""{"refused": {"rule": "ranking-weights-missing", "message": "no weights"}}""") }
        assertEquals("no weights", refusal.message)
    }
}
