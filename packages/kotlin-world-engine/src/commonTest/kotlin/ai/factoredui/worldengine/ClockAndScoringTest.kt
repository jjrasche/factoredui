package ai.factoredui.worldengine

import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.outputs.reportOutputs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClockAndScoringTest {
    private fun tick(log: EventLog, steps: Int) = log.attempt("main", "clock", "tick", buildJsonObject { put("n", steps) }, STAMP)

    private fun assertNear(expected: Double, measured: Double?) {
        val actual = assertNotNull(measured, "expected $expected, found not-measured")
        assertTrue(abs(expected - actual) <= 1e-9 * maxOf(1.0, abs(expected)), "expected $expected, found $actual")
    }

    @Test
    fun each_tick_evaluates_every_stock_once_against_the_previous_values() {
        val log = EventLog(parcelWorld())
        log.place("paddock", 9, 12)
        tick(log, 10)
        val perDay = 625 / 43560.0 * 4.0 / 365.0
        assertNear(10 * perDay, reportOutputs(log.world, log.stateOf()).stocks.getValue("standing_forage"))
        assertEquals(10L, log.stateOf().ticks)
    }

    @Test
    fun a_tick_outside_one_to_one_hundred_thousand_is_refused() {
        val log = EventLog(parcelWorld())
        assertEquals("tick-bound", tick(log, 0).refusedRule())
        assertEquals("tick-bound", tick(log, 100_001).refusedRule())
        assertEquals(0L, log.stateOf().ticks)
    }

    @Test
    fun now_is_ticks_times_the_tick_length_in_hours() {
        val log = EventLog(dungeonWorld())
        tick(log, 3)
        val state = log.stateOf()
        val now = ai.factoredui.worldengine.expression.Evaluation(log.world, state).numberOf(ai.factoredui.worldengine.expression.parseExpression("now"))
        assertNear(3 * 6.0 / 3600.0, now)
    }

    @Test
    fun scores_are_reported_in_their_declared_units() {
        val log = EventLog(parcelWorld())
        (9 until 12).forEach { log.place("paddock", it, 12) }
        val outputs = reportOutputs(log.world, log.stateOf())
        assertNear(3 * 625.0, outputs.scoring.getValue("area_paddock"))
        assertNear(3 * 625 / 43560.0 * 4.0, outputs.scoring.getValue("pasture_yield_annual"))
        assertEquals(mapOf("paddock" to 1875.0), outputs.areas.filterValues { it > 0 })
    }

    @Test
    fun projected_support_weighs_each_neighbour_by_distance_and_counts_only_approval() {
        val log = EventLog(parcelWorld())
        (9 until 12).forEach { log.place("paddock", it, 12) }
        val weights = listOf(600.0, 1500.0, 3000.0).map { 1 / (1 + it / 1320) }
        val expected = (weights[0] + weights[2]) / weights.sum()
        assertNear(expected, reportOutputs(log.world, log.stateOf()).scoring.getValue("neighbor_support"))
    }

    @Test
    fun an_opted_in_neighbour_is_no_longer_synthetic_and_may_endorse() {
        val log = EventLog(parcelWorld())
        val optIn = buildJsonObject {
            put("agent_id", "neighbor-3")
            putJsonObject("attributes") { put("wants_food", 0) }
        }
        log.attempt("main", "neighbor-3", "opt_in", optIn, STAMP)
        val agent = log.stateOf().agents.getValue("neighbor-3")
        assertEquals(false, agent.isSynthetic)
        assertEquals(0.0, agent.attributes.getValue("wants_food"))
        assertEquals(3000.0, agent.attributes.getValue("distance_ft"))
        val unknown = buildJsonObject {
            put("agent_id", "neighbor-3")
            putJsonObject("attributes") { put("height", 2) }
        }
        val refusal = log.attempt("main", "jim", "opt_in", unknown, STAMP)
        assertEquals("unknown-attribute", refusal.refusedRule())
    }
}
