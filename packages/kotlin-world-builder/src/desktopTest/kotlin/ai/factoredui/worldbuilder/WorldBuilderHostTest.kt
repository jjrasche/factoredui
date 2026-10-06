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
    val configured = System.getProperty("WORLD_ENGINE_DESIGN_DIR")?.takeIf { it.isNotBlank() }
        ?: error("WORLD_ENGINE_DESIGN_DIR is not set: Gradle passes the vendored reference at packages/kotlin-world-engine/reference")
    val directory = File(configured)
    check(directory.isDirectory) { "design directory not found: $directory" }
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
        assertEquals("Alternative 1 vs My plan: +1 Path", host.bindings()["diff_text"])
        host.cycleBranch()
        assertEquals("main", host.session.currentBranch)
        assertEquals(1, host.counts()["path"])
        assertEquals("My plan is your base plan.", host.bindings()["diff_text"])
    }

    @Test
    fun theBranchLineNamesTheBranchItsKindAndTheCount() {
        val host = parcelHost()
        assertEquals("Viewing: My plan (1 plan in all)", host.bindings()["branch_line"])
        host.newProposal()
        assertEquals("Viewing: Alternative 1 (2 plans in all)", host.bindings()["branch_line"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun WorldBuilderHost.plans(): List<Map<String, Any?>> = bindings()["plans"] as List<Map<String, Any?>>

    @Test
    fun plansAreCalledMyPlanThenAlternativesInPlainWords() {
        val host = parcelHost()
        host.newProposal()
        host.newProposal()
        assertEquals(listOf("My plan", "Alternative 1", "Alternative 2"), host.plans().map { it["label"] })
    }

    @Test
    fun theCurrentPlanIsMarkedAsBeingViewed() {
        val host = parcelHost()
        host.newProposal()
        assertEquals(listOf("", "viewing"), host.plans().map { it["status"] })
    }

    @Test
    fun aPlanCanBeRenamedAndTheNewNameShowsEverywhere() {
        val host = parcelHost()
        host.renameCurrent("  Orchard plan  ")
        assertEquals("Viewing: Orchard plan (1 plan in all)", host.bindings()["branch_line"])
        assertEquals("Orchard plan", host.plans().single()["label"])
        host.newProposal()
        assertEquals("Alternative 1 matches Orchard plan", host.bindings()["diff_text"])
        assertEquals("Compared with Orchard plan", host.bindings()["compare_title"])
    }

    @Test
    fun aBlankNameIsRefusedWithAReasonAndKeepsTheOldName() {
        val host = parcelHost()
        host.renameCurrent("   ")
        assertEquals("Give the plan a name first.", host.bindings()["message"])
        assertEquals("My plan", host.plans().single()["label"])
    }

    @Test
    fun switchingToAPlanShowsItAndAnUnknownPlanChangesNothing() {
        val host = parcelHost()
        host.newProposal()
        host.switchTo("main")
        assertEquals("main", host.session.currentBranch)
        host.switchTo("no-such-plan")
        assertEquals("main", host.session.currentBranch)
    }

    @Test
    fun anAlternativeIsComparedWithItsBaseScoreByScore() {
        val host = parcelHost()
        host.tap(6, 0, "path")
        host.newProposal()
        host.tap(2, 2, "pond")
        val text = host.bindings()["compare_text"] as String
        assertTrue(text.lines().any { it.startsWith("Pond area: 0 sq ft to 625 sq ft (+625 sq ft)") }, text)
        assertTrue(text.lines().none { it.startsWith("Path area") }, "a figure that did not move is not listed: $text")
    }

    @Test
    fun theBasePlanWithAlternativesTellsYouToPickOneToCompare() {
        val host = parcelHost()
        host.newProposal()
        host.switchTo("main")
        assertEquals("Compare plans", host.bindings()["compare_title"])
        assertEquals("Pick an alternative to see how it differs from My plan.", host.bindings()["compare_text"])
    }

    @Test
    fun aSinglePlanHasNothingToCompare() {
        val host = parcelHost()
        assertEquals("", host.bindings()["compare_title"])
        assertEquals("", host.bindings()["compare_text"])
    }

    @Test
    fun theSwitchAndRenameActionsDriveTheHostWithResolvedParams() {
        val host = parcelHost()
        host.newProposal()
        val handlers = host.actions { }
        kotlinx.coroutines.runBlocking {
            handlers.getValue("world.switchPlan")(mapOf("id" to "main"))
            handlers.getValue("world.renamePlan")(mapOf("name" to "Home farm"))
        }
        assertEquals("Viewing: Home farm (2 plans in all)", host.bindings()["branch_line"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun WorldBuilderHost.groups(): List<Map<String, Any?>> = bindings()["score_groups"] as List<Map<String, Any?>>

    @Test
    fun theWorldsScoresArriveGroupedInPlainWords() {
        val titles = parcelHost().groups().map { it["title"] }
        assertEquals(listOf("Cost", "Labour", "Yield", "Neighbours"), titles)
    }

    @Test
    fun theAreaScoresThatRepeatTheUsageLinesAreNotListedAgain() {
        @Suppress("UNCHECKED_CAST")
        val lines = parcelHost().groups().flatMap { (it["rows"] as List<Map<String, Any?>>).map { row -> row["line"] as String } }
        assertTrue(lines.none { it.contains(" area:") }, lines.toString())
    }

    @Test
    fun tappingTheCapitalFloorExplainsItAndNamesTheUsesWithNoPriceYet() {
        val host = parcelHost()
        host.selectScore("capex_floor")
        val bindings = host.bindings()
        assertEquals("Sourced capital floor", bindings["score_title"])
        val text = bindings["score_text"] as String
        assertTrue("a floor: banked up-front prices only" in text, text)
        assertTrue("No price yet for: Hoop house, Commons building, Van pad, Path, Pond, Woodland tree." in text, text)
        assertTrue("twin" in text.lines().first { it.startsWith("Where the figure comes from") }, text)
    }

    @Test
    fun tappingTheSameScoreAgainClosesItsDetail() {
        val host = parcelHost()
        host.selectScore("capex_floor")
        host.selectScore("capex_floor")
        assertEquals("", host.bindings()["score_text"])
    }

    @Test
    fun theScoreTapActionOpensTheDetail() {
        val host = parcelHost()
        kotlinx.coroutines.runBlocking { host.actions { }.getValue("world.scoreTapped")(mapOf("id" to "labor_hours_total")) }
        assertEquals("Labour hours", host.bindings()["score_title"])
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
