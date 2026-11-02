package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun recorded() = checkNotNull(BoardTest::class.java.getResource("/crew-simulation.json")).readText()

private fun JsonObject.nodes(): List<JsonObject> = listOf(this) + this["children"]!!.jsonArray.flatMap { it.jsonObject.nodes() }

private fun JsonObject.texts() = nodes().filter { it["type"]!!.jsonPrimitive.content == "text" }.map { it["props"]!!.jsonObject["value"]!!.jsonPrimitive.content }

class BoardTest {
    private val board = readBoard(recorded())

    @Test
    fun a_flow_that_ran_has_when_it_ran_who_was_assigned_and_what_it_placed() {
        val frame = board.cards.first { it.name == "frame-walls" }
        assertEquals(2, frame.runs)
        assertEquals(1.0 to 3.0, frame.firstRunAt!! to frame.lastRunAt!!)
        assertEquals(listOf("ana", "ben"), frame.people)
        assertEquals(listOf("ben"), frame.learners)
        assertEquals(listOf("wall@0,0,350", "wall@2438,0,350"), frame.places)
    }

    @Test
    fun the_card_says_it_in_words_with_the_clocks_unit() {
        val words = boardNode(Boarded(board)).texts()
        assertTrue("ran 2 times, hour 1 to 3" in words, words.toString())
        assertTrue(words.any { it == "people: ana, ben; learning: ben" })
    }

    @Test
    fun flows_that_never_ran_are_grouped_as_having_no_hours_yet() {
        val idle = Board("hour", "quiescent", listOf(FlowCard("paint", 0, null, null, emptyList(), emptyList(), emptyList(), emptyList(), null, emptyMap())) + board.cards)
        val words = boardNode(Boarded(idle)).texts()
        assertTrue(words.indexOf("No hours yet") > words.indexOf("Ran"))
        assertEquals("paint", words[words.indexOf("No hours yet") + 1])
    }

    @Test
    fun needs_are_shown_when_the_simulation_carries_them_and_left_out_when_it_does_not() {
        val withNeeds = readBoard(recorded().replaceFirst("{", """{"needs":{"frame-walls":{"carpentry":2,"hours":3}},"""))
        assertEquals(mapOf("carpentry" to 2.0, "hours" to 3.0), withNeeds.cards.first { it.name == "frame-walls" }.needs)
        assertTrue(boardNode(Boarded(withNeeds)).texts().any { it == "needs: carpentry 2, hours 3" })
        assertTrue(boardNode(Boarded(board)).texts().none { it.startsWith("needs:") })
    }

    @Test
    fun a_refusal_from_the_engine_is_read_as_its_message() {
        assertEquals("no clock", assertFailsWith<BoardFormatException> { readBoard("""{"refused": {"rule": "simulation-no-clock", "message": "no clock"}}""") }.message)
    }
}
