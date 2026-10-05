package ai.factoredui.worldengine

import ai.factoredui.worldengine.outputs.TapDecision
import ai.factoredui.worldengine.session.DispatchResult
import ai.factoredui.worldengine.session.WorldAction
import ai.factoredui.worldengine.session.WorldSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldSessionTest {
    private var clock = 0

    private fun parcelSession(): WorldSession {
        val session = WorldSession.fromJson(PARCEL_WORLD_JSON, PARCEL_FILE, referenceLibrary())
        return WorldSession(session.world, timestamps = { "2026-10-05T01:00:${(clock++ % 60).toString().padStart(2, '0')}Z" }, defaultActor = "jim")
    }

    private fun counts(session: WorldSession, branch: String = session.currentBranch): Map<String, Int> = session.counts(branch)

    @Test
    fun a_tap_on_an_empty_tile_places_and_a_second_tap_with_the_same_use_removes() {
        val session = parcelSession()
        assertEquals(TapDecision.Place("pond", 3, 20), session.actionForTap(3, 20, "pond"))
        assertIs<DispatchResult.Accepted>(session.tap(3, 20, "pond"))
        assertEquals(1, counts(session).getValue("pond"))
        assertEquals(TapDecision.Remove(3, 20), session.actionForTap(3, 20, "pond"))
        assertIs<DispatchResult.Accepted>(session.tap(3, 20, "pond"))
        assertEquals(0, counts(session).getValue("pond"))
    }

    @Test
    fun a_refused_tap_carries_the_rule_message_and_leaves_the_log_untouched() {
        val session = parcelSession()
        val before = session.log.dump()
        val refused = assertIs<DispatchResult.Refused>(session.tap(10, 10, "van_pad"))
        assertEquals("van-pad-needs-path", refused.rule)
        assertEquals("a van pad needs a path on one of its four sides", refused.message)
        assertEquals(before, session.log.dump())
    }

    @Test
    fun a_tap_with_another_use_on_an_occupied_tile_is_refused_before_it_reaches_the_log() {
        val session = parcelSession()
        session.tap(3, 20, "pond")
        val refused = assertIs<DispatchResult.Refused>(session.tap(3, 20, "paddock"))
        assertEquals("occupied", refused.rule)
        assertEquals(1, session.log.events.size)
    }

    @Test
    fun render_props_carry_the_tilemap_contract_and_the_world_scores() {
        val session = parcelSession()
        session.dispatch(WorldAction.Place("paddock", 9, 12))
        val props = session.renderProps()
        assertEquals(13, props["cols"])
        assertEquals(26, props["rows"])
        assertEquals("square", props["shape"])
        assertEquals("iso", props["view"])
        assertEquals(625.0, props["tile_area"])
        assertEquals(listOf(mapOf("col" to 9, "row" to 12, "use" to "paddock")), props["cells"])
        assertEquals(mapOf("id" to "pond", "label" to "Pond", "color" to "#5B9BD5", "sprite" to "water"), (props["uses"] as List<*>)[5])
        @Suppress("UNCHECKED_CAST")
        val scores = props["scores"] as List<Map<String, Any?>>
        val support = scores.single { it["id"] == "neighbor_support" }
        assertEquals("Projected neighbour support", support["label"])
        assertEquals(false, support["binding"])
        assertEquals("1", support["unit"])
        val paddockArea = scores.single { it["id"] == "area_paddock" }
        assertEquals(625.0, paddockArea["value"])
    }

    @Test
    fun a_proposal_branch_is_isolated_until_it_is_endorsed_and_merged() {
        val session = parcelSession()
        assertIs<DispatchResult.Accepted>(session.createProposal("more-ponds"))
        assertTrue(session.switchBranch("more-ponds"))
        assertTrue(session.isProposal("more-ponds"))
        assertEquals("main", session.parentOf("more-ponds"))
        session.tap(3, 20, "pond")
        assertEquals(mapOf("paddock" to 0, "hoop_house" to 0, "commons_building" to 0, "van_pad" to 0, "path" to 0, "pond" to 1, "woodland_tree" to 0), session.countsDiff("more-ponds", "main"))
        assertEquals("proposal-unendorsed", assertIs<DispatchResult.Refused>(session.merge()).rule)
        assertEquals("projection-not-binding", assertIs<DispatchResult.Refused>(session.endorse("nearby", actor = "neighbor-1")).rule)
        assertIs<DispatchResult.Accepted>(session.endorse("on_site"))
        assertIs<DispatchResult.Accepted>(session.merge())
        assertEquals(1, counts(session, "main").getValue("pond"))
    }

    @Test
    fun switching_to_an_unknown_branch_is_refused_and_keeps_the_current_one() {
        val session = parcelSession()
        assertFalse(session.switchBranch("nowhere"))
        assertEquals("main", session.currentBranch)
    }

    @Test
    fun undo_reverts_the_latest_place_or_remove_and_then_the_one_before() {
        val session = parcelSession()
        session.tap(3, 20, "pond")
        session.tap(4, 20, "pond")
        assertIs<DispatchResult.Accepted>(session.undoLast())
        assertEquals(1, counts(session).getValue("pond"))
        assertIs<DispatchResult.Accepted>(session.undoLast())
        assertEquals(0, counts(session).getValue("pond"))
        assertEquals("nothing-to-undo", assertIs<DispatchResult.Refused>(session.undoLast()).rule)
    }

    @Test
    fun reverting_a_path_a_van_pad_depends_on_is_refused_by_the_always_rule() {
        val session = parcelSession()
        val path = assertIs<DispatchResult.Accepted>(session.tap(6, 3, "path")).event.id
        session.tap(7, 3, "van_pad")
        assertEquals("van-pad-needs-path", assertIs<DispatchResult.Refused>(session.revert(path)).rule)
        assertEquals(1, counts(session).getValue("path"))
    }

    @Test
    fun a_tick_advances_the_clock_and_an_out_of_range_tick_is_refused() {
        val session = parcelSession()
        assertIs<DispatchResult.Accepted>(session.dispatch(WorldAction.Tick(3)))
        assertEquals("tick-bound", assertIs<DispatchResult.Refused>(session.dispatch(WorldAction.Tick(100_001))).rule)
        assertEquals(3L, session.log.stateOf().ticks)
    }

    @Test
    fun a_runtime_division_by_zero_is_reported_and_leaves_the_log_untouched() {
        val divideWorld = editedWorld(DUNGEON_FILE) { document ->
            val rule = buildJsonObject {
                put("id", "door-needs-a-monster-ratio")
                put("on", "place")
                putJsonArray("applies_to") { add("door") }
                put("require", "1 / count('monster') > 0 / 1 [tile]")
                put("message", "a door needs monsters to ration")
            }
            JsonObject(document + ("rules" to JsonArray(listOf(rule))))
        }
        val session = WorldSession.fromJson(divideWorld, DUNGEON_FILE)
        assertNull(session.parentOf("main"))
        val failed = assertIs<DispatchResult.Failed>(session.tap(0, 0, "door"))
        assertEquals("divide_by_zero", failed.kind)
        assertTrue(session.log.events.isEmpty())
    }
}
