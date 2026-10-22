package ai.factoredui.compose.renderer

import ai.factoredui.compose.media.AudioProps
import ai.factoredui.compose.media.formatElapsed
import ai.factoredui.compose.media.readAudioProps
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.bindingPath
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PLAYBACK_RATES = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
private val PLAY_BUTTON_HEIGHT = 64.dp

private fun nextRate(current: Double): Double = PLAYBACK_RATES.firstOrNull { it > current + 1e-9 } ?: PLAYBACK_RATES.first()

private fun rateText(rate: Double): String = "${rate.toString().removeSuffix(".0")}×"

@Composable
internal fun RenderAudio(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = readAudioProps(resolvedProps, node.props)
    val scope = rememberCoroutineScope()
    val registry = LocalScreenRegistry.current
    val identity = LocalNodeIdentity.current
    val paths = AudioPaths(node.props["position"]?.bindingPath(), node.props["playing"]?.bindingPath(), node.props["rate"]?.bindingPath(), node.props["duration"]?.bindingPath())
    val controller = remember(node.id, context.mediaHost, paths) {
        AudioController(context.mediaHost.audio?.invoke(), context, node.id, scope, paths) { record, notices ->
            if (registry != null && identity != null) registry.update(identity.path) {
                audio = record
                this.notices = notices
            }
        }
    }
    val wanted = AudioWanted((resolvedProps["position"] as? Number)?.toDouble(), resolvedProps["playing"] as? Boolean, (resolvedProps["rate"] as? Number)?.toDouble())
    SideEffect { controller.sync(props, wanted) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    AudioSurface(node.id, props, controller)
}

@Composable
private fun AudioSurface(id: String, props: AudioProps, controller: AudioController) {
    val shown = controller.snapshot
    Column(modifier = Modifier.fillMaxWidth().nodeTag(id), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        props.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        when {
            !controller.canPlay -> Text("Audio is not available on this target.", style = MaterialTheme.typography.bodySmall)
            props.hasControls -> AudioControls(id, props, controller, shown)
        }
        if (shown.status == "blocked") Text("Tap play to start.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AudioControls(id: String, props: AudioProps, controller: AudioController, shown: AudioSnapshot) {
    val step = props.seekStepSeconds
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { controller.skip(-step) }, modifier = Modifier.nodeTag("$id/back")) { Text("−${step.toInt()}") }
        Button(onClick = { controller.togglePlay() }, modifier = Modifier.heightIn(min = PLAY_BUTTON_HEIGHT).nodeTag("$id/play")) {
            Text(if (shown.isPlaying) "Pause" else "Play", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedButton(onClick = { controller.skip(step) }, modifier = Modifier.nodeTag("$id/forward")) { Text("+${step.toInt()}") }
    }
    AudioSeekBar(id, controller, shown)
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("${formatElapsed(shown.position)} / ${formatElapsed(shown.duration ?: Double.NaN)}", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { controller.setRateTo(nextRate(shown.rate)) }, modifier = Modifier.nodeTag("$id/rate")) { Text(rateText(shown.rate)) }
    }
}

@Composable
private fun AudioSeekBar(id: String, controller: AudioController, shown: AudioSnapshot) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val length = (shown.duration ?: 0.0).toFloat()
    Slider(
        value = (dragged ?: shown.position.toFloat()).coerceIn(0f, maxOf(length, 0.001f)),
        onValueChange = { dragged = it },
        onValueChangeFinished = {
            dragged?.let { controller.seekToSeconds(it.toDouble()) }
            dragged = null
        },
        valueRange = 0f..maxOf(length, 0.001f),
        enabled = length > 0f,
        modifier = Modifier.fillMaxWidth().nodeTag("$id/seek"),
    )
}
