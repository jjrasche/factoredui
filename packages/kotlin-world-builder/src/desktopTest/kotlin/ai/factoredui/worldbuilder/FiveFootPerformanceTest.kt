package ai.factoredui.worldbuilder

import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.schema.Spec
import ai.factoredui.worldengine.session.WorldSession
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

private const val TREE = "woodland_tree"
private const val TREE_SIDE_TILES = 5
private const val FRAME_SAMPLES = 15

private fun fiveFootWorld(): String = File("examples/parcel-five-acre-5ft.world.json").path

private fun quarterAcreWorld(): String = File(designDirectory(), "worlds/parcel-five-acre.world.json").path

private fun nowMillis(): Double = System.nanoTime() / 1_000_000.0

private inline fun elapsedMillis(block: () -> Unit): Double {
    val start = nowMillis()
    block()
    return nowMillis() - start
}

private fun List<Double>.median(): Double = sorted()[size / 2]

private fun List<Double>.p95(): Double = sorted()[(size * 95 / 100).coerceAtMost(size - 1)]

private fun List<Double>.summary(): String =
    "n=${size} median=${"%.1f".format(median())}ms p95=${"%.1f".format(p95())}ms max=${"%.1f".format(max())}ms"

private fun treeAnchors(session: WorldSession): List<Pair<Int, Int>> =
    (0 until session.world.rows / TREE_SIDE_TILES).flatMap { rowBlock ->
        (0 until session.world.cols / TREE_SIDE_TILES).map { colBlock -> colBlock * TREE_SIDE_TILES to rowBlock * TREE_SIDE_TILES }
    }

@OptIn(ExperimentalTestApi::class)
class FiveFootPerformanceTest {

    private val report = mutableListOf<String>()

    private val spec = Json { ignoreUnknownKeys = true }
        .decodeFromString(Spec.serializer(), File("examples/world-builder-engine.spec.json").readText())

    private fun note(line: String) {
        report.add(line)
        println(line)
    }

    private fun saveReport(name: String) {
        File("build").mkdirs()
        File("build/$name.txt").writeText(report.joinToString("\n"))
    }

    @Test
    fun theEngineKeepsUpWhileTheFiveFootBoardFillsWithTrees() {
        val host = WorldBuilderHost(openSession(fiveFootWorld()))
        val anchors = treeAnchors(host.session)
        val tapMillis = mutableListOf<Double>()
        val bindingsMillis = mutableListOf<Double>()
        anchors.forEach { (col, row) ->
            tapMillis.add(elapsedMillis { host.tap(col, row, TREE) })
            bindingsMillis.add(elapsedMillis { host.bindings() })
        }
        assertEquals(anchors.size * TREE_SIDE_TILES * TREE_SIDE_TILES, host.counts()[TREE])
        note("engine 5ft fill ${anchors.size} trees (${host.session.world.cols}x${host.session.world.rows} cells)")
        note("  tap whole fill: ${tapMillis.summary()}")
        note("  tap first 20: ${tapMillis.take(20).summary()}")
        note("  tap last 20: ${tapMillis.takeLast(20).summary()}")
        note("  bindings (render payload) whole fill: ${bindingsMillis.summary()}")
        note("  bindings last 20: ${bindingsMillis.takeLast(20).summary()}")
        val removeMillis = elapsedMillis { host.tap(0, 0, "erase") }
        val replaceMillis = elapsedMillis { host.tap(0, 0, TREE) }
        note("  at full board: remove ${"%.1f".format(removeMillis)}ms, place ${"%.1f".format(replaceMillis)}ms")
        saveReport("perf-5ft-engine")
    }

    @Test
    fun theQuarterAcreBoardForComparison() {
        val host = WorldBuilderHost(openSession(quarterAcreWorld()))
        val tapMillis = (0 until 12).flatMap { col -> (0 until 24).map { row -> col to row } }.take(100).map { (col, row) ->
            elapsedMillis { host.tap(col, row, TREE) }
        }
        note("engine 25ft ${host.session.world.cols}x${host.session.world.rows} cells, 100 trees: tap ${tapMillis.summary()}")
        saveReport("perf-25ft-engine")
    }

