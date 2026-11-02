package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun recorded() = checkNotNull(OddsTest::class.java.getResource("/crew-sample.json")).readText()

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.byId(id: String) = nodes().first { it["id"]!!.jsonPrimitive.content == id }

private fun JsonObject.texts() = nodes().filter { it["type"]!!.jsonPrimitive.content == "text" }.map { it["props"]!!.jsonObject["value"]!!.jsonPrimitive.content }

private fun withThresholds(): Sampling = readSampling(
    recorded().replaceFirst(
        "{",
        """{"below":{"simulation.objective_total.labour_hours":{"value":10,"fraction":0.6,"n_with_value":200},"simulation.objective_total.finish_time":{"value":1.2,"fraction":0.8,"n_with_value":200}},"joint":{"paths":["simulation.objective_total.labour_hours","simulation.objective_total.finish_time"],"fraction":0.5},""",
    ),
)

private fun withQuality(): Sampling = readSampling(
    recorded().replaceFirst("{", """{"quality":{"assumed":{"inputs":9,"draws":4500},"inferred":{"inputs":5,"draws":2500},"published":{"inputs":15,"draws":7500}},"""),
)

class OddsTest {
    @Test
    fun the_quality_of_the_inputs_is_read_and_said_as_a_count_of_each() {
        val sampling = withQuality()
        assertEquals(mapOf("assumed" to 9, "inferred" to 5, "published" to 15), sampling.quality)
        assertTrue("inputs: 15 published, 5 inferred, 9 assumed" in oddsNode(Sampled(sampling)).texts())
    }

    @Test
    fun the_outputs_with_a_threshold_are_drawn_before_the_others() {
        val ids = oddsNode(Sampled(withThresholds())).nodes().map { it["id"]!!.jsonPrimitive.content }.filter { it.endsWith(".strip") }
        assertEquals(setOf("odds.simulation.objective_total.labour_hours.strip", "odds.simulation.objective_total.finish_time.strip"), ids.take(2).toSet())
    }

    @Test
    fun an_output_is_read_with_its_percentiles_and_its_range() {
        val spread = readSampling(recorded()).spreads.getValue("simulation.objective_total.labour_hours")
        assertEquals(listOf(8.0, 8.0, 10.0, 12.0, 12.0), listOf(spread.min, spread.p5, spread.p50, spread.p95, spread.max))
    }

    @Test
    fun a_strip_is_drawn_for_each_output_that_varied_and_not_for_one_that_did_not() {
        val node = oddsNode(Sampled(readSampling(recorded())))
        assertTrue(node.nodes().any { it["id"]!!.jsonPrimitive.content == "odds.simulation.objective_total.labour_hours" })
        assertTrue(node.nodes().none { it["id"]!!.jsonPrimitive.content == "odds.simulation.skills_final.ben.carpentry" })
    }

    @Test
    fun a_threshold_is_a_dashed_line_on_its_strip() {
        val strip = oddsNode(Sampled(withThresholds())).byId("odds.simulation.objective_total.labour_hours.strip")["props"]!!.jsonObject
        val marks = strip["shapes"]!!.jsonArray.map { it.jsonObject }.filter { it["style"]!!.jsonPrimitive.content == "threshold" }
        assertEquals(1, marks.size)
        assertTrue(strip["styles"]!!.jsonObject.getValue("threshold").jsonObject["dash"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun the_joint_fraction_is_one_sentence_naming_every_threshold() {
        assertEquals(listOf("50% of runs have labour hours at most 10 and finish time at most 1.2."), oddsSentences(withThresholds()))
    }

    @Test
    fun a_refusal_is_read_as_its_message_and_a_failed_sample_is_said_on_screen() {
        assertEquals("nothing varies", assertFailsWith<OddsFormatException> { readSampling("""{"refused": {"rule": "sample-nothing-varies", "message": "nothing varies"}}""") }.message)
        assertTrue(oddsNode(Sampled(null, "nothing varies")).texts().any { it.contains("could not be run: nothing varies") })
    }
}
