package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun recorded() = checkNotNull(BuildStepsTest::class.java.getResource("/crew-simulation.json")).readText()

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.texts() = nodes().filter { it["type"]!!.jsonPrimitive.content == "text" }.map { it["props"]!!.jsonObject["value"]!!.jsonPrimitive.content }

class BuildStepsTest {
    private val build = readBuild(recorded())

    @Test
    fun the_moments_are_the_first_step_and_each_step_a_flow_ran() {
        assertEquals(listOf(0.0, 1.0, 3.0, 4.0), build.keyframes.map { it.t })
        assertEquals(emptyList(), build.keyframes.first().acts)
        assertEquals(listOf("frame-walls"), build.keyframes[1].acts.map { it.flow })
    }

    @Test
    fun a_moment_says_who_acted_who_was_learning_and_what_was_placed() {
        val act = build.keyframes[1].acts.single()
        assertEquals(listOf("ana", "ben"), act.people)
        assertEquals(listOf("ben"), act.learners)
        assertEquals(listOf("wall@0,0,350"), act.places)
    }

    @Test
    fun a_piece_is_staged_at_the_moment_it_was_placed() {
        assertEquals(mapOf("wall@0,0,350" to 1, "wall@2438,0,350" to 2, "window@800,0,900" to 3), build.stageOf)
    }

    @Test
    fun each_moment_has_a_card_shown_only_while_the_slider_is_on_it() {
        val node = buildNode(Built(build))
        val cards = node.nodes().filter { it["id"]!!.jsonPrimitive.content.matches(Regex("build\\.step\\.\\d+")) }
        assertEquals(listOf("{ui.step}=0", "{ui.step}=1", "{ui.step}=2", "{ui.step}=3"), cards.map { it["visible"]!!.jsonPrimitive.content })
        assertTrue(node.texts().any { it == "Step 2 of 4, hour 1" })
        assertTrue(node.texts().any { it == "  people: ana, ben; learning: ben" })
    }

    @Test
    fun the_slider_runs_over_the_moments_and_next_and_previous_step_it_within_them() {
        val node = buildNode(Built(build))
        val slider = node.nodes().first { it["id"]!!.jsonPrimitive.content == "build.slider" }["props"]!!.jsonObject
        assertEquals(listOf("0", "3"), listOf("min", "max").map { slider[it]!!.jsonPrimitive.content })
        val next = node.nodes().first { it["id"]!!.jsonPrimitive.content == "build.next" }["action"]!!.jsonObject["params"]!!.jsonObject
        assertEquals(listOf("ui.step", "1", "0", "3"), listOf("path", "add", "min", "max").map { next[it]!!.jsonPrimitive.content })
    }

    @Test
    fun a_refusal_is_read_as_its_message() {
        assertEquals("no clock", assertFailsWith<BuildFormatException> { readBuild("""{"refused": {"rule": "simulation-no-clock", "message": "no clock"}}""") }.message)
    }
}
