package ai.factoredui.compose.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ai.factoredui.compose.adapter.HostDataSource
import ai.factoredui.compose.forcegraph.RenderForceGraph
import ai.factoredui.compose.scene3d.RenderScene3d
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.ImageFit
import ai.factoredui.compose.schema.LayoutAlign
import ai.factoredui.compose.schema.LayoutJustify
import ai.factoredui.compose.schema.ListProps
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.asForceGraphProps
import ai.factoredui.compose.schema.asScene3dProps
// BindingResolver used to support {ref} in list `data` prop (nested lists).
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
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.ButtonVariant
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.svg.SvgDecoder

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
 * Renders a full [Spec]: its root tree plus any spec-level keyboard shortcuts.
 * When [Spec.keybindings] is empty this is just the root render — no focus
 * machinery is installed.
 */
@Composable
fun RenderSpec(spec: Spec, context: RenderContext) {
    if (spec.keybindings.isEmpty()) {
        RenderNode(node = spec.root, context = context)
    } else {
        KeybindingHost(keybindings = spec.keybindings, context = context) {
            RenderNode(node = spec.root, context = context)
        }
    }
}

/**
 * Hot-swapping entry point: renders whatever [Spec] the host currently holds,
 * recomposing in place when [specFlow] emits a new one — no navigation boundary.
 *
 * This is how an experiment variant swap (or any live spec update) reaches the
 * screen: the host pushes the new spec through the flow and the tree
 * re-renders against the same [context] / data store. Use a [StateFlow] so
 * there is always a current spec to show (a cold source can `.stateIn(...)`).
 */
@Composable
fun RenderSpec(specFlow: StateFlow<Spec>, context: RenderContext) {
    val spec by specFlow.collectAsState()
    RenderSpec(spec = spec, context = context)
}

/**
 * Recursive node renderer.
 *
 * Orchestrator role: resolves visibility, fires observability, delegates to
 * the leaf composable that handles each SpecNodeType.
 */
@Composable
fun RenderNode(node: SpecNode, context: RenderContext) {
    // Subscribe to reactive data so binding updates drive recomposition
    val liveData by context.dataFlow.collectAsState()
    val isVisible = BindingResolver.isVisible(node.visible, liveData)
    if (!isVisible) return

    // Fire render observability hook — mirrors the path-context wrapping in the React renderer
    LaunchedEffect(node.id) {
        context.observability.onRender(node.id)
    }

    val resolvedProps = remember(node.props, liveData) {
        BindingResolver.resolveProps(node.props, liveData)
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
        SpecNodeType.CARD -> RenderCard(node, resolvedProps, context)
        SpecNodeType.TEXTINPUT -> RenderTextInput(node, resolvedProps, context)
        SpecNodeType.CHIP -> RenderChip(node, resolvedProps, context)
        SpecNodeType.FORCE_GRAPH -> RenderForceGraph(node.props.asForceGraphProps())
        SpecNodeType.SCENE3D -> {
            val sceneProps = node.props.asScene3dProps()
            val liveFrame = (resolvedProps["frame"] as? Number)?.toFloat() ?: sceneProps.clipFrameFraction
            val liveImpulse = (resolvedProps["impulse"] as? Number)?.toFloat() ?: sceneProps.clipImpulse
            val liveAutoplay = (resolvedProps["autoplay"] as? Boolean) ?: sceneProps.clipAutoplay
            val liveSeverity = (resolvedProps["severity"] as? Number)?.toFloat() ?: sceneProps.clipSeverity
            val liveEffector = (resolvedProps["effector"] as? String) ?: sceneProps.clipEffector
            val liveEngine = (resolvedProps["engine"] as? String) ?: sceneProps.engine
            val livePlayhead = (resolvedProps["playhead"] as? Number)?.toInt()
            val livePlaying = resolvedProps["playing"] as? Boolean
            val liveBody = resolvedProps["body"] as? Map<*, *>
            val playheadWritePath = node.props["playhead"]?.bindingPath()
            val sceneScope = rememberCoroutineScope()
            val intentDispatch = sceneIntentDispatcher(context, node.id)
            RenderScene3d(
                sceneProps.copy(
                    clipFrameFraction = liveFrame, clipImpulse = liveImpulse, clipAutoplay = liveAutoplay,
                    clipSeverity = liveSeverity, clipEffector = liveEffector, engine = liveEngine,
                ),
                node.id, context.observability,
                playheadBinding = livePlayhead,
                playingBinding = livePlaying,
                onPlayheadChange = { next -> playheadWritePath?.let { context.setBinding(it, next) } },
                chrome = sceneProps.chrome,
                liveBody = liveBody,
                onIntent = { action, params -> sceneScope.launch { intentDispatch(action, params) } },
            )
        }
        SpecNodeType.CANVAS -> RenderCanvas(node, context)
        SpecNodeType.TOGGLE -> RenderToggle(node, resolvedProps, context)
        SpecNodeType.SLIDER -> RenderSlider(node, resolvedProps, context)
        SpecNodeType.SELECT -> RenderSelect(node, resolvedProps, context)
        SpecNodeType.TABS -> RenderTabs(node, resolvedProps, context)
        SpecNodeType.MODAL -> RenderModal(node, resolvedProps, context)
    }
}

