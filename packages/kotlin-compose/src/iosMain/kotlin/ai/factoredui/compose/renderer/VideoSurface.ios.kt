package ai.factoredui.compose.renderer

import androidx.compose.runtime.Composable
import ai.factoredui.compose.schema.VideoProps

@Composable
internal actual fun VideoSurface(props: VideoProps, onPositionChange: (Double) -> Unit) {
    UnsupportedVideoNotice(props, "iOS")
}
