package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.DeviceProfile
import ai.factoredui.compose.scene.RendererCapability
import ai.factoredui.compose.scene.SceneLayerKind
import ai.factoredui.compose.scene.ViewState
import ai.factoredui.compose.scene.selectRenderer
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.TileSprite
import ai.factoredui.compose.schema.TilemapUse
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal class SceneFrame(
    val node: SpecNode,
    val resolvedProps: Map<String, Any?>,
    val context: RenderContext,
    val view: ViewState,
    val levelFeet: Int,
    val profile: DeviceProfile,
)

internal interface SceneRenderer {
    val capability: RendererCapability

    @Composable
    fun Draw(frame: SceneFrame)
}

internal class SceneRenderers(private val registered: List<SceneRenderer>) {
    fun select(profile: DeviceProfile, levelFeet: Int, preferredId: String?): SceneRenderer? {
        val chosen = selectRenderer(registered.map { it.capability }, profile, levelFeet, preferredId) ?: return null
        return registered.first { it.capability.id == chosen.id }
    }

    fun describe(): String = registered.joinToString { it.capability.id }
}

internal object PixelMapRenderer : SceneRenderer {
    override val capability = RendererCapability(
        id = "pixel-map",
        layers = setOf(SceneLayerKind.GROUND, SceneLayerKind.FOOTPRINTS, SceneLayerKind.INSTANCES, SceneLayerKind.GRID),
        levelsFeet = setOf(1, 5, 25, 125),
    )

    @Composable
    override fun Draw(frame: SceneFrame) = RenderTilemap(frame.node, frame.resolvedProps, frame.context)
}

internal const val VECTOR_LOOK = "vector"

internal object VectorMapRenderer : SceneRenderer {
    override val capability = RendererCapability(
        id = "vector-map",
        layers = setOf(SceneLayerKind.GROUND, SceneLayerKind.FOOTPRINTS, SceneLayerKind.INSTANCES, SceneLayerKind.GRID),
        levelsFeet = setOf(25, 125, 625),
    )

    @Composable
    override fun Draw(frame: SceneFrame) = RenderTilemap(frame.node, vectorPropsOf(frame.resolvedProps), frame.context)
}

internal fun vectorPropsOf(resolvedProps: Map<String, Any?>): Map<String, Any?> {
    val terrainMode = resolvedProps["terrain_mode"] ?: "hillshade"
    return resolvedProps + mapOf("view" to "top", "look" to VECTOR_LOOK, "terrain_mode" to terrainMode)
}

internal fun flattenedUses(uses: List<TilemapUse>): List<TilemapUse> =
    uses.map { it.copy(sprite = TileSprite.FLAT, image = null, critter = null) }

internal val LocalDeviceProfile = staticCompositionLocalOf { DeviceProfile.DESKTOP }

internal val LocalSceneRenderers = staticCompositionLocalOf { SceneRenderers(listOf(PixelMapRenderer, VectorMapRenderer)) }