// --- Container nodes ---

@Composable
private fun RenderColumn(node: SpecNode, context: RenderContext) {
    val props = node.props.asLayoutProps()
    val hasFlexChild = node.children.any { it.props.asLayoutProps().flex > 0f }
    val fillWidth = hasFlexChild || props.align != LayoutAlign.START
    val colModifier = Modifier.padding(props.padding.dp)
        .let { if (fillWidth) it.fillMaxWidth() else it }
        .let { if (hasFlexChild) it.fillMaxHeight() else it }
    Column(
        modifier = colModifier,
        verticalArrangement = verticalArrangement(props.justify, props.gap),
        horizontalAlignment = columnHorizontalAlignment(props.align),
    ) {
        node.children.forEach { child ->
            val childFlex = child.props.asLayoutProps().flex
            when {
                childFlex > 0f -> Box(Modifier.weight(childFlex)) { RenderNode(node = child, context = context) }
                props.align == LayoutAlign.STRETCH -> Box(Modifier.fillMaxWidth()) { RenderNode(node = child, context = context) }
                else -> RenderNode(node = child, context = context)
            }
        }
    }
}

@Composable
private fun RenderRow(node: SpecNode, context: RenderContext) {
    val props = node.props.asLayoutProps()
    val hasFlexChild = node.children.any { it.props.asLayoutProps().flex > 0f }
    val fillWidth = hasFlexChild || props.justify != LayoutJustify.START
    val rowModifier = Modifier.padding(props.padding.dp)
        .let { if (fillWidth) it.fillMaxWidth() else it }
    Row(
        modifier = rowModifier,
        horizontalArrangement = horizontalArrangement(props.justify, props.gap),
        verticalAlignment = rowVerticalAlignment(props.align),
    ) {
        node.children.forEach { child ->
            val childFlex = child.props.asLayoutProps().flex
            if (childFlex > 0f) {
                Box(modifier = Modifier.weight(childFlex)) { RenderNode(node = child, context = context) }
            } else {
                RenderNode(node = child, context = context)
            }
        }
    }
}

internal fun Modifier.nodeTag(id: String): Modifier =
    this.testTag(id).semantics { contentDescription = id }

private fun columnHorizontalAlignment(align: LayoutAlign): Alignment.Horizontal = when (align) {
    LayoutAlign.CENTER -> Alignment.CenterHorizontally
    LayoutAlign.END -> Alignment.End
    else -> Alignment.Start
}

private fun rowVerticalAlignment(align: LayoutAlign): Alignment.Vertical = when (align) {
    LayoutAlign.CENTER -> Alignment.CenterVertically
    LayoutAlign.END -> Alignment.Bottom
    else -> Alignment.Top
}

private fun verticalArrangement(justify: LayoutJustify, gap: Int): Arrangement.Vertical = when (justify) {
    LayoutJustify.CENTER -> Arrangement.spacedBy(gap.dp, Alignment.CenterVertically)
    LayoutJustify.END -> Arrangement.spacedBy(gap.dp, Alignment.Bottom)
    LayoutJustify.BETWEEN -> Arrangement.SpaceBetween
    LayoutJustify.AROUND -> Arrangement.SpaceAround
    else -> Arrangement.spacedBy(gap.dp, Alignment.Top)
}

