package ai.factoredui.worldengine

import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.log.LogResult
import ai.factoredui.worldengine.text.pythonFloatRepr
import ai.factoredui.worldengine.world.WorldLoadException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventLogTest {
    private fun committed(result: LogResult) = assertIs<LogResult.Committed>(result).event

    private fun refusalText(result: LogResult): String = assertIs<LogResult.Refused>(result).refusal.toString()

    private fun scriptedLog(): EventLog {
        val log = EventLog(parcelWorld())
        log.place("hoop_house", 4, 8)
        log.remove(5, 8)
        log.attempt("main", "clock", "tick", buildJsonObject { put("n", 2) }, STAMP)
        log.branch("idea", "main", "jim", STAMP)
        log.place("pond", 3, 20, branch = "idea")
        log.place("paddock", 9, 12, branch = "idea")
        return log
    }

    @Test
    fun a_place_touches_every_tile_of_its_footprint() {
        val event = committed(EventLog(parcelWorld()).place("hoop_house", 4, 8))
        assertEquals(listOf("4,8", "5,8"), event.touches)
        assertNull(event.removed)
    }

    @Test
    fun a_remove_records_what_stood_there_in_declared_units() {
        val log = EventLog(parcelWorld())
        log.place("hoop_house", 4, 8)
        val event = committed(log.remove(5, 8))
        assertEquals(listOf("4,8", "5,8"), event.touches)
        val removed = event.removed!!
        assertEquals("hoop_house", removed.getValue("type").jsonPrimitive.content)
        assertEquals(4, removed.getValue("col").jsonPrimitive.content.toInt())
        val labour = removed.getValue("properties").jsonObject.getValue("labor_hours_per_tile").jsonPrimitive.double
        assertEquals("24.57682291666667", pythonFloatRepr(labour))
    }

    @Test
    fun a_tick_touches_the_clock_and_an_enrolment_touches_its_agent() {
        val log = EventLog(parcelWorld())
        assertEquals(listOf("clock"), committed(log.attempt("main", "clock", "tick", buildJsonObject { put("n", 2) }, STAMP)).touches)
        val enrol = buildJsonObject {
            put("agent_type", "neighbor")
            put("agent_id", "neighbor-9")
        }
        assertEquals(listOf("agent:neighbor-9"), committed(log.attempt("main", "jim", "enroll", enrol, STAMP)).touches)
        assertEquals("agent-exists: agent neighbor-9 is already enrolled", refusalText(log.attempt("main", "jim", "enroll", enrol, STAMP)))
    }

    @Test
    fun a_merge_lists_the_incoming_events_and_touches_their_union() {
        val log = scriptedLog()
        val merge = committed(log.merge("idea", "main", "jim", STAMP))
        assertEquals("e7", merge.id)
        assertEquals(listOf("3,20", "9,12"), merge.touches)
        assertEquals(JsonArray(listOf(JsonPrimitive("e5"), JsonPrimitive("e6"))), merge.parameters.getValue("events"))
        assertEquals("nothing-to-merge: main already holds every event of idea", refusalText(log.merge("idea", "main", "jim", STAMP)))
    }

    @Test
    fun branch_refusals_name_the_branch() {
        val log = scriptedLog()
        assertEquals("branch-exists: branch idea already exists", refusalText(log.branch("idea", "main", "jim", STAMP)))
        assertEquals("unknown-branch: no branch or event 'nope'", refusalText(log.branch("x", "nope", "jim", STAMP)))
        val fromEvent = committed(log.branch("from-e1", "e1", "jim", STAMP))
        assertEquals("e1", fromEvent.parent)
        assertEquals("e1", fromEvent.parameters.getValue("from").jsonPrimitive.content)
    }

    @Test
    fun governance_verbs_and_endorsements_off_a_proposal_are_refused_as_plain_actions() {
        val log = EventLog(parcelWorld())
        assertEquals("governance-verb: use Log.merge() for 'merge'", refusalText(log.attempt("main", "jim", "merge", JsonObject(emptyMap()), STAMP)))
        assertEquals("not-a-proposal: branch main is not a proposal", refusalText(log.attempt("main", "jim", "endorse", buildJsonObject { put("weight_class", "nearby") }, STAMP)))
        assertEquals("unknown-action: no action 'dig'", refusalText(log.attempt("main", "jim", "dig", JsonObject(emptyMap()), STAMP)))
        assertTrue(log.events.isEmpty())
    }

    @Test
    fun revert_refuses_unknown_unsupported_and_repeated_reverts() {
        val log = scriptedLog()
        assertEquals("revert-unsupported: only place and remove revert; branch from before e3 instead", refusalText(log.revert("e3", "main", "jim", STAMP)))
        assertEquals("revert-unknown: e99 is not applied on main", refusalText(log.revert("e99", "main", "jim", STAMP)))
        val revert = committed(log.revert("e2", "main", "jim", STAMP))
        assertEquals(listOf("4,8", "5,8"), revert.touches)
        assertEquals("place", revert.parameters.getValue("undo").jsonObject.getValue("action").jsonPrimitive.content)
        assertEquals("already-reverted: e2 is already reverted", refusalText(log.revert("e2", "main", "jim", STAMP)))
        assertEquals(1, reportCounts(log).getValue("hoop_house") / 2)
    }

    private fun reportCounts(log: EventLog) = ai.factoredui.worldengine.outputs.reportOutputs(log.world, log.stateOf()).counts

    @Test
    fun a_refused_attempt_does_not_consume_an_event_number_but_a_branch_does() {
        val log = EventLog(parcelWorld())
        log.place("van_pad", 10, 10)
        assertEquals("e1", committed(log.branch("idea", "main", "jim", STAMP)).id)
        assertEquals("e2", committed(log.place("pond", 3, 20, branch = "idea")).id)
    }

    @Test
    fun a_saved_log_with_removes_merges_and_reverts_replays_byte_identically() {
        val log = scriptedLog()
        log.merge("idea", "main", "jim", STAMP)
        log.revert("e2", "main", "jim", STAMP)
        val reloaded = EventLog.load(parcelWorld(), log.dump())
        assertEquals(log.dump().toString(), reloaded.dump().toString())
        log.heads.keys.forEach { assertEquals(log.stateOf(it).snapshot(), reloaded.stateOf(it).snapshot()) }
    }

    private fun withEvents(document: JsonObject, edit: (List<JsonObject>) -> List<JsonObject>): JsonObject =
        JsonObject(document + ("events" to JsonArray(edit(document.getValue("events").jsonArray.map { it.jsonObject }))))

    private fun withField(event: JsonObject, key: String, value: kotlinx.serialization.json.JsonElement): JsonObject = JsonObject(event + (key to value))

    @Test
    fun replay_refuses_a_log_from_another_world() {
        val document = withEvents(scriptedLog().dump()) { events -> listOf(withField(events[0], "world", JsonPrimitive("dungeon-tiny"))) + events.drop(1) }
        val refused = assertFailsWith<WorldLoadException> { EventLog.load(parcelWorld(), document) }
        assertEquals("event e1 belongs to world dungeon-tiny", refused.message)
    }

    @Test
    fun replay_refuses_an_event_that_does_not_extend_its_branch_head() {
        val document = withEvents(scriptedLog().dump()) { events -> listOf(events[0], withField(events[1], "parent", kotlinx.serialization.json.JsonNull)) + events.drop(2) }
        val refused = assertFailsWith<WorldLoadException> { EventLog.load(parcelWorld(), document) }
        assertEquals("event e2 does not extend the head of branch main", refused.message)
    }

    @Test
    fun replay_refuses_a_merge_that_claims_events_the_conflict_rule_does_not_allow() {
        val log = scriptedLog()
        log.merge("idea", "main", "jim", STAMP)
        val document = withEvents(log.dump()) { events ->
            events.map { event ->
                if (event.getValue("action").jsonPrimitive.content != "merge") return@map event
                val parameters = JsonObject(event.getValue("parameters").jsonObject + ("events" to JsonArray(listOf(JsonPrimitive("e5")))))
                withField(event, "parameters", parameters)
            }
        }
        val refused = assertFailsWith<WorldLoadException> { EventLog.load(parcelWorld(), document) }
        assertEquals("event e7 does not replay: merge of idea is not the merge the conflict rule allows (['e5', 'e6'])", refused.message)
    }

    @Test
    fun a_log_that_breaks_the_events_schema_does_not_load() {
        val document = withEvents(scriptedLog().dump()) { events -> listOf(withField(events[0], "id", JsonPrimitive("x1"))) + events.drop(1) }
        val refused = assertFailsWith<WorldLoadException> { EventLog.load(parcelWorld(), document) }
        assertTrue(refused.message.startsWith("event log does not match events.schema.json: ["), refused.message)
    }
}
