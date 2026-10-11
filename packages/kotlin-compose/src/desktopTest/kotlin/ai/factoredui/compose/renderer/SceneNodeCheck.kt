package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.DeviceProfile
import ai.factoredui.compose.scene.RendererCapability
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class SceneNodeCheck {

    private val scene = SpecNode(
        id = "world",
        type = SpecNodeType.SCENE,
        props = mapOf("view_state" to SpecValue.StringValue("{view}")),
    )

    @Test
    fun aSceneNodeAtALevelNoRendererServesSaysSoInsteadOfDrawing() = runComposeUiTest {
        val check = SpecVisualCheck(this, RenderContext(initialData = mapOf("view" to mapOf("level_feet" to 3))))
        check.render(scene, viewport = 400.dp)
        check.assertPresent("world")
        onNodeWithText("No scene renderer supports 3 ft per tile", substring = true).assertExists()
    }

    @Test
    fun theRegistryHandsBackTheRendererItsSelectionChose() {
        val vector = FakeRenderer("vector-map", setOf(25, 625))
        val pixel = FakeRenderer("pixel-map", setOf(25))
        val renderers = SceneRenderers(listOf(pixel, vector))
        assertSame(vector, renderers.select(DeviceProfile.PHONE, 625, null))
        assertSame(vector, renderers.select(DeviceProfile.PHONE, 25, "vector-map"))
        assertSame(pixel, renderers.select(DeviceProfile.PHONE, 25, null))
        assertNull(renderers.select(DeviceProfile.PHONE, 5, null))
        assertEquals("pixel-map, vector-map", renderers.describe())
    }

    private class FakeRenderer(id: String, levels: Set<Int>) : SceneRenderer {
        override val capability = RendererCapability(id, emptySet(), levels)

        @Composable
        override fun Draw(frame: SceneFrame) = Unit
    }
}
