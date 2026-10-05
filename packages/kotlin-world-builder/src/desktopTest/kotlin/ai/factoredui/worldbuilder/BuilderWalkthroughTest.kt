package ai.factoredui.worldbuilder

import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.fitFlowView
import ai.factoredui.compose.layout.project
import ai.factoredui.compose.layout.tileCenter
import ai.factoredui.compose.layout.tilemapScreenBounds
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.serialization.json.Json

private const val WINDOW_WIDTH_PX = 1500
private const val WINDOW_HEIGHT_PX = 900
private const val TILE_WIDTH = 64f
private const val HEADROOM = 0.95f
private const val FIT_MARGIN = 12f
private const val MAX_FIT_SCALE = 2f
private val SHOT_DIRECTORY = File(System.getProperty("WALKTHROUGH_DIR") ?: System.getenv("WALKTHROUGH_DIR") ?: "build/walkthrough")

@OptIn(ExperimentalTestApi::class)
class BuilderWalkthroughTest {

    private val spec = Json { ignoreUnknownKeys = true }
        .decodeFromString(Spec.serializer(), File("examples/world-builder-engine.spec.json").readText())

    private lateinit var host: WorldBuilderHost
    private lateinit var context: RenderContext

    private fun DesktopComposeUiTest.open(theme: String, world: String? = null) {
        val presentation = loadPresentation("examples/parcel.presentation.json")
        host = if (world == null) parcelHost(presentation) else WorldBuilderHost(openSession(File(designDirectory(), world).path), presentation)
        var publish: () -> Unit = {}
        context = RenderContext(
            actions = host.actions { publish() },
            initialData = host.bindings() + mapOf("theme" to theme, "animate" to false, "brush" to host.initialBrush()),
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

        onNodeWithTag("btn-branch").performClick()
        shot("06-back-on-main-dark")
    }

    @Test
    fun theLidarSampleShowsItsTenTreesAtTheirMeasuredSpots() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("dark", "worlds/parcel-lidar-sample.world.json")
        shot("09-lidar-sample-dark")
    }

    @Test
    fun theSameFirstStepsInLightForComparison() = runDesktopComposeUiTest(WINDOW_WIDTH_PX, WINDOW_HEIGHT_PX) {
        open("light")
        shot("07-empty-light")
        tiles("paddock", 5 to 9, 6 to 9)
        tiles("woodland_tree", 2 to 4)
        tiles("commons_building", 3 to 15)
        shot("08-placed-light")
    }
}
