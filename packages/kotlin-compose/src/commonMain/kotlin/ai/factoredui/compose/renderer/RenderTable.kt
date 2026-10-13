package ai.factoredui.compose.renderer

import ai.factoredui.compose.format.MISSING_FIGURE
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.DisplayProfile
import ai.factoredui.compose.schema.ShownColumn
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.TableLayout
import ai.factoredui.compose.schema.readColumnOverrides
import ai.factoredui.compose.schema.mergedProfile
import ai.factoredui.compose.schema.readDisplayProfile
import ai.factoredui.compose.schema.readTableView
import ai.factoredui.compose.schema.tableLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalDisplayProfile = compositionLocalOf<DisplayProfile?> { null }

private val PROFILE_SCOPES = setOf(
    SpecNodeType.COLUMN, SpecNodeType.ROW, SpecNodeType.STACK, SpecNodeType.SCROLLVIEW, SpecNodeType.GRID,
    SpecNodeType.CARD, SpecNodeType.LIST, SpecNodeType.TABS, SpecNodeType.MODAL,
)

/** The display profile a container's descendant tables inherit: its own `display` when it has one, else its ancestors'. */
internal fun scopedProfileOf(node: SpecNode, resolvedProps: Map<String, Any?>): DisplayProfile? {
    if (node.type !in PROFILE_SCOPES) return null
    return (resolvedProps["display"] as? Map<*, *>)?.let(::readDisplayProfile)
}

private const val LABEL_COLUMN = "label"
private const val LABEL_FLEX = 2.0
private const val VALUE_FLEX = 1.0
private const val ROW_GAP = 8.0
private const val ROW_PADDING = 4.0
private val NOTICE_INK = Color(0xFFB3261E)

private fun specValueOf(raw: Any?): SpecValue = when (raw) {
    null -> SpecValue.NullValue
    is String -> SpecValue.StringValue(raw)
    is Boolean -> SpecValue.BooleanValue(raw)
    is Number -> SpecValue.NumberValue(raw.toDouble())
    is List<*> -> SpecValue.ArrayValue(raw.map(::specValueOf))
    is Map<*, *> -> SpecValue.ObjectValue(raw.entries.associate { it.key.toString() to specValueOf(it.value) })
    else -> SpecValue.StringValue(raw.toString())
}

private fun actionRefOf(raw: SpecValue?): ActionRef? {
    val fields = (raw as? SpecValue.ObjectValue)?.value ?: return null
    val action = (fields["action"] as? SpecValue.StringValue)?.value ?: return null
    return ActionRef(action, (fields["params"] as? SpecValue.ObjectValue)?.value.orEmpty())
}

private fun cellNode(id: String, value: String, slot: Pair<String, SpecValue>, align: String, extra: Map<String, SpecValue> = emptyMap()) = SpecNode(
    id = id,
    type = SpecNodeType.TEXT,
    props = mapOf("value" to SpecValue.StringValue(value), slot, "align" to SpecValue.StringValue(align)) + extra,
)

private val LABEL_SLOT = "flex" to SpecValue.NumberValue(LABEL_FLEX)

/** A column with a width keeps that many dp; the others share what is left. */
private fun slotOf(column: ShownColumn): Pair<String, SpecValue> =
    column.width?.let { "width" to SpecValue.NumberValue(it) } ?: ("flex" to SpecValue.NumberValue(VALUE_FLEX))

private fun rowLayoutProps(): Map<String, SpecValue> = mapOf("gap" to SpecValue.NumberValue(ROW_GAP), "padding" to SpecValue.NumberValue(ROW_PADDING))

private fun headerNode(columns: List<ShownColumn>, showLabel: Boolean, labelHeader: String): SpecNode {
    val bold = mapOf("bold" to SpecValue.BooleanValue(true))
    val label = listOfNotNull(cellNode("head/$LABEL_COLUMN", labelHeader, LABEL_SLOT, "left", bold).takeIf { showLabel })
    val cells = columns.map { cellNode("head/${it.id}", it.header, slotOf(it), it.align, bold) }
    return SpecNode(id = "head", type = SpecNodeType.ROW, props = rowLayoutProps(), children = label + cells)
}

