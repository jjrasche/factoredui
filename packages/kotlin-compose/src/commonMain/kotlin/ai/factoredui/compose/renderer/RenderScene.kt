package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.DeviceProfile
import ai.factoredui.compose.scene.LANDING_LEVEL_FEET
import ai.factoredui.compose.scene.resolveViewState
import ai.factoredui.compose.schema.SpecNode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PHONE_WIDTH_LIMIT = 600.dp

@Composable
internal fun RenderScene(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val view = resolveViewState(resolvedProps["view_state"])
    val levelFeet = view.levelFeet ?: LANDING_LEVEL_FEET
    val renderers = LocalSceneRenderers.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val profile = if (maxWidth < PHONE_WIDTH_LIMIT) DeviceProfile.PHONE else DeviceProfile.DESKTOP
        val renderer = renderers.select(profile, levelFeet, resolvedProps["renderer"] as? String ?: view.rendererId)
        if (renderer == null) {
            Box(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
                Text(
                    "No scene renderer supports $levelFeet ft per tile on $profile (registered: ${renderers.describe()}).",
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            CompositionLocalProvider(LocalDeviceProfile provides profile) {
                renderer.Draw(SceneFrame(node, resolvedProps, context, view, levelFeet, profile))
            }
        }
    }
}
