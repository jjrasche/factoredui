package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.DeviceProfile
import ai.factoredui.compose.scene.RendererCapability
import ai.factoredui.compose.scene.SceneLayerKind
import ai.factoredui.compose.scene.ViewState
import ai.factoredui.compose.scene.selectRenderer
import ai.factoredui.compose.schema.SpecNode
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

internal val LocalSceneRenderers = staticCompositionLocalOf { SceneRenderers(listOf(PixelMapRenderer)) }