private fun horizontalArrangement(justify: LayoutJustify, gap: Int): Arrangement.Horizontal = when (justify) {
    LayoutJustify.CENTER -> Arrangement.spacedBy(gap.dp, Alignment.CenterHorizontally)
    LayoutJustify.END -> Arrangement.spacedBy(gap.dp, Alignment.End)
    LayoutJustify.BETWEEN -> Arrangement.SpaceBetween
    LayoutJustify.AROUND -> Arrangement.SpaceAround
    else -> Arrangement.spacedBy(gap.dp, Alignment.Start)
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
    if (props.lazy) {
        RenderLazyGrid(node, context, props)
    } else {
        RenderChunkedGrid(node, context, props)
    }
}

/**
 * Default grid renderer: composes every child up front in chunked rows.
 * Works inside any container, including unbounded scrollviews. Right
 * choice for small item counts (dashboards, controls). Use `lazy: true`
 * for large data sets where windowed rendering matters.
 */
@Composable
private fun RenderChunkedGrid(
    node: SpecNode,
    context: RenderContext,
    props: ai.factoredui.compose.schema.GridProps,
) {
    Column(verticalArrangement = Arrangement.spacedBy(props.gap.dp)) {
        node.children.chunked(props.columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(props.gap.dp)) {
                rowItems.forEach { child ->
                    Box(modifier = Modifier.weight(1f)) {
                        RenderNode(node = child, context = context)
                    }
                }
                // Pad the trailing row so cells stay column-aligned.
                repeat(props.columns - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RenderLazyGrid(
    node: SpecNode,
    context: RenderContext,
    props: ai.factoredui.compose.schema.GridProps,
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(props.columns),
        verticalArrangement = Arrangement.spacedBy(props.gap.dp),
        horizontalArrangement = Arrangement.spacedBy(props.gap.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        node.children.forEach { child ->
            item(key = child.id) {
                RenderNode(node = child, context = context)
            }
        }
    }
}

@Composable
private fun RenderCard(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = resolvedProps.asResolvedCardProps()
    val fraction = props.maxWidthFraction
    if (fraction != null) {
        BoxWithConstraints {
            StyledCard(node, props, context, Modifier.widthIn(max = maxWidth * fraction))
        }
    } else {
        StyledCard(node, props, context, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StyledCard(
    node: SpecNode,
    props: ResolvedCardProps,
    context: RenderContext,
    widthModifier: Modifier,
) {
    val shape = props.cornerRadius?.let { RoundedCornerShape(it.dp) } ?: CardDefaults.shape
    val colors = props.background
        ?.let { CardDefaults.cardColors(containerColor = it) }
        ?: CardDefaults.cardColors()
    val elevation = if (props.background != null) {
        CardDefaults.cardElevation(defaultElevation = 0.dp)
    } else {
        CardDefaults.cardElevation()
    }
    Card(modifier = widthModifier.nodeTag(node.id), shape = shape, colors = colors, elevation = elevation) {
        val contentModifier = props.padding?.let { Modifier.padding(it.dp) } ?: Modifier
        Column(modifier = contentModifier) {
            node.children.forEach { child -> RenderNode(node = child, context = context) }
        }
    }
}

@Composable
private fun RenderList(node: SpecNode, context: RenderContext) {
    val props = node.props.asListProps()
    val host = context.hostDataSource
    val query = props.dataSource
    // Live path: a `data_source` query plus a host that can serve it. The host
    // streams result sets and we re-render on every emission. Falls back to the
    // static `data` key whenever either piece is absent, so existing specs are
    // unaffected.
    if (host != null && !query.isNullOrEmpty()) {
        RenderLiveList(props, context, host, query)
    } else {
        RenderStaticList(props, context)
    }
}

/**
 * `data_source`-bound list. Subscribes to the host's live query and re-renders
 * the row template on every emission. `remember(query)` keeps the subscription
 * stable across recompositions; `collectAsState` cancels it when the list
 * leaves composition (releasing any host-side watch via the Flow's `awaitClose`).
 */
@Composable
private fun RenderLiveList(
    props: ListProps,
    context: RenderContext,
    host: HostDataSource,
    query: String,
) {
    val resultsFlow = remember(query) { host.subscribe(query) }
    val rows by resultsFlow.collectAsState(initial = emptyList())
    RenderRows(rows, props, context)
}

/**
 * Static list. `data` may be a plain key ("threads") for a top-level binding OR
 * a binding ref ("{row.branches}" / "{item.branches}") so nested lists iterate
 * over a field of the enclosing row's overlay.
 */
@Composable
private fun RenderStaticList(props: ListProps, context: RenderContext) {
    val liveData by context.dataFlow.collectAsState()
    val rawData = when {
        props.data.isEmpty() -> null
        BindingResolver.isBindingRef(props.data) -> BindingResolver.resolveBinding(props.data, liveData)
        else -> liveData[props.data]
    }
    val rows: List<Any?> = (rawData as? List<*>) ?: emptyList<Any?>()
    RenderRows(rows, props, context)
}

/**
 * Shared row renderer for both the live and static list paths. Caps to
 * `maxItems`, shows `emptyText` when there is nothing to render, and scopes
 * each row so the template's bindings + action params resolve against it.
 */
@Composable
private fun RenderRows(rows: List<Any?>, props: ListProps, context: RenderContext) {
    val visibleRows = rows.filterNotNull().let { all ->
        val cap = props.maxItems
        if (cap != null && all.size > cap) all.take(cap) else all
    }

    if (visibleRows.isEmpty() && props.emptyText.isNotEmpty()) {
        Text(text = props.emptyText, style = MaterialTheme.typography.bodyMedium)
        return
    }

    val itemTemplate = props.itemTemplate ?: return
    LazyColumn {
        items(visibleRows) { row ->
            // Scope the row under "row" (the requested "{row.x}" syntax) and
            // "item" (legacy syntax used by existing static specs + nested
            // lists). Both keys point at the same row, so "{row.id}" and
            // "{item.id}" resolve identically — and because the template renders
            // with this scoped context, action param interpolation in dispatch()
            // picks up the same row overlay for free.
            val rowContext = context.withAdditionalData(mapOf("row" to row, "item" to row))
            RenderNode(node = itemTemplate, context = rowContext)
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
        modifier = Modifier.nodeTag(node.id),
    )
}

@Composable
private fun RenderButton(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asButtonProps()
    val label = (resolvedProps["label"] as? String) ?: props.label
    val icon = (resolvedProps["icon"] as? String) ?: props.icon
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
            modifier = Modifier.nodeTag(node.id),
        ) { ButtonContent(icon, label) }

        ButtonVariant.GHOST -> TextButton(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = Modifier.nodeTag(node.id),
        ) { ButtonContent(icon, label) }

        ButtonVariant.DESTRUCTIVE -> Button(
            onClick = onClickHandler,
            enabled = !isDisabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.nodeTag(node.id),
        ) { ButtonContent(icon, label) }

        else -> Button(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = Modifier.nodeTag(node.id),
        ) { ButtonContent(icon, label) }
    }
}

@Composable
private fun RenderImage(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val props = node.props.asImageProps()
    val source = (resolvedProps["source"] as? String) ?: props.source
    val alt = (resolvedProps["alt"] as? String) ?: props.alt
    val cornerRadius = (resolvedProps["cornerRadius"] as? Double)?.toInt() ?: 0

    val contentScale = when (props.fit) {
        ImageFit.COVER -> ContentScale.Crop
        ImageFit.CONTAIN -> ContentScale.Fit
        ImageFit.FILL -> ContentScale.FillBounds
    }

    val shape = RoundedCornerShape(cornerRadius.dp)
    val fallbackTint = MaterialTheme.colorScheme.surfaceVariant

    val aspectRatio = props.aspectRatio
    val maxHeight = (resolvedProps["maxHeight"] as? Double)?.toInt()
    val sizeModifier = when {
        maxHeight != null && aspectRatio != null -> Modifier.height(maxHeight.dp).aspectRatio(aspectRatio)
        aspectRatio != null -> Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        else -> Modifier.fillMaxWidth().height(RenderDefaults.IMAGE_FALLBACK_HEIGHT)
    }
    val baseModifier = sizeModifier
        .clip(shape)
        .testTag(node.id)
        .semantics { contentDescription = alt.ifEmpty { node.id } }

    if (source.isEmpty()) {
        Box(modifier = baseModifier.then(Modifier.background(fallbackTint)))
        return
    }

    AsyncImage(
        model = source,
        contentDescription = alt.ifEmpty { node.id },
        contentScale = contentScale,
        modifier = baseModifier.then(Modifier.background(fallbackTint)),
    )
}

@Composable
private fun RenderIcon(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val name = (resolvedProps["name"] as? String) ?: ""
    val sizeDp = ((resolvedProps["size"] as? Number)?.toInt() ?: 24).dp
    if (name.isEmpty()) return
    IconifyImage(
        name = name,
        sizeDp = sizeDp,
        contentDescription = node.id,
        modifier = Modifier.nodeTag(node.id),
    )
}

@Composable
private fun IconifyImage(name: String, sizeDp: Dp, contentDescription: String, modifier: Modifier = Modifier) {
    val (prefix, iconName) = parseIconifyName(name)
    val url = "https://api.iconify.design/$prefix/$iconName.svg"
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) {
        ImageLoader.Builder(platformContext)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    AsyncImage(
        model = url,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier.size(sizeDp),
    )
}

@Composable
private fun ButtonContent(icon: String?, label: String) {
    if (!icon.isNullOrEmpty()) {
        IconifyImage(name = icon, sizeDp = 20.dp, contentDescription = label.ifEmpty { "icon" })
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    } else {
        Text(label)
    }
}

/**
 * Parse an Iconify icon name. Accepts `lucide:settings` (set:name) or just
 * `settings` (defaults to lucide). Iconify hosts ~150 icon sets behind a
 * single CDN — host apps don't need to bundle any icon font.
 */
private fun parseIconifyName(name: String): Pair<String, String> {
    val colon = name.indexOf(':')
    return if (colon > 0 && colon < name.length - 1) {
        name.substring(0, colon) to name.substring(colon + 1)
    } else {
        "lucide" to name
    }
}

@Composable
private fun RenderDivider(node: SpecNode) {
    Divider(modifier = Modifier.nodeTag(node.id))
}

@Composable
private fun RenderSpacer(node: SpecNode) {
    val props = node.props.asSpacerProps()
    val height = if (props.size > 0) props.size.dp else RenderDefaults.SPACER_HEIGHT
    Spacer(modifier = Modifier.height(height))
}

@Composable
private fun RenderTextInput(
    node: SpecNode,
    resolvedProps: Map<String, Any?>,
    context: RenderContext,
) {
    val placeholder = (resolvedProps["placeholder"] as? String) ?: ""
    val multiline = (resolvedProps["multiline"] as? Boolean) ?: false
    val maxLength = (resolvedProps["maxLength"] as? Double)?.toInt()

    // The `value` prop is typically a binding ref like "{shell.composeText}".
    // We read the raw (unresolved) prop to extract the binding path for write-back,
    // and the resolved value for the current display text.
    val writePath = node.props["value"]?.bindingPath()
    val currentValue = (resolvedProps["value"] as? String) ?: ""

    androidx.compose.material3.OutlinedTextField(
        value = currentValue,
        onValueChange = { next ->
            val capped = if (maxLength != null && next.length > maxLength) next.take(maxLength) else next
            writePath?.let { context.setBinding(it, capped) }
        },
        placeholder = { Text(placeholder) },
        singleLine = !multiline,
        modifier = Modifier
            .fillMaxWidth()
            .nodeTag(node.id),
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
        modifier = Modifier.nodeTag(node.id),
    )
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

private data class ResolvedCardProps(
    val background: Color?,
    val cornerRadius: Int?,
    val padding: Int?,
    val maxWidthFraction: Float?,
)

private fun Map<String, Any?>.asResolvedCardProps(): ResolvedCardProps = ResolvedCardProps(
    background = (get("background") as? String)?.parseColor(),
    cornerRadius = (get("cornerRadius") as? Double)?.toInt(),
    padding = (get("padding") as? Double)?.toInt(),
    maxWidthFraction = (get("maxWidthFraction") as? Double)?.toFloat(),
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
