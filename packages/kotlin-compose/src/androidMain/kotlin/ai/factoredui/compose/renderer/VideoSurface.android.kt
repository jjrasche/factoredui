package ai.factoredui.compose.renderer

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ai.factoredui.compose.schema.VideoProps
import java.io.File

private const val DEFAULT_ASPECT_RATIO = 16f / 9f
private const val POSITION_POLL_MILLIS = 200L
private const val SEEK_TOLERANCE_MILLIS = 250.0

@Composable
internal actual fun VideoSurface(props: VideoProps, onPositionChange: (Double) -> Unit) {
    if (props.source.isEmpty()) {
        UnsupportedVideoNotice(props, "android")
        return
    }
    var player by remember { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(player, props.source) {
        val view = player ?: return@LaunchedEffect
        while (true) {
            if (view.isPlaying) onPositionChange(view.currentPosition.toDouble())
            delay(POSITION_POLL_MILLIS)
        }
    }
    val modifier = Modifier.fillMaxWidth().aspectRatio(props.aspectRatio ?: DEFAULT_ASPECT_RATIO)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).also { player = it }.apply {
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
                if (kotlin.math.abs(view.currentPosition - requested) > SEEK_TOLERANCE_MILLIS) {
                    view.seekTo(requested.toInt())
                }
            }
        },
    )
    DisposableEffect(props.source) { onDispose { player = null } }
}

private fun videoUriOf(source: String): Uri =
    if (source.startsWith("http") || source.startsWith("content://")) Uri.parse(source)
    else Uri.fromFile(File(source))
