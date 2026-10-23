package ai.factoredui.compose.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.StackAnchor
import ai.factoredui.compose.schema.asStackPlacement

@Composable
internal fun BoxScope.RenderStackChild(child: SpecNode, context: RenderContext) {
    val placement = child.props.asStackPlacement()
    when (placement.anchor) {
        StackAnchor.FILL -> Box(modifier = Modifier.fillMaxSize()) { RenderSizedChild(child, context) }
        StackAnchor.TOP -> Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) { RenderSizedChild(child, context) }
        StackAnchor.BOTTOM -> Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { RenderSizedChild(child, context) }
        StackAnchor.END -> Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()) { RenderSizedChild(child, context) }
        null -> RenderSizedChild(child, context)
    }
}
