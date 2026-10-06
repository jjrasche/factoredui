package ai.factoredui.compose.renderer

import ai.factoredui.compose.scene.DeviceProfile
import ai.factoredui.compose.scene.LANDING_LEVEL_FEET
import ai.factoredui.compose.scene.nearestLadderLevel
import ai.factoredui.compose.scene.resolveViewState
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.resolveTileArea
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

private val PHONE_WIDTH_LIMIT = 600.dp

@Composable
internal fun RenderScene(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val view = resolveViewState(resolvedProps["view_state"])
    val levelFeet = view.levelFeet ?: defaultLevelFeet(node.type, resolveTileArea(resolvedProps["tile_area"]))
    val renderers = LocalSceneRenderers.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val profile = if (maxWidth < PHONE_WIDTH_LIMIT) DeviceProfile.PHONE else DeviceProfile.DESKTOP
        val renderer = renderers.select(profile, levelFeet, view.rendererId)
        if (renderer == null) {
            Box(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
                Text(
                    "No scene renderer supports $levelFeet ft per tile on $profile (registered: ${renderers.describe()}).",
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            renderer.Draw(SceneFrame(node, resolvedProps, context, view, levelFeet, profile))
        }
    }
}

private fun defaultLevelFeet(type: SpecNodeType, tileArea: Double): Int =
    if (type == SpecNodeType.TILEMAP) nearestLadderLevel(sqrt(tileArea)) else LANDING_LEVEL_FEET
