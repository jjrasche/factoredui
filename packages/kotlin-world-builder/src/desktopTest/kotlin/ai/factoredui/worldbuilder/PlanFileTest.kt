package ai.factoredui.worldbuilder

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun worldPath() = File(designDirectory(), "worlds/parcel-five-acre.world.json").path

class PlanFileTest {

    @Test
    fun aPlanFileCarriesTheLogAndTheNamesYouGaveYourPlans() {
        val log = JsonObject(mapOf("world" to JsonPrimitive("w"), "events" to JsonArray(emptyList())))
        val decoded = decodePlanFile(encodePlanFile(log, mapOf("main" to "Orchard")))
        assertEquals(log, decoded.log)
        assertEquals(mapOf("main" to "Orchard"), decoded.names)
    }

    @Test
    fun aBareEventLogOpensAsAPlanWithNoNames() {
        val bare = """{"world": "w", "events": []}"""
        val decoded = decodePlanFile(bare)
        assertEquals(emptyMap(), decoded.names)
        assertTrue((decoded.log as JsonObject).containsKey("events"))
    }

    @Test
    fun theFileNameCarriesTheWorldAndTheMoment() {
        assertEquals("parcel-five-acre-20261005-143007.plan.json", planFileName("parcel-five-acre", LocalDateTime.of(2026, 10, 5, 14, 30, 7)))
    }

    @Test
    fun savingHandsTheWriterTheNamedFileAndTellsYouWhereItWent() {
        var savedName = ""
        var savedText = ""
        val host = WorldBuilderHost(openSession(worldPath()), writePlan = { name, text ->
            savedName = name
            savedText = text
            "C:/plans/$name"
        })
        host.tap(6, 0, "path")
        host.savePlan()
        assertTrue(savedName.startsWith("parcel-five-acre-") && savedName.endsWith(".plan.json"), savedName)
        assertTrue("\"events\"" in savedText && "\"path\"" in savedText, "the log is in the file")
        val message = host.bindings()["message"] as String
        assertTrue(message.startsWith("Saved to C:/plans/parcel-five-acre-"), message)
    }

    @Test
    fun aSavedPlanOpensAgainWithItsAlternativesAndNames() {
        val folder = Files.createTempDirectory("plans").toFile()
        val host = WorldBuilderHost(openSession(worldPath()), writePlan = { name, text -> File(folder, name).also { it.writeText(text) }.path })
        host.tap(6, 0, "path")
        host.renameCurrent("Home farm")
        host.newProposal()
        host.tap(2, 2, "pond")
        host.savePlan()
        val saved = folder.listFiles().single()
        val reopened = openPlan(worldPath(), saved.path)
        val again = WorldBuilderHost(reopened.session, initialPlanNames = reopened.names)
        assertEquals(listOf("Home farm", "Alternative 1"), again.bindings().let { it["plans"] as List<*> }.map { (it as Map<*, *>)["label"] })
        again.switchTo("proposal-1")
        assertEquals(1, again.counts()["pond"])
        again.switchTo("main")
        assertEquals(1, again.counts()["path"])
        assertEquals(0, again.counts()["pond"])
    }
}
