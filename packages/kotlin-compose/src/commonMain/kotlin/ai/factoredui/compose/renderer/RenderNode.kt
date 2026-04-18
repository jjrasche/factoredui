package ai.factoredui.compose.renderer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.asButtonProps
import ai.factoredui.compose.schema.asGridProps
import ai.factoredui.compose.schema.asIconProps
import ai.factoredui.compose.schema.asImageProps
import ai.factoredui.compose.schema.asLayoutProps
import ai.factoredui.compose.schema.asListProps
import ai.factoredui.compose.schema.asSpacerProps
import ai.factoredui.compose.schema.asTextProps
import ai.factoredui.compose.schema.ButtonVariant

/**
 * Top-level spec renderer entry point.
 *
 * Renders a spec root node into Compose UI. Mirrors renderSpec() in
 * packages/react/src/sdui/renderer.tsx — same recursive pattern, same
 * visibility/binding/action resolution steps.
 */
@Composable
fun RenderSpec(root: SpecNode, context: RenderContext) {
    RenderNode(node = root, context = context)
}

/**
 * Recursive node renderer.
 *
 * Orchestrator role: resolves visibility, fires observability, delegates to
 * the leaf composable that handles each SpecNodeType.
 */
@Composable
fun RenderNode(node: SpecNode, context: RenderContext) {
    val isVisible = BindingResolver.isVisible(node.visible, context.data)
    if (!isVisible) return

    // Fire render observability hook — mirrors the path-context wrapping in the React renderer
    LaunchedEffect(node.id) {
        context.observability.onRender(node.id)
    }

    val resolvedProps = remember(node.props, context.data) {
        BindingResolver.resolveProps(node.props, context.data)
    }

    RenderNodeByType(node = node, resolvedProps = resolvedProps, context = context)
}

/**
 * Dispatch to the correct leaf composable based on node type.
 * Unrecognised types are silently skipped — matches JS renderer behaviour.
 */
@Composable
private fun RenderNodeByType(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    when (node.type) {
        SpecNodeType.COLUMN -> RenderColumn(node, context)
        SpecNodeType.ROW -> RenderRow(node, context)
        SpecNodeType.STACK -> RenderStack(node, context)
        SpecNodeType.SCROLLVIEW -> RenderScrollView(node, context)
        SpecNodeType.GRID -> RenderGrid(node, context)
        SpecNodeType.TEXT -> RenderText(node, resolvedProps)
        SpecNodeType.BUTTON -> RenderButton(node, resolvedProps, context)
        SpecNodeType.IMAGE -> RenderImage(node, resolvedProps)
        SpecNodeType.ICON -> RenderIcon(node, resolvedProps)
        SpecNodeType.DIVIDER -> RenderDivider(node)
        SpecNodeType.SPACER -> RenderSpacer(node)
        SpecNodeType.LIST -> RenderList(node, context)
        SpecNodeType.CARD -> RenderCard(node, context)
        SpecNodeType.TEXTINPUT -> RenderTextInput(node, resolvedProps, context)
        SpecNodeType.CHIP -> RenderChip(node, resolvedProps, context)
        // TABS, MODAL, TOGGLE, SELECT, SLIDER — stubs; extend in Milestone 2
        SpecNodeType.TABS,
        SpecNodeType.MODAL,
        SpecNodeType.TOGGLE,
        SpecNodeType.SELECT,
        SpecNodeType.SLIDER -> RenderStub(node)
    }
}

// --- Container nodes ---

@Composable
private fun RenderColumn(node: SpecNode, context: RenderContext) {
    val props = node.props.asLayoutProps()
    Column(
        modifier = Modifier.padding(props.padding.dp),
        verticalArrangement = Arrangement.spacedBy(props.gap.dp),
    ) {
        node.children.forEach { child -> RenderNode(node = child, context = context) }
    }
}

@Composable
private fun RenderRow(node: SpecNode, context: RenderContext) {
    val props = node.props.asLayoutProps()
    Row(
        modifier = Modifier.padding(props.padding.dp),
        horizontalArrangement = Arrangement.spacedBy(props.gap.dp),
    ) {
        node.children.forEach { child -> RenderNode(node = child, context = context) }
    }
}

@Composable
private fun RenderStack(node: SpecNode, context: RenderContext) {
    Box {
        node.children.forEach { child -> RenderNode(node = child, context = context) }
    }
}

@Composable
private fun RenderScrollView(node: SpecNode, context: RenderContext) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        node.children.forEach { child -> RenderNode(node = child, context = context) }
    }
}

