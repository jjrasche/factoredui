package ai.factoredui.compose.renderer

import ai.factoredui.compose.media.VoiceMode
import ai.factoredui.compose.media.VoiceProps
import ai.factoredui.compose.media.readVoiceProps
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.bindingPath
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val TALK_BUTTON_HEIGHT = 96.dp

@Composable
internal fun RenderVoice(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = readVoiceProps(resolvedProps, node.props)
    val scope = rememberCoroutineScope()
    val registry = LocalScreenRegistry.current
    val identity = LocalNodeIdentity.current
    val paths = VoicePaths(node.props["listening"]?.bindingPath(), node.props["transcript"]?.bindingPath())
    val controller = remember(node.id, context.mediaHost, paths) {
        VoiceController(context.mediaHost.voice?.invoke(), context, node.id, scope, paths) { record, notices ->
            if (registry != null && identity != null) registry.update(identity.path) {
                voice = record
                this.notices = notices
            }
        }
    }
    SideEffect { controller.sync(props) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    VoiceSurface(node.id, props, controller)
}

private fun Modifier.talkGesture(mode: VoiceMode, controller: VoiceController): Modifier = pointerInput(mode, controller) {
    detectTapGestures(
        onPress = {
            if (mode == VoiceMode.HOLD) {
                controller.begin()
                tryAwaitRelease()
                controller.end()
            }
        },
        onTap = { if (mode == VoiceMode.TOGGLE) controller.toggle() },
    )
}

@Composable
private fun VoiceSurface(id: String, props: VoiceProps, controller: VoiceController) {
    val shown = controller.snapshot
    val ground = if (shown.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val ink = if (shown.isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = TALK_BUTTON_HEIGHT).clip(RoundedCornerShape(24.dp)).background(ground).then(if (controller.canListen) Modifier.talkGesture(props.mode, controller) else Modifier).nodeTag(id),
        ) {
            Text(if (shown.isListening) "Listening…" else props.label, style = MaterialTheme.typography.headlineSmall, color = ink)
        }
        if (!controller.canListen) Text("Voice is not available on this target.", style = MaterialTheme.typography.bodySmall)
        shown.transcript?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
    }
}
