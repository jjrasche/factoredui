package ai.factoredui.worldengine

import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.log.LogResult
import ai.factoredui.worldengine.world.WorldLoadException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EventIdTest {

    private fun newLog(): EventLog = EventLog(parcelWorld()).also {
        it.place("path", 0, 0)
        it.place("path", 1, 0)
        it.place("path", 2, 0)
    }

    private fun relabelled(log: EventLog, from: String, to: String): JsonObject =
        Json.parseToJsonElement(log.dump().toString().replace("\"$from\"", "\"$to\"")) as JsonObject

    private fun idOf(result: LogResult): String = assertIs<LogResult.Committed>(result).event.id

    @Test
    fun aFreshLogNumbersItsEventsOneAfterAnother() {
        val log = EventLog(parcelWorld())
        assertEquals(listOf("e1", "e2", "e3"), listOf(log.place("path", 0, 0), log.place("path", 1, 0), log.branch("idea", "main", "jim", STAMP)).map(::idOf))
    }

    @Test
    fun theNextIdAfterALoadedLogThatSkipsIdsIsOnePastTheHighest() {
        val loaded = EventLog.load(parcelWorld(), relabelled(newLog(), "e2", "e7"))
        assertEquals("e8", idOf(loaded.place("path", 3, 0)))
    }

    @Test
    fun aBranchOpenedAfterALoadAlsoTakesOnePastTheHighest() {
        val loaded = EventLog.load(parcelWorld(), relabelled(newLog(), "e3", "e9"))
        assertEquals("e10", idOf(loaded.branch("idea", "main", "jim", STAMP)))
    }

    @Test
    fun anIdLongerThanAnyIntegerIsStillOnePastTheHighest() {
        val huge = "e" + "9".repeat(30)
        val loaded = EventLog.load(parcelWorld(), relabelled(newLog(), "e3", huge))
        assertEquals("e1" + "0".repeat(30), idOf(loaded.place("path", 3, 0)))
    }

    @Test
    fun aLogThatReusesAnActionIdDoesNotLoad() {
        val refusal = assertFailsWith<WorldLoadException> { EventLog.load(parcelWorld(), relabelled(newLog(), "e2", "e1")) }
        assertTrue(refusal.message.startsWith("event-id-duplicate: event id e1 appears twice"), refusal.message)
    }

    @Test
    fun aLogWhoseBranchEventReusesAnIdDoesNotLoad() {
        val log = newLog().also { it.branch("idea", "main", "jim", STAMP) }
        val refusal = assertFailsWith<WorldLoadException> { EventLog.load(parcelWorld(), relabelled(log, "e4", "e2")) }
        assertTrue(refusal.message.startsWith("event-id-duplicate"), refusal.message)
    }
}