@Composable
private fun RenderGrid(node: SpecNode, context: RenderContext) {
    val props = node.props.asGridProps()
    // Simple wrapping row — a proper LazyVerticalGrid requires androidMain; stub here works for commonTest
    Column {
        node.children.chunked(props.columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(props.gap.dp)) {
                rowItems.forEach { child ->
                    Box(modifier = Modifier.weight(1f)) {
                        RenderNode(node = child, context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderCard(node: SpecNode, context: RenderContext) {
    Card(modifier = Modifier.fillMaxWidth()) {
        node.children.forEach { child -> RenderNode(node = child, context = context) }
    }
}

@Composable
private fun RenderList(node: SpecNode, context: RenderContext) {
    val props = node.props.asListProps()
    val rawData = context.data[props.data]
    val items = when (rawData) {
        is List<*> -> rawData.filterNotNull()
        else -> emptyList<Any>()
    }

    if (items.isEmpty() && props.emptyText.isNotEmpty()) {
        Text(text = props.emptyText, style = MaterialTheme.typography.bodyMedium)
        return
    }

    LazyColumn {
        items(items) { item ->
            val itemTemplate = props.itemTemplate ?: return@items
            // Merge item data into context so bindings within the template can reach it
            val itemContext = context.copy(
                data = context.data + mapOf("item" to item),
            )
            RenderNode(node = itemTemplate, context = itemContext)
        }
    }
}

// --- Leaf nodes ---

@Composable
private fun RenderText(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val props = resolvedProps.asResolvedTextProps()
    val style = when (props.variant) {
        "heading" -> MaterialTheme.typography.headlineMedium
        "subheading" -> MaterialTheme.typography.titleMedium
        "caption" -> MaterialTheme.typography.bodySmall
        "label" -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.bodyMedium
    }
    Text(
        text = props.value,
        style = style.copy(
            fontWeight = if (props.bold) FontWeight.Bold else null,
            color = props.colorValue ?: Color.Unspecified,
        ),
        textAlign = when (props.align) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.End
            else -> TextAlign.Start
        },
        maxLines = props.numberOfLines ?: Int.MAX_VALUE,
        modifier = Modifier.semantics { contentDescription = node.id },
    )
}

@Composable
private fun RenderButton(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asButtonProps()
    val label = (resolvedProps["label"] as? String) ?: props.label
    val isDisabled = (resolvedProps["disabled"] as? Boolean) ?: props.disabled
    val scope = rememberCoroutineScope()

    val onClickHandler: () -> Unit = {
        node.action?.let { actionRef ->
            scope.launch { context.dispatch(node.id, actionRef) }
        }
    }

    when (props.variant) {
        ButtonVariant.OUTLINE -> OutlinedButton(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = Modifier.semantics { contentDescription = node.id },
        ) { Text(label) }

        ButtonVariant.GHOST -> TextButton(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = Modifier.semantics { contentDescription = node.id },
        ) { Text(label) }

        ButtonVariant.DESTRUCTIVE -> Button(
            onClick = onClickHandler,
            enabled = !isDisabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.semantics { contentDescription = node.id },
        ) { Text(label) }

        else -> Button(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = Modifier.semantics { contentDescription = node.id },
        ) { Text(label) }
    }
}

@Composable
private fun RenderImage(node: SpecNode, resolvedProps: Map<String, Any?>) {
    // Image loading requires a platform-specific loader (Coil, etc.).
    // Stub with a placeholder Box — replace in Milestone 2 with expect/actual.
    val alt = (resolvedProps["alt"] as? String) ?: ""
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = alt.ifEmpty { node.id } },
    )
}

@Composable
private fun RenderIcon(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val name = (resolvedProps["name"] as? String) ?: ""
    // Icon resolution is font/library-specific; render accessible placeholder
    Text(
        text = name,
        modifier = Modifier.semantics { contentDescription = node.id },
    )
}

@Composable
private fun RenderDivider(node: SpecNode) {
    Divider(modifier = Modifier.semantics { contentDescription = node.id })
}

@Composable
private fun RenderSpacer(node: SpecNode) {
    val props = node.props.asSpacerProps()
    if (props.size > 0) {
        Spacer(modifier = Modifier.height(props.size.dp))
    } else {
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RenderTextInput(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val placeholder = (resolvedProps["placeholder"] as? String) ?: ""
    // Full stateful TextInput deferred to Milestone 2 (needs host state hoisting)
    androidx.compose.material3.OutlinedTextField(
        value = "",
        onValueChange = {},
        placeholder = { Text(placeholder) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = node.id },
    )
}

@Composable
private fun RenderChip(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val label = (resolvedProps["label"] as? String) ?: ""
    val scope = rememberCoroutineScope()
    androidx.compose.material3.FilterChip(
        selected = (resolvedProps["selected"] as? Boolean) ?: false,
        onClick = {
            node.action?.let { actionRef ->
                scope.launch { context.dispatch(node.id, actionRef) }
            }
        },
        label = { Text(label) },
        modifier = Modifier.semantics { contentDescription = node.id },
    )
}

/** Placeholder for unimplemented node types — renders nothing but stays structurally present. */
@Composable
private fun RenderStub(node: SpecNode) {
    // TODO(milestone-2): implement TABS, MODAL, TOGGLE, SELECT, SLIDER
    Box(modifier = Modifier.semantics { contentDescription = "stub:${node.type.name.lowercase()}" })
}

// --- Helper: read resolved (Any?) props into typed data classes ---

private data class ResolvedTextProps(
    val value: String,
    val variant: String,
    val align: String,
    val colorValue: Color?,
    val bold: Boolean,
    val numberOfLines: Int?,
)

private fun Map<String, Any?>.asResolvedTextProps(): ResolvedTextProps = ResolvedTextProps(
    value = (get("value") as? String) ?: "",
    variant = (get("variant") as? String) ?: "body",
    align = (get("align") as? String) ?: "left",
    colorValue = (get("color") as? String)?.parseColor(),
    bold = (get("bold") as? Boolean) ?: false,
    numberOfLines = (get("numberOfLines") as? Double)?.toInt(),
)

private fun String.parseColor(): Color? = runCatching {
    val hex = removePrefix("#")
    when (hex.length) {
        6 -> Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f,
        )
        8 -> Color(
            alpha = hex.substring(0, 2).toInt(16) / 255f,
            red = hex.substring(2, 4).toInt(16) / 255f,
            green = hex.substring(4, 6).toInt(16) / 255f,
            blue = hex.substring(6, 8).toInt(16) / 255f,
        )
        else -> null
    }
}.getOrNull()
