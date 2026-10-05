package ai.factoredui.worldbuilder

import ai.factoredui.worldengine.session.WorldAction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun designDirectory(): File {
    val configured = System.getProperty("WORLD_ENGINE_DESIGN_DIR") ?: System.getenv("WORLD_ENGINE_DESIGN_DIR")
    val directory = File(configured ?: "C:/Users/rasche_j/Documents/workspace/van-life/.git-worktrees/design-world-engine/design/world-engine")
    check(directory.isDirectory) { "design directory not found: $directory (set WORLD_ENGINE_DESIGN_DIR)" }
    return directory
}

internal fun parcelHost(presentation: Map<String, UsePresentation> = emptyMap()) =
    WorldBuilderHost(openSession(File(designDirectory(), "worlds/parcel-five-acre.world.json").path), presentation)

@Suppress("UNCHECKED_CAST")
internal fun WorldBuilderHost.counts(): Map<String, Int> = bindings()["counts"] as Map<String, Int>

class WorldBuilderHostTest {

    private val everyUse = listOf("paddock", "hoop_house", "commons_building", "van_pad", "path", "pond", "woodland_tree")

    @Test
    fun theParcelOpensEmptyWithEverySevenUsesCountedAtZero() {
        val bindings = parcelHost().bindings()
        assertEquals(everyUse.associateWith { 0 }, bindings["counts"])
        assertEquals(emptyList<Any?>(), bindings["cells"])
        assertEquals(true, bindings["controlled"])
        assertEquals(mapOf("cols" to 13, "rows" to 26, "tile_area" to 625.0, "view" to "iso"), bindings["parcel"])
        assertTrue((bindings["scores_text"] as String).contains("Pasture yield"), "the world's scores reach the panel")
        assertEquals("", bindings["message"])
        assertEquals("main", bindings["branch"])
    }

    @Test
    fun aTapPlacesAndTheSameBrushTapRemoves() {
        val host = parcelHost()
        host.tap(6, 0, "path")
        assertEquals(1, host.counts()["path"])
        host.tap(6, 0, "path")
        assertEquals(0, host.counts()["path"])
    }

    @Test
    fun theEraseBrushRemovesWhateverIsThere() {
        val host = parcelHost()
        host.tap(6, 0, "path")
        host.tap(6, 0, "erase")
        assertEquals(0, host.counts()["path"])
    }

    @Test
    fun aRefusedTapShowsTheRulesMessageAndChangesNothing() {
        val host = parcelHost()
        host.tap(10, 10, "van_pad")
        assertEquals("Refused: a van pad needs a path on one of its four sides", host.bindings()["message"])
        assertEquals(0, host.counts()["van_pad"])
    }

    @Test
    fun theNextAcceptedTapClearsTheMessage() {
        val host = parcelHost()
        host.tap(10, 10, "van_pad")
        host.tap(6, 0, "path")
        assertEquals("", host.bindings()["message"])
    }

    @Test
    fun aProposalIsABranchThatDoesNotTouchMainUntilMerged() {
        val host = parcelHost()
        host.tap(6, 0, "path")
        host.newProposal()
        assertEquals("proposal-1", host.session.currentBranch)
        host.tap(6, 1, "path")
        assertEquals(2, host.counts()["path"])
        assertEquals("proposal-1 vs main: +1 Path", host.bindings()["diff_text"])
        host.cycleBranch()
        assertEquals("main", host.session.currentBranch)
        assertEquals(1, host.counts()["path"])
        assertEquals("on main", host.bindings()["diff_text"])
    }

    @Test
    fun theBranchLineNamesTheBranchItsKindAndTheCount() {
        val host = parcelHost()
        assertEquals("branch main (1 in all)", host.bindings()["branch_line"])
        host.newProposal()
        assertEquals("proposal proposal-1 (2 in all)", host.bindings()["branch_line"])
    }

    @Test
    fun undoRevertsTheLastPlacement() {
        val host = parcelHost()
        host.tap(6, 0, "path")
        host.tap(6, 1, "path")
        host.undo()
        assertEquals(1, host.counts()["path"])
        assertEquals("", host.bindings()["message"])
    }

    @Test
    fun undoWithNothingToUndoExplainsWhy() {
        val host = parcelHost()
        host.undo()
        assertTrue((host.bindings()["message"] as String).isNotEmpty(), "an empty undo is not silent")
    }

    @Test
    fun theUsesCarryTheHostsPresentationExtras() {
        val host = parcelHost(mapOf("paddock" to UsePresentation(critter = "sheep"), "commons_building" to UsePresentation(height = 1.2)))
        @Suppress("UNCHECKED_CAST")
        val uses = (host.bindings()["uses"] as List<Map<String, Any?>>).associateBy { it["id"] }
        assertEquals("sheep", uses.getValue("paddock")["critter"])
        assertEquals(1.2, uses.getValue("commons_building")["height"])
        assertEquals(null, uses.getValue("pond")["critter"])
    }

    @Test
    fun theTapActionDispatchesTheTapPublishesAndReportsTheBrush() {
        val host = parcelHost()
        var published = 0
        val handler = host.actions { published++ }.getValue("world.tileTapped")
        runBlocking { handler(mapOf("col" to 6.0, "row" to 0.0, "use" to "path")) }
        assertEquals(1, published)
        assertEquals(1, host.counts()["path"])
    }

    @Test
    fun aScriptedTapSequenceReproducesThePythonDemosFinalCounts() {
        val host = parcelHost()
        val demo = Json.parseToJsonElement(File(designDirectory(), "demos/parcel-five-acre.demo.json").readText()).jsonObject
        demo.getValue("steps").jsonArray.map { it.jsonObject }.forEach { step -> replayStep(host, step) }
        val expected = mapOf("paddock" to 12, "hoop_house" to 2, "commons_building" to 4, "van_pad" to 1, "path" to 6, "pond" to 2, "woodland_tree" to 4)
        assertEquals(expected, host.counts(), "the same final counts engine_ref.py --demo prints")
    }

    private fun replayStep(host: WorldBuilderHost, step: JsonObject) {
        val parameters = step["parameters"]?.jsonObject
        val branch = step["on"]?.jsonPrimitive?.content
        when (step.getValue("do").jsonPrimitive.content) {
            "place" -> placeStep(host, parameters!!, branch)
            "remove" -> host.tap(parameters!!.getValue("col").jsonPrimitive.int, parameters.getValue("row").jsonPrimitive.int, "erase")
            "branch" -> host.session.createBranch(
                step.getValue("name").jsonPrimitive.content,
                step.getValue("from").jsonPrimitive.content,
                step["proposal"]?.jsonPrimitive?.content == "true",
            )
            "merge" -> host.session.merge(step.getValue("branch").jsonPrimitive.content, step.getValue("into").jsonPrimitive.content)
            "endorse" -> host.session.endorse(parameters!!.getValue("weight_class").jsonPrimitive.content, step.getValue("actor").jsonPrimitive.content, branch!!)
            "tick" -> host.session.dispatch(WorldAction.Tick(parameters!!.getValue("n").jsonPrimitive.int))
        }
    }

    private fun placeStep(host: WorldBuilderHost, parameters: JsonObject, branch: String?) {
        val col = parameters.getValue("col").jsonPrimitive.int
        val row = parameters.getValue("row").jsonPrimitive.int
        val use = parameters.getValue("type").jsonPrimitive.content
        if (branch == null) host.tap(col, row, use) else host.session.dispatch(WorldAction.Place(use, col, row), branch)
    }
}