    private fun ComposeUiTest.open(worldPath: String, fillTrees: Boolean): WorldBuilderHost {
        val host = WorldBuilderHost(openSession(worldPath), loadPresentation("examples/parcel.presentation.json"))
        if (fillTrees) fillWithTrees(host)
        var publish: () -> Unit = {}
        val context = RenderContext(
            actions = host.actions { publish() },
            initialData = host.bindings() + mapOf("theme" to "dark", "animate" to false, "brush" to host.initialBrush(), "rename_draft" to ""),
        )
        publish = { context.applyBindings(host.bindings()) }
        setContent { Box(Modifier.size(1100.dp, 800.dp)) { RenderSpec(spec = spec, context = context) } }
        waitForIdle()
        return host
    }

    private fun ComposeUiTest.drawMillis(): Double = elapsedMillis { onNodeWithTag("world:map").captureToImage() }

    private fun ComposeUiTest.measureWindow(label: String, worldPath: String, fillTrees: Boolean) {
        val host = open(worldPath, fillTrees)
        val map = onNodeWithTag("world:map")
        val still = (1..FRAME_SAMPLES).map { drawMillis() }
        note("$label still frame: ${still.summary()}")

        val zoomIdle = mutableListOf<Double>()
        val zoomDraw = mutableListOf<Double>()
        map.performMouseInput { moveTo(center) }
        repeat(FRAME_SAMPLES) { step ->
            val delta = if (step % 2 == 0) -1f else 1f
            zoomIdle.add(elapsedMillis { map.performMouseInput { scroll(delta) }; waitForIdle() })
            zoomDraw.add(drawMillis())
        }
        note("$label zoom step (input + recompose): ${zoomIdle.summary()}; frame after: ${zoomDraw.summary()}")

        val panEvent = mutableListOf<Double>()
        map.performTouchInput { down(center) }
        repeat(FRAME_SAMPLES) { step ->
            val sign = if (step % 2 == 0) 1f else -1f
            panEvent.add(elapsedMillis { map.performTouchInput { moveBy(Offset(sign * 40f, sign * 15f)) }; waitForIdle() })
        }
        map.performTouchInput { up() }
        note("$label pan move event (input + recompose + draw): ${panEvent.summary()}")

        val hoverIdle = mutableListOf<Double>()
        val hoverDraw = mutableListOf<Double>()
        repeat(FRAME_SAMPLES) { step ->
            val spot = Offset(300f + step * 23f, 200f + step * 17f)
            hoverIdle.add(elapsedMillis { map.performMouseInput { moveTo(spot) }; waitForIdle() })
            hoverDraw.add(drawMillis())
        }
        note("$label hover move (input + recompose): ${hoverIdle.summary()}; frame after: ${hoverDraw.summary()}")

        val countBefore = host.counts().values.sum()
        val tapIdle = mutableListOf<Double>()
        repeat(FRAME_SAMPLES) { step ->
            val spot = Offset(250f + step * 31f, 150f + step * 29f)
            tapIdle.add(elapsedMillis { map.performTouchInput { click(spot) }; waitForIdle() })
        }
        note("$label tap through pick + engine + panel (click to idle): ${tapIdle.summary()}; engine counts $countBefore -> ${host.counts().values.sum()}")
    }

    private fun fillWithTrees(host: WorldBuilderHost) {
        treeAnchors(host.session).forEach { (col, row) -> host.tap(col, row, TREE) }
    }

    @Test
    fun theFiveFootWindowEmpty() = runComposeUiTest {
        measureWindow("window 5ft empty", fiveFootWorld(), fillTrees = false)
        saveReport("perf-5ft-window-empty")
    }

    @Test
    fun theFiveFootWindowFullOfTrees() = runComposeUiTest {
        measureWindow("window 5ft full", fiveFootWorld(), fillTrees = true)
        saveReport("perf-5ft-window-full")
    }

    @Test
    fun theQuarterAcreWindowForComparison() = runComposeUiTest {
        measureWindow("window 25ft empty", quarterAcreWorld(), fillTrees = false)
        saveReport("perf-25ft-window")
    }
}
