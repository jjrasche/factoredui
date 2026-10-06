package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.DeviceProfile
import ai.factoredui.compose.scene.RendererCapability
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.TileSprite
import ai.factoredui.compose.schema.TilemapUse
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class SceneNodeCheck {

    private fun sceneOf(type: SpecNodeType, tileArea: Double = 625.0, withViewState: Boolean = false) = SpecNode(
        id = "world",
        type = type,
        props = buildMap {
            put("cols", SpecValue.NumberValue(5.0))
            put("rows", SpecValue.NumberValue(4.0))
            put("tile_area", SpecValue.NumberValue(tileArea))
            put("uses", SpecValue.StringValue("{uses}"))
            if (withViewState) put("view_state", SpecValue.StringValue("{view}"))
        },
    )

    private val uses = listOf(mapOf("id" to "path", "label" to "Path", "color" to "#D2B48C", "sprite" to "flat"))

    private fun contextOf(view: Map<String, Any?> = emptyMap()) = RenderContext(initialData = mapOf("uses" to uses, "view" to view))

    @Test
    fun theTilemapAliasLandsAtItsOwnTileScaleAndDrawsThePixelMap() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(sceneOf(SpecNodeType.TILEMAP), viewport = 400.dp)
        check.assertPresent("world:map")
    }

    @Test
    fun aSceneNodeAskedForTheTileScaleDrawsThePixelMap() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(mapOf("level_feet" to 25)))
        check.render(sceneOf(SpecNodeType.SCENE, withViewState = true), viewport = 400.dp)
        check.assertPresent("world:map")
    }

    @Test
    fun aBareSceneNodeLandsAtTheVectorLevelAndDrawsTheVectorMap() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(sceneOf(SpecNodeType.SCENE), viewport = 400.dp)
        check.assertPresent("world:map")
    }

    @Test
    fun aSceneNodeAtALevelNoRendererServesSaysSoInsteadOfDrawing() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(mapOf("level_feet" to 3)))
        check.render(sceneOf(SpecNodeType.SCENE, withViewState = true), viewport = 400.dp)
        check.assertPresent("world")
        onNodeWithTag("world:map").assertDoesNotExist()
    }

    @Test
    fun theVectorRendererPutsTheMapTopDownWithFlatUsesAndHillshadeByDefault() {
        val props = vectorPropsOf(mapOf("view" to "iso", "terrain_mode" to null))
        assertEquals("top", props["view"])
        assertEquals(VECTOR_LOOK, props["look"])
        assertEquals("hillshade", props["terrain_mode"])
        assertEquals("heat", vectorPropsOf(mapOf("terrain_mode" to "heat"))["terrain_mode"])
    }

    @Test
    fun aFlattenedUseKeepsItsIdentityAndColourButLosesItsSpriteAndPicture() {
        val use = TilemapUse("barn", "Barn", "#AA5533", TileSprite.BLOCK, 2f, critter = "cow", image = "barn.png")
        assertEquals(TilemapUse("barn", "Barn", "#AA5533", TileSprite.FLAT, 2f), flattenedUses(listOf(use)).single())
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
