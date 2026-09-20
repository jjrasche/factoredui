package ai.factoredui.compose.renderer

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ai.factoredui.compose.schema.VideoProps
import java.io.File

private const val DEFAULT_ASPECT_RATIO = 16f / 9f

@Composable
internal actual fun VideoSurface(props: VideoProps, onPositionChange: (Double) -> Unit) {
    if (props.source.isEmpty()) {
        UnsupportedVideoNotice(props, "android")
        return
    }
    val modifier = Modifier.fillMaxWidth().aspectRatio(props.aspectRatio ?: DEFAULT_ASPECT_RATIO)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                setVideoURI(videoUriOf(props.source))
                setOnPreparedListener { player ->
                    player.isLooping = props.loop
                    if (props.autoplay) start()
                }
                setOnCompletionListener { onPositionChange(duration.toDouble()) }
            }
        },
        update = { view ->
            props.position?.let { requested ->
                if (kotlin.math.abs(view.currentPosition - requested) > 250.0) {
                    view.seekTo(requested.toInt())
                }
            }
        },
    )
    DisposableEffect(props.source) { onDispose { } }
}

private fun videoUriOf(source: String): Uri =
    if (source.startsWith("http") || source.startsWith("content://")) Uri.parse(source)
    else Uri.fromFile(File(source))
