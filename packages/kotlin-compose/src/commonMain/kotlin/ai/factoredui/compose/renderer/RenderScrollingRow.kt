package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun SpecNode.scrollsToARow(): Boolean = props["scroll_to"] != null && props["itemTemplate"] is SpecValue.NodeValue

@Composable
internal fun ScrollingRowOfData(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext, gapDp: Int, paddingDp: Int) {
    val template = (node.props["itemTemplate"] as? SpecValue.NodeValue)?.value ?: return
    val rows = (resolvedProps["data"] as? List<*>)?.filterNotNull().orEmpty()
    val index = (resolvedProps["scroll_to"] as? Number)?.toInt()
    val listPath = LocalNodeIdentity.current?.path ?: node.id
    val state = rememberLazyListState()
    LaunchedEffect(index, rows.size) {
        if (index != null && index in rows.indices) state.animateScrollToItem(index)
    }
    LazyRow(state = state, userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(gapDp.dp), modifier = Modifier.nodeTag(node.id).padding(paddingDp.dp)) {
        itemsIndexed(rows) { position, row ->
            CompositionLocalProvider(LocalPathPrefix provides "$listPath/$position/") {
                RenderNode(node = template, context = context.withAdditionalData(mapOf("row" to row, "item" to row)))
            }
        }
    }
}
