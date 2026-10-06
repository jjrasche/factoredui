package ai.factoredui.worldbuilder

import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.fitFlowView
import ai.factoredui.compose.layout.project
import ai.factoredui.compose.layout.tileCenter
import ai.factoredui.compose.layout.tilemapScreenBounds
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.scene.LANDING_LEVEL_FEET
import ai.factoredui.compose.scene.ViewState
import ai.factoredui.compose.schema.Spec
import ai.factoredui.worldengine.session.DispatchResult
import ai.factoredui.worldengine.session.WorldAction
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.serialization.json.Json

private const val WINDOW_WIDTH_PX = 1500
private const val WINDOW_HEIGHT_PX = 900
private const val TILE_WIDTH = 64f
private const val HEADROOM = 0.95f
private const val FIT_MARGIN = 12f
private const val MAX_FIT_SCALE = 2f
private const val TERRAIN_REVIEW = "terrain-2026-10-06"
private const val SCENE_REVIEW = "scene-2026-10-06"
private const val ART_REVIEW = "art-2026-10-06"
private const val FIVE_FOOT_ROWS = 130
private const val FIVE_FOOT_TILE_MM = 1524.0
private val FARM_FOCUS = 32.0 to 59.0
private val FARM_PLAN = listOf(
    Triple("woodland_tree", 21, 51), Triple("woodland_tree", 26, 47), Triple("woodland_tree", 37, 52),
    Triple("hoop_house", 26, 52),
    Triple("commons_building", 16, 56),
    Triple("path", 31, 58), Triple("path", 31, 63),
    Triple("paddock", 36, 58), Triple("paddock", 36, 63),
    Triple("van_pad", 26, 62),
    Triple("pond", 41, 57),
)
private val SHOT_DIRECTORY =File(System.getProperty("WALKTHROUGH_DIR") ?: System.getenv("WALKTHROUGH_DIR") ?: "build/walkthrough")

@OptIn(ExperimentalTestApi::class)
class BuilderWalkthroughTest {

    private val spec = Json { ignoreUnknownKeys = true }
        .decodeFromString(Spec.serializer(), File("examples/world-builder-engine.spec.json").readText())

    private lateinit var host: WorldBuilderHost
    private lateinit var context: RenderContext

    private fun DesktopComposeUiTest.open(theme: String, world: String? = null, worldFile: File? = null, extra: Map<String, Any?> = emptyMap(), plan: (WorldBuilderHost) -> Unit = {}) {
        val presentation = loadPresentation("examples/parcel.presentation.json")
        host = when {
            worldFile != null -> WorldBuilderHost(openSession(worldFile.path), presentation)
            world != null -> WorldBuilderHost(openSession(File(designDirectory(), world).path), presentation)
            else -> parcelHost(presentation)
        }
        plan(host)
        var publish: () -> Unit = {}
        context = RenderContext(
            actions = host.actions { publish() },
            initialData = host.bindings() + mapOf("theme" to theme, "animate" to false, "brush" to host.initialBrush(), "rename_draft" to "") + extra,
        )
        publish = { context.applyBindings(host.bindings()) }
        val backdrop = if (theme == "dark") Color(0xFF14181F) else Color(0xFFF4F1EA)
        setContent { Box(Modifier.fillMaxSize().background(backdrop)) { RenderSpec(spec = spec, context = context) } }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.shot(name: String) {
        waitForIdle()
        SHOT_DIRECTORY.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", File(SHOT_DIRECTORY, "$name.png"))
    }

    private fun DesktopComposeUiTest.pick(use: String) {
        onNodeWithTag("world:brush:$use").performClick()
        waitForIdle()
    }

    private fun DesktopComposeUiTest.tapTile(col: Int, row: Int) {
        val size = onNodeWithTag("world:map").fetchSemanticsNode().size
        val bounds = tilemapScreenBounds(TileShape.SQUARE, TileView.ISO, 13, 26, TILE_WIDTH)
        val fit = fitFlowView(
            contentWidth = bounds.maxX - bounds.minX,
            contentHeight = bounds.maxY - bounds.minY + HEADROOM * TILE_WIDTH,
            viewWidth = size.width.toFloat(),
            viewHeight = size.height.toFloat(),
            maxScale = MAX_FIT_SCALE,
            margin = FIT_MARGIN,
        )
        val ground = project(TileView.ISO, tileCenter(TileShape.SQUARE, col, row), TILE_WIDTH)
        val x = fit.translateX + (ground.x - bounds.minX) * fit.scale
        val y = fit.translateY + (ground.y - bounds.minY + HEADROOM * TILE_WIDTH) * fit.scale
        onNodeWithTag("world:map").performTouchInput { click(Offset(x, y)) }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.tiles(use: String, vararg at: Pair<Int, Int>) {
        pick(use)
        at.forEach { (col, row) -> tapTile(col, row) }
    }

    @Test
    fun aFirstTimeLandownerWalksTheBuilderInDark() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("dark")
        shot("01-empty-dark")

        tapTile(5, 9)
        shot("02-first-tap-paddock-dark")

        tiles("paddock", 4 to 9, 6 to 9, 5 to 8, 5 to 10, 4 to 8, 6 to 8)
        tiles("path", 8 to 13, 8 to 14, 8 to 15)
        tiles("van_pad", 9 to 14)
        tiles("woodland_tree", 2 to 4, 3 to 5, 2 to 6, 11 to 20, 10 to 21)
        tiles("pond", 9 to 5, 9 to 6)
        tiles("commons_building", 3 to 15)
        tiles("hoop_house", 5 to 18)
        shot("03-several-uses-placed-dark")

        tiles("van_pad", 1 to 24)
        shot("04-refused-van-pad-dark")

        onNodeWithTag("btn-proposal").performClick()
        waitForIdle()
        tiles("pond", 10 to 9, 10 to 10)
        tiles("woodland_tree", 7 to 22)
        shot("05-proposal-with-diff-dark")

        onAllNodesWithTag("plan-pick")[0].performClick()
        shot("06-back-on-main-dark")
    }

    @Test
    fun theLidarSampleShowsItsTenTreesAtTheirMeasuredSpots() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("dark", "worlds/parcel-lidar-sample.world.json")
        shot("09-lidar-sample-dark")
        host.selectInstance("tree-01")
        context.applyBindings(host.bindings())
        shot("10-lidar-tree-record-dark")
    }

