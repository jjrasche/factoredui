package ai.factoredui.compose.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import ai.factoredui.compose.ramp.ValueColourReading
import ai.factoredui.compose.ramp.legibleInk
import ai.factoredui.compose.ramp.readValueColour
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ai.factoredui.compose.adapter.HostDataSource
import ai.factoredui.compose.scene3d.RenderScene3d
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.ImageFit
import ai.factoredui.compose.schema.LayoutAlign
import ai.factoredui.compose.schema.LayoutJustify
import ai.factoredui.compose.schema.LayoutProps
import ai.factoredui.compose.schema.ListProps
import ai.factoredui.compose.schema.Spec
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
import ai.factoredui.compose.schema.fillsParent
import ai.factoredui.compose.schema.asListProps
import ai.factoredui.compose.schema.selectListRows
import ai.factoredui.compose.schema.asSpacerProps
import ai.factoredui.compose.schema.asTextProps
import ai.factoredui.compose.schema.bindingPath
import ai.factoredui.compose.schema.displayedText
import ai.factoredui.compose.schema.ButtonSize
import ai.factoredui.compose.schema.ButtonVariant
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalTextStyle
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
    context.screen.root = root
    ThemeScope(context.theme) {
        CompositionLocalProvider(LocalScreenRegistry provides context.screen) {
            ScreenFrameCounter(context.screen)
            RenderNode(node = root, context = context)
        }
    }
}

private fun themeOf(spec: Spec, context: RenderContext): SpecTheme {
    val named = spec.theme ?: return context.theme
    return context.themes[named] ?: themeNamed(named) ?: context.theme.also { context.setBinding(RENDERER_ERROR_PATH, "spec theme must be light or dark, not '$named'; the host's theme is used.") }
}

private fun windowGroundOf(root: SpecNode, theme: SpecTheme): Color {
    val named = (root.props["background"] as? SpecValue.StringValue)?.value?.takeUnless { it.startsWith("{") }
    return named?.let { theme.tokenColor(it) ?: it.parseColor() } ?: theme.ground
}

/**
 * Renders a full [Spec]: its root tree plus any spec-level keyboard shortcuts.
 * When [Spec.keybindings] is empty this is just the root render — no focus
 * machinery is installed.
 */
