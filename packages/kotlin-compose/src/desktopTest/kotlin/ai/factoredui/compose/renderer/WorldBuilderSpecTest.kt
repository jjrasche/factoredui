package ai.factoredui.compose.renderer

import ai.factoredui.compose.render.jsonObjectToMap
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalTestApi::class)
class WorldBuilderSpecTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val spec = json.decodeFromString(Spec.serializer(), File("examples/world-builder.spec.json").readText())

    private fun hostData(shape: String? = null): Map<String, Any?> {
        val data = jsonObjectToMap(json.parseToJsonElement(File("examples/world-builder.data.json").readText()) as JsonObject)
        val still = data + ("animate" to false)
        return if (shape == null) still else still + ("shape" to shape)
    }

    private fun SpecVisualCheck.centreOfMap(): Pair<Float, Float> =
        region("world:map").let { ((it.right.value - it.left.value) / 2f) to ((it.bottom.value - it.top.value) / 2f) }

    @Test
    fun theExampleOpensWithEveryUseCountedAtZeroAndTheSevenBrushes() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(initialData = hostData()))
        check.render(spec.root, viewport = 1000.dp)
        assertEquals("Woodland tree: 0 trees, 0 sq ft", check.node("row-woodland_tree").props["value"])
        assertEquals("Pond: 0 tiles, 0 sq ft", check.node("row-pond").props["value"])
        listOf("paddock", "hoop_house", "commons_building", "van_pad", "path", "pond", "woodland_tree", "erase").forEach {
            check.assertPresent("world:brush:$it")
        }
    }

    @Test
    fun clickingTheMapPlacesTheBrushAndTheSidePanelReadsTheArea() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(initialData = hostData()))
        check.render(spec.root, viewport = 1000.dp)
        val (x, y) = check.centreOfMap()
        check.tapAt("world:map", x, y)
        assertEquals(1, (check.binding("counts") as Map<*, *>)["woodland_tree"])
        assertEquals("Woodland tree: 1 trees, 625 sq ft", check.node("row-woodland_tree").props["value"])
    }

    @Test
    fun switchingTheBrushThenClickingPlacesThatUse() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(initialData = hostData()))
        check.render(spec.root, viewport = 1000.dp)
        check.tap("world:brush:pond")
        val (x, y) = check.centreOfMap()
        check.tapAt("world:map", x, y)
        assertEquals("Pond: 1 tiles, 625 sq ft", check.node("row-pond").props["value"])
        assertEquals("Woodland tree: 0 trees, 0 sq ft", check.node("row-woodland_tree").props["value"])
    }

    @Test
    fun theHexShapeRendersAndPlacesToo() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(initialData = hostData(shape = "hex")))
        check.render(spec.root, viewport = 1000.dp)
        val (x, y) = check.centreOfMap()
        check.tapAt("world:map", x, y)
        assertTrue((check.binding("cells") as List<*>).size == 1, "a hex click places one tile")
    }

    @Test
    fun theExampleFollowsTheThemeValueAndTheHostCanFlipItLive() = runComposeUiTest {
        val context = RenderContext(initialData = hostData() + ("theme" to "light"))
        val check = SpecVisualCheck(this, context)
        check.render(spec.root, viewport = 1000.dp)
        val corner = { check.png().toPixelMap()[2, 2] }
        assertTrue(corner().red > 0.9f, "light by default: ${corner()}")
        context.setBinding("theme", "dark")
        waitForIdle()
        assertTrue(corner().red < 0.2f, "dark after the host flips the value: ${corner()}")
        context.setBinding("theme", "light")
        waitForIdle()
        assertTrue(corner().red > 0.9f, "and light again: ${corner()}")
    }
}
