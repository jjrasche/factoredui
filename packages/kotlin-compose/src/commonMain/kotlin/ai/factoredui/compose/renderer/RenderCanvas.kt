package ai.factoredui.compose.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.asCanvasProps

private const val NODE_CENTER_DP = 20f
private val EDGE_COLOR = Color(0xFF8A94AD)

@Composable
fun RenderCanvas(node: SpecNode, context: RenderContext) {
    val liveData by context.dataFlow.collectAsState()
    val positions = childPositions(node, liveData)
    val edges = node.props.asCanvasProps().edges
    Box(modifier = Modifier.fillMaxSize().nodeTag(node.id)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (edge in edges) {
                val from = positions[edge.from] ?: continue
                val to = positions[edge.to] ?: continue
                drawLine(
                    color = EDGE_COLOR,
                    start = edgeEndpoint(from, density),
                    end = edgeEndpoint(to, density),
                    strokeWidth = 2f,
                )
            }
        }
        for (child in node.children) {
            val position = positions[child.id] ?: Offset(0f, 0f)
            Box(modifier = Modifier.offset(position.x.dp, position.y.dp)) {
                RenderNode(child, context)
            }
        }
    }
}

private fun childPositions(node: SpecNode, liveData: Map<String, Any?>): Map<String, Offset> =
    node.children.associate { child ->
        val resolved = BindingResolver.resolveProps(child.props, liveData)
        child.id to Offset(
            (resolved["x"] as? Number)?.toFloat() ?: 0f,
            (resolved["y"] as? Number)?.toFloat() ?: 0f,
        )
    }

private fun edgeEndpoint(nodeCorner: Offset, density: Float): Offset =
    Offset((nodeCorner.x + NODE_CENTER_DP) * density, (nodeCorner.y + NODE_CENTER_DP) * density)