@Composable
fun RenderSpec(spec: Spec, context: RenderContext) {
    remember(spec) { context.startBindings(spec.state.mapValues { (_, value) -> BindingResolver.resolveValue(value, emptyMap()) }) }
    context.screen.root = spec.root
    ThemeScope(remember(spec.theme, context.theme) { themeOf(spec, context) }) {
        CompositionLocalProvider(LocalScreenRegistry provides context.screen) {
            ScreenFrameCounter(context.screen)
            Box(modifier = Modifier.fillMaxSize().background(windowGroundOf(spec.root, LocalSpecTheme.current))) {
                WindowSizeBindings(context)
                if (spec.keybindings.isEmpty()) {
                    RenderNode(node = spec.root, context = context)
                } else {
                    KeybindingHost(keybindings = spec.keybindings, context = context) {
                        RenderNode(node = spec.root, context = context)
                    }
                }
            }
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
    val path = LocalPathPrefix.current + node.id
    RecordNodeLifetime(path, node)
    if (!isVisible) {
        RecordNode(path, node) { visible = false }
        return
    }
    PropReads.visit(node.id)
    @Suppress("NAME_SHADOWING") val node = PropReads.instrument(node)

    // Fire render observability hook — mirrors the path-context wrapping in the React renderer
    LaunchedEffect(node.id) {
        context.observability.onRender(node.id)
    }

    val resolvedProps = remember(node.props, liveData) {
        PropReads.watchResolved(node.id, BindingResolver.resolveProps(node.props, liveData))
    }

    RecordNode(path, node) {
        visible = true
        boundProps = boundPropsOf(node, resolvedProps)
        enabled = resolvedProps["disabled"] != true
        action = node.action?.action
    }
    val scopedTheme = themeNamed(resolvedProps["theme"] as? String)
    val profile = scopedProfileOf(node, resolvedProps) ?: LocalDisplayProfile.current
    CompositionLocalProvider(LocalNodeIdentity provides NodeIdentity(node.id, path), LocalDisplayProfile provides profile) {
        SwipeHost(node, context) {
            if (scopedTheme == null) {
                RenderNodeByType(node = node, resolvedProps = resolvedProps, context = context)
            } else {
                ThemeScope(scopedTheme) { RenderNodeByType(node = node, resolvedProps = resolvedProps, context = context) }
            }
        }
    }
}

private fun typeWireOf(node: SpecNode): String = node.type.name.lowercase()

@Composable
private fun RecordNodeLifetime(path: String, node: SpecNode) {
    val registry = LocalScreenRegistry.current ?: return
    DisposableEffect(registry, path) {
        registry.record(path, node.id, typeWireOf(node)) {}
        onDispose { registry.forget(path) }
    }
}

@Composable
private fun RecordNode(path: String, node: SpecNode, change: NodeRecord.() -> Unit) {
    val registry = LocalScreenRegistry.current ?: return
    SideEffect { registry.record(path, node.id, typeWireOf(node), change) }
}

private const val LONGEST_SHOWN_TEXT = 120
private const val MOST_SHOWN_ITEMS = 50

private fun compactValue(value: Any?): Any? = when (value) {
    is String -> if (value.length <= LONGEST_SHOWN_TEXT) value else "<text: ${value.length} chars>"
    is List<*> -> if (value.size <= MOST_SHOWN_ITEMS && value.none { it is Map<*, *> || it is List<*> }) value else "<list: ${value.size} items>"
    is Map<*, *> -> "<object: ${value.size} keys>"
    else -> value
}

private fun boundPropsOf(node: SpecNode, resolvedProps: Map<String, Any?>): Map<String, Any?> =
    node.props.filterValues { it is SpecValue.StringValue && BindingResolver.isBindingRef(it.value) }
        .keys.sorted().associateWith { compactValue(resolvedProps[it]) }

@Composable
private fun ThemeScope(theme: SpecTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = theme.colorScheme(MaterialTheme.colorScheme), typography = theme.typography(), shapes = theme.shapes()) {
        CompositionLocalProvider(LocalSpecTheme provides theme, LocalContentColor provides theme.ink, content = content)
    }
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
        SpecNodeType.COLUMN -> RenderColumn(node, resolvedProps, context)
        SpecNodeType.ROW -> RenderRow(node, resolvedProps, context)
        SpecNodeType.STACK -> RenderStack(node, context)
        SpecNodeType.SCROLLVIEW -> RenderScrollView(node, context)
        SpecNodeType.GRID -> RenderGrid(node, context)
        SpecNodeType.TEXT -> RenderText(node, resolvedProps, context)
        SpecNodeType.BUTTON -> RenderButton(node, resolvedProps, context)
        SpecNodeType.IMAGE -> RenderImage(node, resolvedProps)
        SpecNodeType.ICON -> RenderIcon(node, resolvedProps)
        SpecNodeType.DIVIDER -> RenderDivider(node)
        SpecNodeType.SPACER -> RenderSpacer(node)
        SpecNodeType.LIST -> RenderList(node, context)
        SpecNodeType.CARD -> RenderCard(node, resolvedProps, context)
        SpecNodeType.TEXTINPUT -> RenderTextInput(node, resolvedProps, context)
        SpecNodeType.CHIP -> RenderChip(node, resolvedProps, context)
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
                modifier = Modifier.nodeTag(node.id).let { base ->
                    if (resolveLayoutProps(node, resolvedProps).flex > 0f) base.fillMaxSize() else base
                },
            )
        }
        SpecNodeType.CANVAS -> RenderCanvas(node, context)
        SpecNodeType.GEOMAP -> RenderGeomap(node, resolvedProps, context)
        SpecNodeType.GRAPH -> RenderGraph(node, resolvedProps, context)
        SpecNodeType.PLAN -> RenderPlan(node, resolvedProps, context)
        SpecNodeType.CHART -> RenderChart(node, resolvedProps, context)
        SpecNodeType.HEATMAP -> RenderHeatmap(node, resolvedProps)
        SpecNodeType.STAT -> RenderStat(node, resolvedProps)
        SpecNodeType.VOLUME -> RenderVolume(node, resolvedProps, context)
        SpecNodeType.SCENE -> RenderScene(node, resolvedProps, context)
        SpecNodeType.TABLE -> RenderTable(node, resolvedProps, context)
        SpecNodeType.TOGGLE -> RenderToggle(node, resolvedProps, context)
        SpecNodeType.VIDEO -> RenderVideo(node, resolvedProps, context)
        SpecNodeType.AUDIO -> RenderAudio(node, resolvedProps, context)
        SpecNodeType.READALONG -> RenderReadAlong(node, resolvedProps, context)
        SpecNodeType.SHEET -> RenderSheet(node, resolvedProps, context)
        SpecNodeType.VOICE -> RenderVoice(node, resolvedProps, context)
        SpecNodeType.SLIDER -> RenderSlider(node, resolvedProps, context)
        SpecNodeType.SELECT -> RenderSelect(node, resolvedProps, context)
        SpecNodeType.TABS -> RenderTabs(node, resolvedProps, context)
        SpecNodeType.MODAL -> RenderModal(node, resolvedProps, context)
    }
}