private fun valueCell(index: Int, column: ShownColumn): SpecNode {
    val format = column.format?.let { mapOf("format" to specValueOf(it)) }.orEmpty()
    return cellNode("$index/${column.id}", "{row.values.${column.id}}", slotOf(column), column.align, format + ("missing" to SpecValue.StringValue(column.missing)))
}

private fun rowNode(index: Int, columns: List<ShownColumn>, showLabel: Boolean, rowTap: ActionRef?): SpecNode {
    val label = listOfNotNull(cellNode("$index/$LABEL_COLUMN", "{row.label}", LABEL_SLOT, "left").takeIf { showLabel })
    return SpecNode(id = "$index", type = SpecNodeType.ROW, props = rowLayoutProps(), children = label + columns.map { valueCell(index, it) }, action = rowTap)
}

private fun titleOf(raw: Any?, viewLabel: String): String? = when (raw) {
    false -> null
    is String -> raw
    else -> viewLabel
}

@Composable
private fun TableNotice(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = NOTICE_INK)
}

@Composable
private fun TableRow(path: String, row: SpecNode, rowData: Map<String, Any?>, context: RenderContext) {
    CompositionLocalProvider(LocalPathPrefix provides "$path/row/") {
        RenderNode(node = row, context = context.withAdditionalData(mapOf("row" to rowData, "item" to rowData)))
    }
}

/** A view drawn as a header and one row per view row, each a row of text cells formatted by the display profile. */
@Composable
internal fun RenderTable(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val view = readTableView(resolvedProps["view"])
    if (view == null) {
        RecordDrawn { notices = listOf("table '${node.id}' has no view: bind view to the engine's view object") }
        Column(modifier = Modifier.nodeTag(node.id)) { TableNotice("table '${node.id}' has no view: bind view to the engine's view object") }
        return
    }
    val layout: TableLayout = tableLayout(
        view,
        mergedProfile(LocalDisplayProfile.current, (resolvedProps["display"] as? Map<*, *>)?.let(::readDisplayProfile)),
        readColumnOverrides(resolvedProps["columns"]),
        resolvedProps["show_unit"] != false,
        resolvedProps["missing"] as? String ?: MISSING_FIGURE,
    )
    val showLabel = resolvedProps["show_label"] != false
    val title = titleOf(resolvedProps["title"], view.label)
    val emptyText = (resolvedProps["empty_text"] as? String).orEmpty()
    val rowTap = actionRefOf(node.props["on_row_tap"])
    val path = LocalNodeIdentity.current?.path ?: node.id
    val header = headerNode(layout.columns, showLabel, (resolvedProps["label_header"] as? String).orEmpty())
    val rows = view.rows.indices.map { rowNode(it, layout.columns, showLabel, rowTap) }
    val isShowingEmpty = rows.isEmpty() && emptyText.isNotEmpty()
    RecordDrawn {
        text = title
        notices = listOfNotNull(layout.notice)
        syntheticChildren = listOf("$path/" to header) + rows.map { "$path/row/" to it }
        emptyMessage = if (isShowingEmpty) emptyText else null
        if (!isShowingEmpty) emptyBounds = null
    }
    BoxWithConstraints(modifier = Modifier.nodeTag(node.id)) {
        val isBounded = constraints.hasBoundedHeight
        Column {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp)) }
            layout.notice?.let { TableNotice(it) }
            CompositionLocalProvider(LocalPathPrefix provides "$path/") { RenderNode(node = header, context = context) }
            when {
                isShowingEmpty -> Text(emptyText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.emptyMessagePlacement())
                isBounded -> LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(rows) { index, row -> TableRow(path, row, view.rows[index], context) }
                }
                else -> rows.forEachIndexed { index, row -> TableRow(path, row, view.rows[index], context) }
            }
        }
    }
}
