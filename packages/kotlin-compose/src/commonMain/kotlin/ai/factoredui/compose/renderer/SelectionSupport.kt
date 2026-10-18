package ai.factoredui.compose.renderer

import ai.factoredui.compose.detail.SelectionDetail
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.resolveGeomapSelection
import ai.factoredui.compose.schema.selectionAfterTap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The ids selected in a drawing and the way to change them: a tap on [tapped] selects it, a tap on it again or on nothing clears. */
internal class DrawingSelection(val ids: Set<String>, val tap: (String?) -> Unit)

/** The selection lives in the bound path of `selected` when there is one, so two drawings and any card bound to it agree; otherwise in the drawing. */
@Composable
internal fun rememberDrawingSelection(node: SpecNode, resolvedSelected: Any?, context: RenderContext): DrawingSelection {
    val path = node.props["selected"]?.bindingPath()
    var local by remember { mutableStateOf(resolveGeomapSelection(resolvedSelected)) }
    val ids = if (path != null) resolveGeomapSelection(resolvedSelected) else local
    return DrawingSelection(ids) { tapped ->
        val next = selectionAfterTap(ids, tapped)
        if (path != null) context.setBinding(path, next) else local = next.toSet()
    }
}

/** A tap picks only what the host described when it gave `details`; with none given every drawn thing can be picked. */
internal fun isPickable(id: String, details: Map<String, SelectionDetail>) = details.isEmpty() || id in details

@Composable
internal fun SelectionDetailCard(nodeId: String, selection: Set<String>, details: Map<String, SelectionDetail>) {
    val detail = selection.singleOrNull()?.let(details::get) ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).nodeTag("$nodeId/detail"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            detail.sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    section.heading?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LocalSpecTheme.current.muted) }
                    section.rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(row.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(row.value ?: "–", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        row.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalSpecTheme.current.muted) }
                    }
                }
            }
        }
    }
}