// --- Container nodes ---

private fun resolveLayoutProps(node: SpecNode, resolved: Map<String, Any?>): LayoutProps {
    val base = node.props.asLayoutProps()
    val justify = when (resolved["justify"] as? String) {
        "center" -> LayoutJustify.CENTER
        "end" -> LayoutJustify.END
        "between" -> LayoutJustify.BETWEEN
        "around" -> LayoutJustify.AROUND
        "start" -> LayoutJustify.START
        else -> base.justify
    }
    val align = when (resolved["align"] as? String) {
        "center" -> LayoutAlign.CENTER
        "end" -> LayoutAlign.END
        "stretch" -> LayoutAlign.STRETCH
        "start" -> LayoutAlign.START
        else -> base.align
    }
    return base.copy(
        justify = justify,
        align = align,
        gap = (resolved["gap"] as? Number)?.toInt() ?: base.gap,
        padding = (resolved["padding"] as? Number)?.toInt() ?: base.padding,
        background = (resolved["background"] as? String) ?: base.background,
        flex = (resolved["flex"] as? Number)?.toFloat() ?: base.flex,
    )
}

@Composable
private fun RenderColumn(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val liveData by context.dataFlow.collectAsState()
    val props = resolveLayoutProps(node, resolvedProps)
    val hasFlexChild = node.children.any { it.props.asLayoutProps().flex > 0f }
    // A node's OWN flex is honoured by whatever lays it out — except at the root, where
    // nothing does. Filling here means a flex container claims its space wherever it sits.
    val claimsSpace = props.flex > 0f
    val fillWidth = hasFlexChild || claimsSpace || props.align != LayoutAlign.START
    val colModifier = Modifier.nodeTag(node.id).groundOf(props).padding(props.padding.dp)
        .let { if (fillWidth) it.fillMaxWidth() else it }
        .let { if (hasFlexChild || claimsSpace) it.fillMaxHeight() else it }
    Column(
        modifier = colModifier,
        verticalArrangement = verticalArrangement(props.justify, props.gap),
        horizontalAlignment = columnHorizontalAlignment(props.align),
    ) {
        SlotSizeNotices(node.children)
        node.children.forEach { child ->
            val childLayout = child.props.asLayoutProps()
            val childFlex = childLayout.flex
            val fixedWidth = childLayout.width
            val fixedHeight = childLayout.height
            when {
                !BindingResolver.isVisible(child.visible, liveData) -> RenderNode(node = child, context = context)
                fixedHeight != null -> Box(Modifier.height(fixedHeight.dp).then(columnSlotWidth(fixedWidth, props.align)), propagateMinConstraints = true) { RenderInSlot(child, context, fixedWidth) }
                fixedWidth != null -> Box(Modifier.let { if (childFlex > 0f) it.weight(childFlex) else it }.width(fixedWidth.dp)) { RenderInFixedSlot(child, context) }
                childFlex > 0f -> Box(Modifier.weight(childFlex)) { RenderNode(node = child, context = context) }
                props.align == LayoutAlign.STRETCH -> Box(Modifier.fillMaxWidth()) { RenderNode(node = child, context = context) }
                else -> RenderNode(node = child, context = context)
            }
        }
    }
}

