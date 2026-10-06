package ai.factoredui.worldbuilder

import ai.factoredui.compose.scene.ViewState
import ai.factoredui.worldengine.session.DispatchResult
import ai.factoredui.worldengine.session.WorldAction
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun groundDemoHost() = WorldBuilderHost(openSession(File(designDirectory(), "worlds/parcel-ground-demo.world.json").path))

class WorldBuilderTerrainTest {

    @Test
    fun theGroundDemoBindsEveryVertexHeightTheEngineHolds() {
        val host = groundDemoHost()
        val ground = host.session.ground()!!
        val terrain = host.bindings()["terrain"] as Map<*, *>
        val cols = host.session.world.cols
        val rows = host.session.world.rows
        assertEquals(cols, terrain["cols"])
        assertEquals(rows, terrain["rows"])
        assertEquals((rows + 1) * (cols + 1), (terrain["heights_mm"] as List<*>).size)
        assertEquals(ground.heightsMm.map { it.roundToInt() }, terrain["heights_mm"])
        assertEquals(ground.version, terrain["version"])
        assertEquals(List((rows + 1) * (cols + 1)) { 0 }, terrain["cut_fill_mm"])
    }

    @Test
    fun aDigShowsAsCutAtTheFourCornersOfTheDugTile() {
        val host = groundDemoHost()
        val before = host.terrain()!!["version"] as Long
        assertIs<DispatchResult.Accepted>(host.session.dispatch(WorldAction.Dig(2, 1, 120)))
        val terrain = host.terrain()!!
        val cutFill = terrain["cut_fill_mm"] as List<*>
        val vertexCols = host.session.world.cols + 1
        val corners = listOf(1 * vertexCols + 2, 1 * vertexCols + 3, 2 * vertexCols + 2, 2 * vertexCols + 3)
        assertEquals(corners.map { -120 }, corners.map { cutFill[it] })
        assertEquals(4, cutFill.count { it != 0 })
        assertTrue((terrain["version"] as Long) > before, "the ground version moves when a height changes")
    }

    @Test
    fun aVectorLevelSetsTheViewAndTheTerrainModeTheVectorMapWillShow() {
        val bindings = groundDemoHost().levelBindings(ViewState(null, quarterTurns = 1), 625, null)
        assertEquals(mapOf("level_feet" to 625, "quarter_turns" to 1), bindings["view_state"])
        assertEquals("hillshade", bindings["terrain_mode"])
    }

    @Test
    fun aPixelLevelSetsOnlyTheView() {
        val bindings = groundDemoHost().levelBindings(ViewState(null), 5, null)
        assertEquals(setOf("view_state"), bindings.keys)
    }

    @Test
    fun aVectorLevelLeavesAnExplicitTerrainChoiceAlone() {
        listOf("heat", "off").forEach { chosen ->
            assertFalse("terrain_mode" in groundDemoHost().levelBindings(ViewState(null), 625, chosen), chosen)
        }
    }

    @Test
    fun aWorldWithoutGroundBindsNoTerrain() {
        assertFalse("terrain" in parcelHost().bindings())
    }
}