    private fun DesktopComposeUiTest.terrainShot(name: String, mode: String, isContoursShown: Boolean) {
        context.setBinding("terrain_mode", mode)
        context.setBinding("contours", isContoursShown)
        context.setBinding("contour_interval_mm", "50")
        waitForIdle()
        val directory = File(SHOT_DIRECTORY, TERRAIN_REVIEW)
        directory.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", File(directory, "$name.png"))
    }

    @Test
    fun theGroundDemoInEachTerrainModeAndAfterDiggingTheBasin() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("dark", "worlds/parcel-ground-demo.world.json")
        terrainShot("01-hillshade", "hillshade", isContoursShown = false)
        terrainShot("02-heat", "heat", isContoursShown = false)
        terrainShot("03-contours-over-hillshade", "hillshade", isContoursShown = true)
        listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1).forEach { (col, row) ->
            assertIs<DispatchResult.Accepted>(host.session.dispatch(WorldAction.Dig(col, row, 150)))
        }
        context.applyBindings(host.bindings())
        terrainShot("04-cutfill-after-basin-dig", "cutfill", isContoursShown = false)
    }

    @Test
    fun theGroundDemoAtTheLandingLevelIsTheVectorMapWithHillshade() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("dark", "worlds/parcel-ground-demo.world.json")
        onNodeWithText("off").assertExists()
        context.applyBindings(host.levelBindings(ViewState(null), LANDING_LEVEL_FEET, null))
        waitForIdle()
        onNodeWithTag("world:terrain-legend").assertExists()
        onNodeWithText("hillshade").assertExists()
        onNodeWithText("off").assertDoesNotExist()
        val directory = File(SHOT_DIRECTORY, SCENE_REVIEW)
        directory.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", File(directory, "01-vector-landing-hillshade.png"))
    }

    private fun DesktopComposeUiTest.artShot(name: String) {
        waitForIdle()
        val directory = File(SHOT_DIRECTORY, ART_REVIEW)
        directory.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", File(directory, "$name.png"))
    }

    private fun centredOn(groundX: Double, groundY: Double, rows: Int, tileMm: Double) =
        mapOf("centre_mm" to listOf(groundX * tileMm, (rows - groundY) * tileMm))

    private fun placeFarmPlan(host: WorldBuilderHost) {
        FARM_PLAN.forEach { (use, col, row) -> host.tap(col, row, use) }
        val placed = host.counts()
        FARM_PLAN.map { it.first }.distinct().forEach { use -> check((placed[use] ?: 0) > 0) { "$use was refused: $placed" } }
    }

    @Test
    fun theFiveAcrePlanInThePixelLook() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        val view = centredOn(FARM_FOCUS.first, FARM_FOCUS.second, FIVE_FOOT_ROWS, FIVE_FOOT_TILE_MM)
        open("dark", worldFile = File("examples/parcel-five-acre-5ft.world.json"), extra = mapOf("look" to "pixel", "view_state" to view), plan = ::placeFarmPlan)
        artShot("01-five-acre-plan-pixel")
    }

    @Test
    fun theLidarTreesInThePixelLook() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("dark", "worlds/parcel-lidar-sample.world.json", extra = mapOf("look" to "pixel"))
        artShot("02-lidar-trees-pixel")
    }

    @Test
    fun theSameFirstStepsInLightForComparison()= runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("light")
        shot("07-empty-light")
        tiles("paddock", 5 to 9, 6 to 9)
        tiles("woodland_tree", 2 to 4)
        tiles("commons_building", 3 to 15)
        shot("08-placed-light")
    }
}