@Composable
private fun RenderRow(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val liveData by context.dataFlow.collectAsState()
    val props = resolveLayoutProps(node, resolvedProps)
    if (node.scrollsToARow()) return ScrollingRowOfData(node, resolvedProps, context, props.gap, props.padding)
    val hasFlexChild = node.children.any { it.props.asLayoutProps().flex > 0f }
    val claimsSpace = props.flex > 0f
    val fillWidth = hasFlexChild || claimsSpace || props.justify != LayoutJustify.START
    val scope = rememberCoroutineScope()
    val rowModifier = Modifier.nodeTag(node.id).groundOf(props)
        .let { modifier -> node.action?.let { action -> modifier.clickable { scope.launch { context.dispatch(node.id, action) } } } ?: modifier }
        .padding(props.padding.dp)
        .let { if (fillWidth) it.fillMaxWidth() else it }
        .let { if (claimsSpace) it.fillMaxHeight() else it }
    Row(
        modifier = rowModifier,
        horizontalArrangement = horizontalArrangement(props.justify, props.gap),
        verticalAlignment = rowVerticalAlignment(props.align),
    ) {
        SlotSizeNotices(node.children)
        node.children.forEach { child ->
            val childLayout = child.props.asLayoutProps()
            val fixedWidth = childLayout.width
            val fixedHeight = heightOf(childLayout.height)
            val isHeightFixed = childLayout.height != null
            when {
                !BindingResolver.isVisible(child.visible, liveData) -> RenderNode(node = child, context = context)
                fixedWidth != null -> Box(modifier = Modifier.width(fixedWidth.dp).then(fixedHeight), propagateMinConstraints = isHeightFixed) { RenderInFixedSlot(child, context) }
                childLayout.flex > 0f -> Box(modifier = Modifier.weight(childLayout.flex).then(fixedHeight), propagateMinConstraints = isHeightFixed) {
                    CompositionLocalProvider(LocalSlotWidth provides SlotWidth.GIVEN) { RenderNode(node = child, context = context) }
                }
                isHeightFixed -> Box(modifier = fixedHeight, propagateMinConstraints = true) {
                    CompositionLocalProvider(LocalSlotWidth provides SlotWidth.SHARED) { RenderNode(node = child, context = context) }
                }
                else -> CompositionLocalProvider(LocalSlotWidth provides SlotWidth.SHARED) { RenderNode(node = child, context = context) }
            }
        }
        RowOfData(node, resolvedProps, context)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RowOfData(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val template = (node.props["itemTemplate"] as? SpecValue.NodeValue)?.value ?: return
    val rows = (resolvedProps["data"] as? List<*>)?.filterNotNull() ?: return
    val listPath = LocalNodeIdentity.current?.path ?: node.id
    rows.forEachIndexed { index, row ->
        val rowContext = context.withAdditionalData(mapOf("row" to row, "item" to row))
        val flex = (BindingResolver.resolveProps(template.props, rowContext.data)["flex"] as? Number)?.toFloat() ?: template.props.asLayoutProps().flex
        CompositionLocalProvider(LocalPathPrefix provides "$listPath/$index/") {
            if (flex > 0f) Box(modifier = Modifier.weight(flex)) { RenderNode(node = template, context = rowContext) } else RenderNode(node = template, context = rowContext)
        }
    }
}

@Composable
private fun RenderInFixedSlot(child: SpecNode, context: RenderContext) {
    CompositionLocalProvider(LocalSlotWidth provides SlotWidth.FIXED) { RenderNode(node = child, context = context) }
}

@Composable
private fun RenderInSlot(child: SpecNode, context: RenderContext, fixedWidth: Float?) {
    if (fixedWidth != null) RenderInFixedSlot(child, context) else RenderNode(node = child, context = context)
}

private fun heightOf(fixedHeight: Float?): Modifier = fixedHeight?.let { Modifier.height(it.dp) } ?: Modifier

private fun widthOf(fixedWidth: Float?): Modifier = fixedWidth?.let { Modifier.width(it.dp) } ?: Modifier

private fun columnSlotWidth(fixedWidth: Float?, align: LayoutAlign): Modifier = when {
    fixedWidth != null -> Modifier.width(fixedWidth.dp)
    align == LayoutAlign.STRETCH -> Modifier.fillMaxWidth()
    else -> Modifier
}

/** A child of a stack or a scroll with a width or height is drawn in a slot of exactly that size; one with neither is drawn as it is. */
@Composable
internal fun RenderSizedChild(child: SpecNode, context: RenderContext) {
    val layout = child.props.asLayoutProps()
    if (layout.width == null && layout.height == null) return RenderNode(node = child, context = context)
    Box(modifier = widthOf(layout.width).then(heightOf(layout.height)), propagateMinConstraints = true) { RenderInSlot(child, context, layout.width) }
}

@Composable
private fun SlotSizeNotices(children: List<SpecNode>) {
    val faults = children.flatMap { child -> child.props.asLayoutProps().sizeFaults.map { "'${child.id}' $it" } }
    if (faults.isEmpty()) return
    RecordDrawn { notices = faults }
    faults.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB3261E)) }
}

