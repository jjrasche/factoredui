package ai.factoredui.compose.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.asModalProps
import ai.factoredui.compose.schema.asSelectProps
import ai.factoredui.compose.schema.asSliderProps
import ai.factoredui.compose.schema.asTabsProps
import ai.factoredui.compose.schema.asToggleProps
import ai.factoredui.compose.schema.bindingPath

/**
 * Form-input + selection-container primitives.
 *
 * All five share the same shape as RenderTextInput: read the raw binding path
 * from node.props for write-back, read the resolved current value from
 * resolvedProps for display, and call context.setBinding(path, value) on
 * change. node.action, when present, fires after the binding write.
 */

@Composable
internal fun RenderToggle(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val props = node.props.asToggleProps()
    val writePath = node.props["value"]?.bindingPath()
    val current = (resolvedProps["value"] as? Boolean) ?: false
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = node.id },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (props.label.isNotEmpty()) {
            Text(text = props.label, modifier = Modifier.weight(1f))
        } else {
            Box(modifier = Modifier.weight(1f))
        }
        Switch(
            checked = current,
            onCheckedChange = { next ->
                writePath?.let { context.setBinding(it, next) }
                node.action?.let { ref -> scope.launch { context.dispatch(node.id, ref) } }
            },
        )
    }
}

@Composable
internal fun RenderSlider(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val props = node.props.asSliderProps()
    val writePath = node.props["value"]?.bindingPath()
    val current = when (val raw = resolvedProps["value"]) {
        is Number -> raw.toFloat()
        is String -> raw.toFloatOrNull() ?: props.min
        else -> props.min
    }.coerceIn(props.min, props.max)
    val scope = rememberCoroutineScope()

    val steps = props.step?.let { step ->
        if (step > 0f) (((props.max - props.min) / step).toInt() - 1).coerceAtLeast(0) else 0
    } ?: 0

    Slider(
        value = current,
        onValueChange = { next ->
            writePath?.let { context.setBinding(it, next.toDouble()) }
        },
        onValueChangeFinished = {
            node.action?.let { ref -> scope.launch { context.dispatch(node.id, ref) } }
        },
        valueRange = props.min..props.max,
        steps = steps,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = node.id },
    )
}

@Composable
internal fun RenderSelect(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val props = node.props.asSelectProps()
    val writePath = node.props["value"]?.bindingPath()
    val currentValue = (resolvedProps["value"] as? String) ?: ""
    val currentLabel = props.options.firstOrNull { it.value == currentValue }?.label
        ?: props.placeholder.ifEmpty { "Select…" }
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = node.id },
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(currentLabel) }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            props.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        writePath?.let { context.setBinding(it, option.value) }
                        node.action?.let { ref -> scope.launch { context.dispatch(node.id, ref) } }
                    },
                )
            }
        }
    }
}

@Composable
internal fun RenderTabs(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val props = node.props.asTabsProps()
    val writePath = node.props["selectedIndex"]?.bindingPath()
    val rawSelected = (resolvedProps["selectedIndex"] as? Number)?.toInt() ?: 0
    val selected = rawSelected.coerceIn(0, (props.items.size - 1).coerceAtLeast(0))

    if (props.items.isEmpty()) return

    Column(modifier = Modifier.semantics { contentDescription = node.id }) {
        TabRow(selectedTabIndex = selected) {
            props.items.forEachIndexed { index, label ->
                Tab(
                    selected = index == selected,
                    onClick = {
                        if (writePath != null) context.setBinding(writePath, index)
                    },
                    text = { Text(label) },
                )
            }
        }
        // Convention: one child per tab item. Mismatches render nothing.
        node.children.getOrNull(selected)?.let { child ->
            Box(modifier = Modifier.padding(top = 8.dp)) {
                RenderNode(node = child, context = context)
            }
        }
    }
}

@Composable
internal fun RenderModal(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val props = node.props.asModalProps()
    val title = (resolvedProps["title"] as? String) ?: props.title
    val scope = rememberCoroutineScope()

    val onDismiss: () -> Unit = {
        node.action?.let { ref -> scope.launch { context.dispatch(node.id, ref) } }
    }

    AlertDialog(
        onDismissRequest = if (props.dismissible) onDismiss else ({}),
        title = if (title.isNotEmpty()) ({ Text(title) }) else null,
        text = {
            Column { node.children.forEach { child -> RenderNode(node = child, context = context) } }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = props.dismissible,
            ) { Text("Close") }
        },
        modifier = Modifier.semantics { contentDescription = node.id },
    )
}
