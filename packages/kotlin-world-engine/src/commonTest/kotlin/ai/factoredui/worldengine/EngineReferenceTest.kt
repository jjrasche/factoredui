package ai.factoredui.worldengine

import ai.factoredui.worldengine.expression.Evaluation
import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.MAX_DEPTH
import ai.factoredui.worldengine.expression.MAX_NODES
import ai.factoredui.worldengine.expression.Scope
import ai.factoredui.worldengine.expression.ScopeSite
import ai.factoredui.worldengine.expression.Value
import ai.factoredui.worldengine.expression.checkExpression
import ai.factoredui.worldengine.expression.parseExpression
import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.log.LogResult
import ai.factoredui.worldengine.outputs.TapDecision
import ai.factoredui.worldengine.outputs.actionForTap
import ai.factoredui.worldengine.outputs.renderProps
import ai.factoredui.worldengine.outputs.reportOutputs
import ai.factoredui.worldengine.world.WorldLoadException
import ai.factoredui.worldengine.world.WorldLoader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineReferenceTest {
    private fun assertNear(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) <= 1e-6 * maxOf(1.0, abs(expected)), "expected $expected, found $actual")
    }

    private fun countsOn(log: EventLog, branch: String = "main"): Map<String, Int> = reportOutputs(log.world, log.stateOf(branch)).counts

    @Test
    fun test_parcel_demo_reaches_the_expected_counts_and_scores() {
        val log = runDemo(parcelWorld())
        val outputs = reportOutputs(log.world, log.stateOf("main"))
        assertEquals(
            mapOf("paddock" to 12, "hoop_house" to 2, "commons_building" to 4, "van_pad" to 1, "path" to 6, "pond" to 2, "woodland_tree" to 4),
            outputs.counts,
        )
        assertNear(75.5, outputs.scoring.getValue("hoop_house_labor"))
        assertNear(12 * 625 / 43560.0 * 4.0, outputs.equations.getValue("pasture_yield"))
        assertNear(12 * 625 / 43560.0 * 4.0, outputs.stocks.getValue("standing_forage"))
        val support = outputs.scoring.getValue("neighbor_support")
        assertTrue(support > 0.0 && support < 1.0)
    }

    @Test
    fun test_dungeon_demo_reaches_the_expected_counts_and_scores() {
        val log = runDemo(dungeonWorld())
        val outputs = reportOutputs(log.world, log.stateOf("main"))
        assertEquals(mapOf("wall" to 10, "floor" to 7, "door" to 1, "monster" to 1, "treasure" to 2), outputs.counts)
        assertNear(2.0, outputs.scoring.getValue("treasure_per_monster"))
        assertNear(1.0, outputs.scoring.getValue("guarded_treasure"))
        assertEquals(10L, outputs.ticks)
    }

    @Test
    fun test_replaying_a_saved_log_rebuilds_the_identical_state() {
        val original = runDemo(parcelWorld())
        val reloaded = EventLog.load(parcelWorld(), original.dump())
        original.heads.keys.forEach { branch -> assertEquals(original.stateOf(branch).snapshot(), reloaded.stateOf(branch).snapshot()) }
        assertEquals(original.dump(), reloaded.dump())
    }

    @Test
    fun test_state_is_the_fold_of_the_chain_not_a_separate_store() {
        val log = runDemo(parcelWorld())
        assertEquals(log.stateOf("main").snapshot(), log.fold(log.heads.getValue("main")).snapshot())
    }

    @Test
    fun test_a_refused_action_leaves_the_log_unchanged() {
        val log = EventLog(parcelWorld())
        log.layPath(6 to 0)
        val before = log.dump().toString()
        assertEquals("van-pad-needs-path", log.place("van_pad", 10, 10).refusedRule())
        assertEquals(before, log.dump().toString())
    }

    @Test
    fun test_a_tampered_log_does_not_replay() {
        val document = runDemo(parcelWorld()).dump()
        val events = document.getValue("events").jsonArray.map { it.jsonObject }
        val tampered = events.map { event -> if (isVanPadPlacement(event)) movedTo(event, 10, 10) else event }
        val tamperedDocument = JsonObject(document + ("events" to kotlinx.serialization.json.JsonArray(tampered)))
        assertFailsWith<WorldLoadException> { EventLog.load(parcelWorld(), tamperedDocument) }
    }

    private fun isVanPadPlacement(event: JsonObject): Boolean =
        event.getValue("action").jsonPrimitive.content == "place" &&
            event.getValue("parameters").jsonObject["type"]?.jsonPrimitive?.content == "van_pad"

    private fun movedTo(event: JsonObject, col: Int, row: Int): JsonObject {
        val parameters = event.getValue("parameters").jsonObject
        return JsonObject(event + ("parameters" to JsonObject(parameters + mapOf("col" to JsonPrimitive(col), "row" to JsonPrimitive(row)))))
    }

    @Test
    fun test_a_branch_isolates_its_changes_from_main() {
        val log = EventLog(parcelWorld())
        log.layPath(6 to 0)
        log.branch("idea", "main", "jim", STAMP)
        log.place("pond", 3, 20, branch = "idea")
        assertEquals(1, countsOn(log, "idea").getValue("pond"))
        assertEquals(0, countsOn(log, "main").getValue("pond"))
    }

    @Test
    fun test_merge_applies_the_branch_events_to_the_target() {
        val log = EventLog(parcelWorld())
        log.branch("idea", "main", "jim", STAMP)
        log.place("pond", 3, 20, branch = "idea")
        log.place("path", 6, 0)
        assertIs<LogResult.Committed>(log.merge("idea", "main", "jim", STAMP))
        assertEquals(1, countsOn(log).getValue("pond"))
        assertEquals(1, countsOn(log).getValue("path"))
    }

    @Test
    fun test_merge_is_refused_when_both_branches_changed_the_same_tile() {
        val log = EventLog(parcelWorld())
        log.branch("idea", "main", "jim", STAMP)
        log.place("pond", 3, 20, branch = "idea")
        log.place("paddock", 3, 20)
        val refusal = assertIs<LogResult.Refused>(log.merge("idea", "main", "jim", STAMP)).refusal
        assertEquals("merge-conflict", refusal.rule)
        assertTrue("3,20" in refusal.message)
    }

    @Test
    fun test_merge_rechecks_rules_against_the_target_state() {
        val log = EventLog(parcelWorld())
        log.layPath(6 to 3)
        log.branch("idea", "main", "jim", STAMP)
        assertIs<LogResult.Committed>(log.place("van_pad", 7, 3, branch = "idea"))
        log.remove(6, 3)
        assertEquals("van-pad-needs-path", log.merge("idea", "main", "jim", STAMP).refusedRule())
        assertEquals(0, countsOn(log).getValue("van_pad"))
    }

    @Test
    fun test_revert_of_a_place_removes_what_it_placed() {
        val log = EventLog(parcelWorld())
        val placed = log.place("pond", 3, 20).committedId()
        assertIs<LogResult.Committed>(log.revert(placed, "main", "jim", STAMP))
        assertEquals(0, countsOn(log).getValue("pond"))
    }

    @Test
    fun test_revert_of_a_remove_puts_the_object_back() {
        val log = EventLog(parcelWorld())
        log.place("pond", 3, 20)
        val removed = log.remove(3, 20).committedId()
        log.revert(removed, "main", "jim", STAMP)
        assertEquals(1, countsOn(log).getValue("pond"))
    }

    @Test
    fun test_revert_is_refused_when_a_later_event_touched_the_same_tile() {
        val log = EventLog(parcelWorld())
        val placed = log.place("pond", 3, 20).committedId()
        log.remove(3, 20)
        log.place("paddock", 3, 20)
        assertEquals("revert-conflict", log.revert(placed, "main", "jim", STAMP).refusedRule())
    }

    @Test
    fun test_revert_is_checked_against_the_rules_like_any_action() {
        val log = EventLog(parcelWorld())
        log.layPath(6 to 3)
        val pathEvent = log.events.last().id
        log.place("van_pad", 7, 3)
        assertEquals("van-pad-needs-path", log.revert(pathEvent, "main", "jim", STAMP).refusedRule())
    }

    @Test
    fun test_an_always_rule_refuses_a_removal_that_would_strand_a_van_pad() {
        val log = EventLog(parcelWorld())
        log.layPath(6 to 3)
        log.place("van_pad", 7, 3)
        assertEquals("van-pad-needs-path", log.remove(6, 3).refusedRule())
    }

    private fun proposalLog(): EventLog {
        val log = EventLog(parcelWorld())
        log.branch("proposal", "main", "jim", STAMP, proposal = true)
        log.place("pond", 3, 20, branch = "proposal")
        return log
    }

    private fun endorsement(weightClass: String): JsonObject = buildJsonObject { put("weight_class", weightClass) }

    @Test
    fun test_a_proposal_does_not_merge_before_an_endorsement() {
        assertEquals("proposal-unendorsed", proposalLog().merge("proposal", "main", "jim", STAMP).refusedRule())
    }

    @Test
    fun test_a_synthetic_agent_cannot_endorse_because_its_vote_is_a_projection() {
        val refusal = proposalLog().attempt("proposal", "neighbor-2", "endorse", endorsement("nearby"), STAMP)
        assertEquals("projection-not-binding", refusal.refusedRule())
    }

    @Test
    fun test_an_opted_in_agent_endorses_and_the_proposal_merges() {
        val log = proposalLog()
        val optIn = buildJsonObject {
            put("agent_id", "neighbor-2")
            putJsonObject("attributes") { put("van_aversion", 0) }
        }
        log.attempt("main", "neighbor-2", "opt_in", optIn, STAMP)
        log.branch("proposal-2", "main", "jim", STAMP, proposal = true)
        log.place("pond", 4, 20, branch = "proposal-2")
        assertIs<LogResult.Committed>(log.attempt("proposal-2", "neighbor-2", "endorse", endorsement("nearby"), STAMP))
        assertIs<LogResult.Committed>(log.merge("proposal-2", "main", "jim", STAMP))
        assertEquals(1, countsOn(log).getValue("pond"))
    }

    @Test
    fun test_an_endorsement_needs_a_vote_class_from_the_matchmaking_vocabulary() {
        val refusal = proposalLog().attempt("proposal", "jim", "endorse", endorsement("landlord"), STAMP)
        assertEquals("unknown-weight-class", refusal.refusedRule())
    }

    @Test
    fun test_projected_support_falls_when_van_pads_appear_for_a_van_averse_neighbour() {
        val log = EventLog(parcelWorld())
        listOf(9 to 12, 10 to 12).forEach { (col, row) -> log.place("paddock", col, row) }
        val before = reportOutputs(log.world, log.stateOf()).scoring.getValue("neighbor_support")
        val optIn = buildJsonObject {
            put("agent_id", "neighbor-1")
            putJsonObject("attributes") {
                put("wants_food", 0)
                put("van_aversion", 1)
            }
        }
        log.attempt("main", "jim", "opt_in", optIn, STAMP)
        log.layPath(6 to 2)
        log.place("van_pad", 7, 2)
        val after = reportOutputs(log.world, log.stateOf()).scoring.getValue("neighbor_support")
        assertTrue(after < before)
    }

    @Test
    fun test_a_hoop_house_needs_an_open_south_side() {
        val log = EventLog(parcelWorld())
        log.place("woodland_tree", 3, 9)
        assertEquals("hoop-house-open-south", log.place("hoop_house", 2, 8).refusedRule())
    }

    @Test
    fun test_the_inherited_setback_refuses_a_structure_at_the_lot_line() {
        assertEquals("structure-setback", EventLog(parcelWorld()).place("hoop_house", 1, 8).refusedRule())
    }

    @Test
    fun test_the_parcel_level_override_relaxes_the_inherited_setback() {
        val child = editedWorld(PARCEL_FILE) { document -> withLinkedParcel(document, "parcel-b") }
        val log = EventLog(WorldLoader.load(PARCEL_FILE, referenceLibrary(mapOf(PARCEL_FILE to child))))
        assertIs<LogResult.Committed>(log.place("hoop_house", 1, 8))
        assertIs<LogResult.Refused>(log.place("commons_building", 0, 14))
    }

    private fun withLinkedParcel(document: JsonObject, parcel: String): JsonObject {
        val link = document.getValue("links").jsonArray[0].jsonObject
        val relinked = JsonObject(link + ("parcel" to JsonPrimitive(parcel)))
        return JsonObject(document + ("links" to kotlinx.serialization.json.JsonArray(listOf(relinked))))
    }

    @Test
    fun test_a_tick_advances_stocks_by_their_equations() {
        val log = EventLog(parcelWorld())
        (9 until 12).forEach { col -> log.place("paddock", col, 12) }
        log.attempt("main", "clock", "tick", buildJsonObject { put("n", 365) }, STAMP)
        val outputs = reportOutputs(log.world, log.stateOf())
        assertEquals(365L, outputs.ticks)
        assertNear(3 * 625 / 43560.0 * 4.0, outputs.stocks.getValue("standing_forage"))
    }

    @Test
    fun test_a_tap_on_an_empty_tile_places_and_a_tap_on_the_same_use_removes() {
        val log = EventLog(parcelWorld())
        assertEquals(TapDecision.Place("pond", 3, 20), actionForTap(log.stateOf(), 3, 20, "pond"))
        log.place("pond", 3, 20)
        assertEquals(TapDecision.Remove(3, 20), actionForTap(log.stateOf(), 3, 20, "pond"))
        assertIs<TapDecision.Refused>(actionForTap(log.stateOf(), 3, 20, "paddock"))
    }

    @Test
    fun test_render_props_carry_the_tilemap_contract() {
        val log = runDemo(parcelWorld())
        val props = renderProps(log.world, log.stateOf())
        assertTrue(props.keys.containsAll(listOf("cols", "rows", "shape", "view", "uses", "cells", "counts", "areas")))
        @Suppress("UNCHECKED_CAST")
        val counts = props.getValue("counts") as Map<String, Int>
        assertEquals(counts.values.sum(), (props.getValue("cells") as List<*>).size)
    }

    private fun checkInParcel(text: String): String =
        assertFailsWith<ExpressionException> { checkExpression(parseExpression(text), Scope(parcelWorld(), ScopeSite.EQUATION)) }.kind

    @Test
    fun test_out_of_vocabulary_input_is_refused() {
        assertEquals("unknown_word", checkInParcel("while(true)"))
        assertEquals("syntax", checkInParcel("count('path'); count('path')"))
        assertEquals("syntax", checkInParcel("{ count('path') }"))
        assertEquals("unknown_word", checkInParcel("import('os')"))
        assertEquals("syntax", checkInParcel("count(pasture_yield)"))
        assertEquals("unknown_word", checkInParcel("lambda"))
    }

    @Test
    fun test_an_expression_over_the_node_bound_is_refused() {
        val refused = assertFailsWith<ExpressionException> { parseExpression(List(MAX_NODES + 1) { "1" }.joinToString(" + ")) }
        assertEquals("bound", refused.kind)
    }

    @Test
    fun test_an_expression_nested_past_the_depth_bound_is_refused() {
        val refused = assertFailsWith<ExpressionException> { parseExpression("(".repeat(MAX_DEPTH + 1) + "1" + ")".repeat(MAX_DEPTH + 1)) }
        assertEquals("bound", refused.kind)
    }

    @Test
    fun test_a_self_referring_equation_is_a_cycle_the_world_refuses() {
        val child = editedWorld(PARCEL_FILE) { document -> withFirstEquation(document, "pasture_yield + 0 [ton/year]") }
        val refused = assertFailsWith<WorldLoadException> { WorldLoader.load(PARCEL_FILE, referenceLibrary(mapOf(PARCEL_FILE to child))) }
        assertTrue("cycle" in refused.message)
    }

    private fun withFirstEquation(document: JsonObject, expression: String): JsonObject {
        val equations = document.getValue("equations").jsonArray
        val first = JsonObject(equations[0].jsonObject + ("expr" to JsonPrimitive(expression)))
        return JsonObject(document + ("equations" to kotlinx.serialization.json.JsonArray(listOf(first) + equations.drop(1))))
    }

    @Test
    fun test_adding_unlike_units_is_refused() {
        assertEquals("unit_mismatch", checkInParcel("count('path') + tile_area"))
    }

    @Test
    fun test_units_convert_through_the_unit_table() {
        val world = parcelWorld()
        val evaluation = Evaluation(world, world.seedState())
        assertEquals(Value.Bool(true), evaluation.valueOf(parseExpression("1 [acre] == 43560 [sq_ft]")))
        assertEquals(Value.Bool(true), evaluation.valueOf(parseExpression("1 [year] == 365 [day]")))
    }

    @Test
    fun test_if_evaluates_only_the_branch_it_chooses() {
        val world = dungeonWorld()
        val evaluation = Evaluation(world, world.seedState())
        val tree = parseExpression("if(count('monster') > 0 [tile], count('treasure') / count('monster'), 0)")
        assertEquals(Value.Num(0.0), evaluation.valueOf(tree))
    }

    @Test
    fun the_demo_event_count_matches_the_reference_transcript() {
        val log = runDemo(parcelWorld())
        assertEquals("e33", log.events.last().id)
        assertEquals(365, log.events.last().parameters.getValue("n").jsonPrimitive.int)
    }
}
