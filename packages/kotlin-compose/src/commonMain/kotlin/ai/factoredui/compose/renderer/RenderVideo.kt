package ai.factoredui.compose.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.VideoProps
import ai.factoredui.compose.schema.bindingPath

@Composable
internal fun RenderVideo(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = resolvedProps.asResolvedVideoProps()
    val positionPath = node.props["position"]?.bindingPath()
    VideoSurface(
        props = props,
        onPositionChange = { positionMs ->
            positionPath?.let { path -> context.setBinding(path, positionMs) }
        },
    )
}

internal fun Map<String, Any?>.asResolvedVideoProps(): VideoProps = VideoProps(
    source = (this["source"] as? String) ?: "",
    autoplay = (this["autoplay"] as? Boolean) ?: false,
    loop = (this["loop"] as? Boolean) ?: false,
    position = (this["position"] as? Number)?.toDouble(),
    aspectRatio = (this["aspectRatio"] as? Number)?.toFloat(),
)

@Composable
internal expect fun VideoSurface(props: VideoProps, onPositionChange: (Double) -> Unit)

@Composable
internal fun UnsupportedVideoNotice(props: VideoProps, target: String) {
    Box(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF2B2B30)).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Video unsupported on this target ($target)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE6E6EC),
                textAlign = TextAlign.Center,
            )
            Text(
                text = props.source.ifEmpty { "video node has no source" },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA8D0),
                textAlign = TextAlign.Center,
            )
        }
    }
}