// A declared ground is painted UNDER the padding, so the colour fills the container
// rather than only the area inside its own inset.
@Composable
private fun Modifier.groundOf(props: LayoutProps): Modifier {
    val theme = LocalSpecTheme.current
    val ground = props.background?.let { theme.tokenColor(it) ?: it.parseColor() } ?: return this
    RecordDrawn { fill = argbOf(ground) }
    return this.background(ground)
}

internal fun Modifier.nodeTag(id: String): Modifier =
    this.testTag(id).semantics { contentDescription = id }.placementOf(id)

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
    Box(modifier = (if (node.props.fillsParent()) Modifier.fillMaxSize() else Modifier).groundOf(node.props.asLayoutProps()).nodeTag(node.id)) {
        SlotSizeNotices(node.children)
        node.children.forEach { child -> RenderStackChild(child, context) }
    }
}

@Composable
private fun RenderScrollView(node: SpecNode, context: RenderContext) {
    BoxWithConstraints(propagateMinConstraints = true) {
        val scrolls = if (constraints.hasBoundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(modifier = Modifier.nodeTag(node.id).then(scrolls)) {
            SlotSizeNotices(node.children)
            node.children.forEach { child -> RenderSizedChild(child, context) }
        }
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
    Column(modifier = Modifier.nodeTag(node.id), verticalArrangement = Arrangement.spacedBy(props.gap.dp)) {
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
        modifier = Modifier.nodeTag(node.id).fillMaxWidth(),
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
    val cardModifier = widthModifier.nodeTag(node.id)
    RecordDrawn { fill = props.background?.let(::argbOf) }
    val action = node.action
    if (action != null) {
        val scope = rememberCoroutineScope()
        Card(
            onClick = { scope.launch { context.dispatch(node.id, action) } },
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
        ) {
            val contentModifier = props.padding?.let { Modifier.padding(it.dp) } ?: Modifier
            Column(modifier = contentModifier) {
                node.children.forEach { child -> RenderNode(node = child, context = context) }
            }
        }
    } else {
        Card(modifier = cardModifier, shape = shape, colors = colors, elevation = elevation) {
            val contentModifier = props.padding?.let { Modifier.padding(it.dp) } ?: Modifier
            Column(modifier = contentModifier) {
                node.children.forEach { child -> RenderNode(node = child, context = context) }
            }
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
    val drawsRows = (host != null && !query.isNullOrEmpty()) || props.itemTemplate != null
    if (!drawsRows && node.children.isNotEmpty()) {
        RenderChildList(node, context)
        return
    }
    if (host != null && !query.isNullOrEmpty()) {
        RenderLiveList(props, context, host, query)
    } else {
        RenderStaticList(props, context)
    }
    if (node.children.isNotEmpty()) IgnoredChildrenNotice(node.children.size)
}

private fun ignoredChildrenText(count: Int): String =
    "this list has $count child${if (count == 1) "" else "ren"} the renderer ignores: " +
        "a list draws either its children or its data template, not both"

// A list whose rows ARE its children: heterogeneous rows, each its own node, where one
// template over uniform data is the wrong shape.
@Composable
private fun RenderChildList(node: SpecNode, context: RenderContext) {
    BoxWithConstraints(propagateMinConstraints = true) {
        if (constraints.hasBoundedHeight) {
            LazyColumn(modifier = Modifier.nodeTag(node.id)) {
                items(node.children) { child -> RenderNode(node = child, context = context) }
            }
        } else {
            Column(modifier = Modifier.nodeTag(node.id)) {
                node.children.forEach { child -> RenderNode(node = child, context = context) }
            }
        }
    }
}

// A node handed content it will not draw says so on the screen. Silently dropping it
// renders an empty region that reads as a working screen with nothing in it.
@Composable
private fun IgnoredChildrenNotice(count: Int) {
    RecordDrawn { notices = listOf(ignoredChildrenText(count)) }
    Text(
        text = ignoredChildrenText(count),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFB3261E),
    )
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
    val liveData by context.dataFlow.collectAsState()
    val selected = selectListRows(rows, props.where?.let { BindingResolver.resolveValue(it, liveData) }, props.order?.let { BindingResolver.resolveValue(it, liveData) })
    val visibleRows = selected.filterNotNull().let { all ->
        val cap = props.maxItems
        if (cap != null && all.size > cap) all.take(cap) else all
    }

    val isShowingEmpty = visibleRows.isEmpty() && props.emptyText.isNotEmpty()
    RecordDrawn {
        emptyMessage = if (isShowingEmpty) props.emptyText else null
        if (!isShowingEmpty) emptyBounds = null
    }
    if (isShowingEmpty) {
        Text(text = props.emptyText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.emptyMessagePlacement())
        return
    }

    val itemTemplate = props.itemTemplate ?: return
    val list = LocalNodeIdentity.current
    RecordDrawn { rowCount = visibleRows.size }
    BoxWithConstraints(modifier = list?.let { Modifier.nodeTag(it.id) } ?: Modifier, propagateMinConstraints = true) {
        if (props.stacked || !constraints.hasBoundedHeight) {
            StackedRows(visibleRows, itemTemplate, context, list?.path.orEmpty(), props.rowName)
        } else {
            LazyRows(visibleRows, itemTemplate, context, list?.path.orEmpty(), props.rowName)
        }
    }
}

private fun rowData(row: Any, rowName: String?): Map<String, Any> = buildMap {
    put("row", row)
    put("item", row)
    rowName?.let { put(it, row) }
}

@Composable
private fun RowScope(listPath: String, index: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPathPrefix provides "$listPath/$index/", content = content)
}

@Composable
private fun StackedRows(rows: List<Any>, itemTemplate: SpecNode, context: RenderContext, listPath: String, rowName: String?) {
    Column {
        rows.forEachIndexed { index, row ->
            RowScope(listPath, index) {
                RenderNode(node = itemTemplate, context = context.withAdditionalData(rowData(row, rowName)))
            }
        }
    }
}

@Composable
private fun LazyRows(visibleRows: List<Any>, itemTemplate: SpecNode, context: RenderContext, listPath: String, rowName: String?) {
    LazyColumn {
        itemsIndexed(visibleRows) { index, row ->
            // Scope the row under "row" (the requested "{row.x}" syntax) and
            // "item" (legacy syntax used by existing static specs + nested
            // lists). Both keys point at the same row, so "{row.id}" and
            // "{item.id}" resolve identically — and because the template renders
            // with this scoped context, action param interpolation in dispatch()
            // picks up the same row overlay for free.
            val rowContext = context.withAdditionalData(rowData(row, rowName))
            RowScope(listPath, index) { RenderNode(node = itemTemplate, context = rowContext) }
        }
    }
}

// --- Leaf nodes ---

@Composable
private fun RenderText(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val theme = LocalSpecTheme.current
    val scope = rememberCoroutineScope()
    val props = resolvedProps.asResolvedTextProps().let { read -> read.copy(colorValue = read.colorName?.let { named -> theme.tokenColor(named) ?: named.parseColor() }) }
    val style = when (props.variant) {
        "display" -> MaterialTheme.typography.displayMedium
        "heading" -> MaterialTheme.typography.headlineMedium
        "subheading" -> MaterialTheme.typography.titleMedium
        "caption" -> MaterialTheme.typography.bodySmall
        "label" -> MaterialTheme.typography.labelMedium
        "mono" -> theme.monoStyle()
        else -> MaterialTheme.typography.bodyMedium
    }
    val paint = textPaint(props, LocalSpecTheme.current.ink)
    RecordDrawn {
        text = paint.notice ?: props.value
        fill = paint.background?.let(::argbOf)
        ink = argbOf(paint.ink)
        isBoundToNull = isBindingToNull(node, resolvedProps)
    }
    val fills = fillsItsSlot(props.align, paint.background != null, LocalSlotWidth.current)
    Text(
        text = paint.notice ?: props.value,
        style = style.copy(
            fontWeight = if (props.bold) FontWeight.Bold else null,
            color = paint.ink,
        ),
        textAlign = when (props.align) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.End
            else -> TextAlign.Start
        },
        maxLines = props.numberOfLines ?: Int.MAX_VALUE,
        modifier = Modifier.nodeTag(node.id)
            .let { modifier -> node.action?.let { action -> modifier.clickable { scope.launch { context.dispatch(node.id, action) } } } ?: modifier }
            .let { if (fills) it.fillMaxWidth() else it }
            .let { modifier -> paint.background?.let { modifier.background(it).padding(horizontal = 6.dp, vertical = 2.dp) } ?: modifier },
    )
}

/** The width a node's slot gives it: what its parent gives (a flex share or the parent's width), shared with flex siblings in a row, or a fixed `width`. */
internal enum class SlotWidth { GIVEN, SHARED, FIXED }

internal val LocalSlotWidth = staticCompositionLocalOf { SlotWidth.GIVEN }

private fun isBindingToNull(node: SpecNode, resolvedProps: Map<String, Any?>): Boolean {
    val raw = node.props["value"] as? SpecValue.StringValue ?: return false
    return BindingResolver.isBindingRef(raw.value) && resolvedProps["value"] == null
}

private fun fillsItsSlot(align: String, hasBackground: Boolean, slot: SlotWidth): Boolean = when (slot) {
    SlotWidth.FIXED -> true
    SlotWidth.SHARED -> false
    SlotWidth.GIVEN -> align == "center" || align == "right" || hasBackground
}

private class TextPaint(val ink: Color, val background: Color?, val notice: String?)

private fun argbOf(colour: Color): Long = colour.toArgb().toLong() and 0xFFFFFFFFL

private fun textPaint(props: ResolvedTextProps, themeInk: Color): TextPaint {
    val inkRamp = readValueColour("color_ramp", props.colorRamp)
    val groundRamp = readValueColour("background_ramp", props.backgroundRamp)
    val notice = listOfNotNull(inkRamp, groundRamp).filterIsInstance<ValueColourReading.Refused>().firstOrNull()?.notice
    val preferred = (inkRamp as? ValueColourReading.Coloured)?.colour ?: props.colorValue?.let(::argbOf)
    val background = (groundRamp as? ValueColourReading.Coloured)?.colour
    val ink = when {
        background != null -> Color(legibleInk(preferred, background))
        preferred != null -> Color(preferred)
        else -> themeInk
    }
    return TextPaint(ink, background?.let { Color(it) }, notice)
}

@Composable
private fun RenderButton(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asButtonProps()
    val label = (resolvedProps["label"] as? String) ?: props.label
    val icon = (resolvedProps["icon"] as? String) ?: props.icon
    val isDisabled = (resolvedProps["disabled"] as? Boolean) ?: props.disabled
    val scope = rememberCoroutineScope()
    RecordDrawn {
        text = label
        enabled = !isDisabled
    }

    val onClickHandler: () -> Unit = {
        node.action?.let { actionRef ->
            scope.launch { context.dispatch(node.id, actionRef) }
        }
    }

    val sized = Modifier.nodeTag(node.id).heightIn(min = LocalSpecTheme.current.control.dpOf(props.size.name.lowercase()))
    when (props.variant) {
        ButtonVariant.OUTLINE -> OutlinedButton(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = sized,
        ) { ButtonContent(icon, label, props.size) }

        ButtonVariant.GHOST -> TextButton(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = sized,
        ) { ButtonContent(icon, label, props.size) }

        ButtonVariant.DESTRUCTIVE -> Button(
            onClick = onClickHandler,
            enabled = !isDisabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = sized,
        ) { ButtonContent(icon, label, props.size) }

        else -> Button(
            onClick = onClickHandler,
            enabled = !isDisabled,
            modifier = sized,
        ) { ButtonContent(icon, label, props.size) }
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
private fun ButtonContent(icon: String?, label: String, size: ButtonSize) {
    val style = if (size == ButtonSize.XL) MaterialTheme.typography.headlineSmall else LocalTextStyle.current
    if (!icon.isNullOrEmpty()) {
        IconifyImage(name = icon, sizeDp = 20.dp, contentDescription = label.ifEmpty { "icon" })
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Text(label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Spacer(modifier = Modifier.nodeTag(node.id).height(height))
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
    RecordDrawn { text = currentValue }

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
    RecordDrawn { text = label }
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
    val colorName: String?,
    val bold: Boolean,
    val numberOfLines: Int?,
    val colorRamp: Any?,
    val backgroundRamp: Any?,
)

private fun Map<String, Any?>.displayedTextValue(): String = displayedText(get("value"), get("format"), get("missing") as? String)

private fun Map<String, Any?>.asResolvedTextProps(): ResolvedTextProps = ResolvedTextProps(
    value = displayedTextValue(),
    variant = (get("variant") as? String) ?: "body",
    align = (get("align") as? String) ?: "left",
    colorValue = null,
    colorName = get("color") as? String,
    bold = (get("bold") as? Boolean) ?: false,
    numberOfLines = (get("numberOfLines") as? Double)?.toInt(),
    colorRamp = get("color_ramp"),
    backgroundRamp = get("background_ramp"),
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

internal fun String.parseColor(): Color? = runCatching {
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
