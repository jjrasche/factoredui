package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.share.ShareWriter
import ai.factoredui.compose.share.resolveShared
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

internal const val SHARE_WRITE_DELAY_MILLISECONDS = 250L

@Composable
internal fun ShareBindingsStart(spec: Spec, context: RenderContext) {
    remember(spec) {
        if (spec.share.isNotEmpty()) {
            val start = resolveShared(spec.share, context.shareHost)
            context.setBindings(start.values)
            showShareNotices(context, start.notices)
        }
    }
}

@Composable
internal fun ShareBindingsWrite(spec: Spec, context: RenderContext) {
    if (spec.share.isEmpty()) return
    LaunchedEffect(spec, context) {
        val writer = ShareWriter(spec.share, context.shareHost)
        context.dataFlow.collectLatest { data ->
            delay(SHARE_WRITE_DELAY_MILLISECONDS)
            showShareNotices(context, writer.onData(data))
        }
    }
}

private fun showShareNotices(context: RenderContext, notices: List<String>) {
    if (notices.isNotEmpty()) context.setBinding(RENDERER_ERROR_PATH, notices.joinToString("; "))
}
