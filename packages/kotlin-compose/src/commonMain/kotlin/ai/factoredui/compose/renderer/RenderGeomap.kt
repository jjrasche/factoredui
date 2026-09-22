package ai.factoredui.compose.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import ai.factoredui.compose.schema.bindingPath
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import ai.factoredui.compose.schema.GeomapLegendEntry
import ai.factoredui.compose.schema.GeomapPattern
import ai.factoredui.compose.schema.resolveGeomapLegend
import ai.factoredui.compose.schema.GeoPoint
import ai.factoredui.compose.schema.GeomapBoundsRequest
import ai.factoredui.compose.schema.resolveGeomapBoundsRequest
import ai.factoredui.compose.schema.unresolvedGeometryRefs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import ai.factoredui.compose.scene3d.drawTriangleBatch
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.GeomapFeature
import ai.factoredui.compose.schema.GeomapLayer
import ai.factoredui.compose.schema.GeomapLayerKind
import ai.factoredui.compose.schema.GeomapViewport
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.asGeomapProps
import ai.factoredui.compose.schema.resolveGeomapLayers
import ai.factoredui.compose.schema.resolveGeomapViewport
import ai.factoredui.compose.schema.resolveGeomapSelection
import kotlinx.coroutines.launch

private const val DEFAULT_STROKE_ARGB = 0xFF2C3E50.toInt()
private const val LABEL_FIT = 0.9f
private val LABEL_FONT_SIZE = 12.sp

internal class TessellatedFeature(
    val featureId: String,
    val fillArgb: Int?,
    val strokeArgb: Int?,
    val strokeWidth: Float,
    val worldRings: List<DoubleArray>,
    val triangleWorld: DoubleArray,
    val bounds: WorldBounds?,
    val pattern: GeomapPattern?,
    val label: String?,
    val dash: List<Float>?,
    val labelCandidates: List<GeomapLabelAnchor>,
)

internal class TessellatedLayer(
    val layerId: String,
    val kind: GeomapLayerKind,
    val visible: Boolean,
    val features: List<TessellatedFeature>,
    val minZoom: Float? = null,
    val maxZoom: Float? = null,
)

// The style-spec convention: hidden below min_zoom, and already hidden AT max_zoom.
internal fun TessellatedLayer.isShownAt(zoom: Float): Boolean =
    visible && (minZoom == null || zoom >= minZoom) && (maxZoom == null || zoom < maxZoom)

internal class GeomapTessellation(
    val layers: List<TessellatedLayer>,
    val worldBounds: WorldBounds?,
)

internal data class GeomapHit(val layerId: String, val featureId: String)

@Composable
internal fun RenderGeomap(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asGeomapProps()
    val layersData = resolvedProps["layers"]
    val geometriesData = resolvedProps["geometries"]
    val tessellation = remember(layersData, geometriesData) {
        tessellateGeomapLayers(resolveGeomapLayers(layersData, geometriesData))
    }
    val missingGeometries = remember(layersData, geometriesData) { unresolvedGeometryRefs(layersData, geometriesData) }
    val hostCentreViewport = resolveGeomapViewport(resolvedProps["viewport"])
    val hostBounds = resolveGeomapBoundsRequest(resolvedProps["viewport"])
    val legend = resolveGeomapLegend(resolvedProps["legend"])
    val visibilityPath = node.props["layer_visibility"]?.bindingPath()
    val boundVisibility = (resolvedProps["layer_visibility"] as? Map<*, *>)
        ?.mapNotNull { (key, value) -> (key as? String)?.let { id -> (value as? Boolean)?.let { id to it } } }
        ?.toMap().orEmpty()
    var localVisibility by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    val layerVisibility = if (visibilityPath != null) boundVisibility else localVisibility
    val shownTessellation = remember(tessellation, layerVisibility) { tessellation.withVisibility(layerVisibility) }
    val selectedPath = node.props["selected"]?.bindingPath()
    var localSelection by remember { mutableStateOf(resolveGeomapSelection(resolvedProps["selected"])) }
    val selection = if (selectedPath != null) resolveGeomapSelection(resolvedProps["selected"]) else localSelection
    val selectionStyle = GeomapSelectionStyle(
        strokeArgb = parseGeomapColor(resolvedProps["selection_stroke"] as? String) ?: LocalSpecTheme.current.ink.toArgb(),
        strokeWidth = (resolvedProps["selection_stroke_width"] as? Number)?.toFloat() ?: DEFAULT_SELECTION_STROKE_WIDTH,
    )
    val scope = rememberCoroutineScope()
    val labelMeasurer = rememberTextMeasurer()
    val labelInk = LocalSpecTheme.current.ink
    BoxWithConstraints(modifier = Modifier.fillMaxSize().nodeTag(node.id).clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Only the renderer knows its pixel size, so a host asking to frame a region hands over
        // bounds and the fit is done here rather than guessed from outside.
        val hostViewport = hostCentreViewport ?: hostBounds?.let { request ->
            fitGeomapViewport(worldBoundsOfRequest(request), widthPx, heightPx)
        }
        var viewport by remember(widthPx, heightPx) {
            mutableStateOf(hostViewport ?: fitGeomapViewport(tessellation.worldBounds, widthPx, heightPx))
        }
        LaunchedEffect(hostViewport) { hostViewport?.let { viewport = it } }
        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(shownTessellation, props.onFeatureTap, selection, selectedPath) {
                    val onFeatureTap = props.onFeatureTap
                    detectTapGestures(onTap = { offset ->
                        val hit = hitTestGeomap(shownTessellation, viewport, widthPx, heightPx, offset.x, offset.y)
                        val nextSelection = selectionAfterTap(selection, hit)
                        if (selectedPath != null) context.setBinding(selectedPath, nextSelection)
                        else localSelection = nextSelection.toSet()
                        if (hit != null && onFeatureTap != null) {
                            scope.launch { context.dispatch(node.id, featureTapAction(onFeatureTap, hit)) }
                        }
                    })
                }
                .pointerInput(widthPx, heightPx) {
                    detectTransformGestures { centroid, pan, zoomDelta, _ ->
                        viewport = viewport.afterGeomapGesture(
                            centroid.x, centroid.y, pan.x, pan.y, zoomDelta, widthPx, heightPx,
                        )
                    }
                }
                .pointerInput(props.onViewportChanged) {
                    val onViewportChanged = props.onViewportChanged
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        if (onViewportChanged != null) {
                            scope.launch { context.dispatch(node.id, viewportChangedAction(onViewportChanged, viewport)) }
                        }
                    }
                },
        ) {
            drawGeomapTessellation(shownTessellation, viewport, widthPx, heightPx, labelMeasurer, labelInk, selection, selectionStyle)
        }
        if (legend.isNotEmpty()) {
            GeomapLegend(
                entries = legend,
                nodeId = node.id,
                isLayerShown = { layerId -> layerVisibility[layerId] ?: true },
                onToggleLayer = { layerId ->
                    val shown = layerVisibility[layerId] ?: true
                    if (visibilityPath != null) context.setBinding("$visibilityPath.$layerId", !shown)
                    else localVisibility = localVisibility + (layerId to !shown)
                },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        if (missingGeometries.isNotEmpty()) {
            Text(
                text = "${missingGeometries.size} geometry reference(s) have no entry in `geometries`: " +
                    missingGeometries.joinToString(", "),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSpecTheme.current.ink,
            )
        }
    }
}

// A geometry referenced by several layers is projected and triangulated ONCE and shared by
// reference — the point of the table, since four views of 83 counties are one set of outlines.
private class SharedGeometry(val worldRings: List<DoubleArray>, val bounds: WorldBounds?) {
    var triangles: DoubleArray? = null
}

internal fun tessellateGeomapLayers(layers: List<GeomapLayer>): GeomapTessellation {
    var bounds: WorldBounds? = null
    val shared = mutableMapOf<String, SharedGeometry>()
    val tessellatedLayers = layers.map { layer ->
        val features = layer.features.map { feature ->
            val geometry = feature.geometryId?.let { id ->
                shared.getOrPut(id) { SharedGeometry(projectRings(feature.rings), worldBoundsOf(feature.rings)) }
            }
            val featureBounds = geometry?.bounds ?: worldBoundsOf(feature.rings)
            featureBounds?.let { bounds = bounds?.union(it) ?: it }
            tessellateFeature(feature, layer.kind, featureBounds, geometry)
        }
        TessellatedLayer(layer.id, layer.kind, layer.visible, features, layer.minZoom, layer.maxZoom)
    }
    return GeomapTessellation(tessellatedLayers, bounds)
}

private fun projectRings(rings: List<List<GeoPoint>>): List<DoubleArray> = rings.map { ring ->
    DoubleArray(ring.size * 2).also { flat ->
        ring.forEachIndexed { index, point ->
            flat[index * 2] = lonToWorldX(point.lon)
            flat[index * 2 + 1] = latToWorldY(point.lat)
        }
    }
}

private fun tessellateFeature(
    feature: GeomapFeature,
    kind: GeomapLayerKind,
    bounds: WorldBounds?,
    shared: SharedGeometry?,
): TessellatedFeature {
    val worldRings = shared?.worldRings ?: projectRings(feature.rings)
    val fillArgb = if (kind == GeomapLayerKind.FILL) parseGeomapColor(feature.fill) else null
    val needsTriangles = fillArgb != null && worldRings.size == 1
    val triangleWorld = when {
        !needsTriangles -> DoubleArray(0)
        shared != null -> shared.triangles ?: expandTriangles(worldRings).also { shared.triangles = it }
        else -> expandTriangles(worldRings)
    }
    return TessellatedFeature(
        featureId = feature.id,
        fillArgb = fillArgb,
        strokeArgb = parseGeomapColor(feature.stroke)
            ?: if (kind == GeomapLayerKind.LINE) parseGeomapColor(feature.fill) ?: DEFAULT_STROKE_ARGB else null,
        strokeWidth = feature.strokeWidth,
        worldRings = worldRings,
        triangleWorld = triangleWorld,
        bounds = bounds,
        pattern = if (kind == GeomapLayerKind.FILL) feature.pattern else null,
        label = feature.label?.takeIf { it.isNotBlank() },
        dash = feature.dash,
        labelCandidates = if (feature.label.isNullOrBlank()) emptyList() else geomapLabelCandidatesOf(worldRings),
    )
}

// Drawing every feature every frame is what made a county of parcels unusable; a feature
// whose bounds miss the view contributes no pixels, so it is skipped before any vertex work.
private fun TessellatedLayer.featuresIn(view: WorldBounds): List<TessellatedFeature> =
    features.filter { feature -> feature.bounds?.intersects(view) ?: false }

internal fun GeomapTessellation.withVisibility(overrides: Map<String, Boolean>): GeomapTessellation =
    if (overrides.isEmpty()) this
    else GeomapTessellation(
        layers.map { layer ->
            TessellatedLayer(
                layer.layerId, layer.kind, layer.visible && (overrides[layer.layerId] ?: true), layer.features, layer.minZoom, layer.maxZoom,
            )
        },
        worldBounds,
    )

internal fun drawnGeomapFeatureIds(
    tessellation: GeomapTessellation,
    viewport: GeomapViewport,
    widthPx: Float,
    heightPx: Float,
): List<String> {
    val view = visibleWorldBounds(viewport, widthPx, heightPx)
    return tessellation.layers.filter { it.isShownAt(viewport.zoom) }.flatMap { layer -> layer.featuresIn(view).map { it.featureId } }
}

private fun expandTriangles(worldRings: List<DoubleArray>): DoubleArray {
    val expanded = ArrayList<Double>()
    for (ring in worldRings) {
        val indices = earClipTriangulate(ring)
        for (vertexIndex in indices) {
            expanded.add(ring[vertexIndex * 2])
            expanded.add(ring[vertexIndex * 2 + 1])
        }
    }
    return expanded.toDoubleArray()
}

internal fun hitTestGeomap(
    tessellation: GeomapTessellation,
    viewport: GeomapViewport,
    widthPx: Float,
    heightPx: Float,
    tapXpx: Float,
    tapYpx: Float,
): GeomapHit? {
    val scale = geomapScalePx(viewport.zoom)
    val worldX = lonToWorldX(viewport.lon) + (tapXpx - widthPx / 2.0) / scale
    val worldY = latToWorldY(viewport.lat) + (tapYpx - heightPx / 2.0) / scale
    for (layer in tessellation.layers.asReversed()) {
        if (!layer.isShownAt(viewport.zoom)) continue
        for (feature in layer.features.asReversed()) {
            if (isPointInRings(worldX, worldY, feature.worldRings)) {
                return GeomapHit(layer.layerId, feature.featureId)
            }
        }
    }
    return null
}

private fun DrawScope.drawGeomapTessellation(
    tessellation: GeomapTessellation,
    viewport: GeomapViewport,
    widthPx: Float,
    heightPx: Float,
    labelMeasurer: TextMeasurer,
    labelInk: Color,
    selection: Set<String>,
    selectionStyle: GeomapSelectionStyle,
) {
    val scale = geomapScalePx(viewport.zoom)
    val centerX = lonToWorldX(viewport.lon)
    val centerY = latToWorldY(viewport.lat)
    fun screenX(worldX: Double): Float = ((worldX - centerX) * scale + widthPx / 2.0).toFloat()
    fun screenY(worldY: Double): Float = ((worldY - centerY) * scale + heightPx / 2.0).toFloat()
    val view = visibleWorldBounds(viewport, widthPx, heightPx)

    for (layer in tessellation.layers) {
        if (!layer.isShownAt(viewport.zoom) || layer.kind != GeomapLayerKind.FILL) continue
        val inView = layer.featuresIn(view)
        var vertexCount = 0
        for (feature in inView) vertexCount += feature.triangleWorld.size / 2
        if (vertexCount >= 3) {
            val positions = FloatArray(vertexCount * 2)
            val colors = IntArray(vertexCount)
            var vertex = 0
            for (feature in inView) {
                val fill = feature.fillArgb ?: continue
                val triangles = feature.triangleWorld
                for (index in 0 until triangles.size / 2) {
                    positions[vertex * 2] = screenX(triangles[index * 2])
                    positions[vertex * 2 + 1] = screenY(triangles[index * 2 + 1])
                    colors[vertex] = fill
                    vertex++
                }
            }
            drawIntoCanvas { canvas -> drawTriangleBatch(canvas, positions, colors, vertex) }
        }
        // Inner rings are holes: even-odd fills them as empty, which the triangle batch cannot express.
        for (feature in inView) {
            val fill = feature.fillArgb ?: continue
            if (feature.worldRings.size < 2) continue
            drawPath(screenPathOf(feature.worldRings, closed = true, ::screenX, ::screenY), Color(fill))
        }
    }

    for (layer in tessellation.layers) {
        if (!layer.isShownAt(viewport.zoom)) continue
        for (feature in layer.featuresIn(view)) {
            val pattern = feature.pattern ?: continue
            paintGeomapPattern(screenPathOf(feature.worldRings, closed = true, ::screenX, ::screenY), pattern)
        }
    }

    for (layer in tessellation.layers) {
        if (!layer.isShownAt(viewport.zoom)) continue
        for (feature in layer.featuresIn(view)) {
            val strokeArgb = feature.strokeArgb ?: continue
            val path = Path()
            for (ring in feature.worldRings) {
                val pointCount = ring.size / 2
                if (pointCount < 2) continue
                path.moveTo(screenX(ring[0]), screenY(ring[1]))
                for (index in 1 until pointCount) {
                    path.lineTo(screenX(ring[index * 2]), screenY(ring[index * 2 + 1]))
                }
                if (layer.kind == GeomapLayerKind.FILL) path.close()
            }
            val dashEffect = feature.dash?.let { intervals ->
                PathEffect.dashPathEffect(intervals.map { it * density }.toFloatArray())
            }
            drawPath(path, color = Color(strokeArgb), style = Stroke(width = feature.strokeWidth * density, pathEffect = dashEffect))
        }
    }

    if (selection.isNotEmpty()) {
        for (layer in tessellation.layers) {
            if (!layer.isShownAt(viewport.zoom)) continue
            for (feature in layer.featuresIn(view)) {
                if (feature.featureId !in selection) continue
                drawPath(
                    screenPathOf(feature.worldRings, closed = layer.kind == GeomapLayerKind.FILL, ::screenX, ::screenY),
                    color = Color(selectionStyle.strokeArgb),
                    style = Stroke(width = selectionStyle.strokeWidth * density),
                )
            }
        }
    }

    for (layer in tessellation.layers) {
        if (!layer.isShownAt(viewport.zoom)) continue
        for (feature in layer.featuresIn(view)) {
            val label = feature.label ?: continue
            val measured = labelMeasurer.measure(label, TextStyle(color = labelInk, fontSize = LABEL_FONT_SIZE))
            // The whole label box must sit on the county's own land, which is also what keeps eleven
            // thousand parcel names off a county-scale view without a separate zoom threshold.
            val anchor = placeGeomapLabel(
                feature.labelCandidates,
                feature.worldRings,
                halfWidthWorld = measured.size.width / 2.0 / scale / LABEL_FIT,
                halfHeightWorld = measured.size.height / 2.0 / scale / LABEL_FIT,
            ) ?: continue
            drawText(
                measured,
                topLeft = Offset(screenX(anchor.x) - measured.size.width / 2f, screenY(anchor.y) - measured.size.height / 2f),
            )
        }
    }
}

private fun worldBoundsOfRequest(request: GeomapBoundsRequest): WorldBounds? = worldBoundsOf(
    listOf(listOf(GeoPoint(request.minLon, request.minLat), GeoPoint(request.maxLon, request.maxLat))),
)

private fun screenPathOf(
    worldRings: List<DoubleArray>,
    closed: Boolean,
    screenX: (Double) -> Float,
    screenY: (Double) -> Float,
): Path {
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    for (ring in worldRings) {
        val pointCount = ring.size / 2
        if (pointCount < 2) continue
        path.moveTo(screenX(ring[0]), screenY(ring[1]))
        for (index in 1 until pointCount) path.lineTo(screenX(ring[index * 2]), screenY(ring[index * 2 + 1]))
        if (closed) path.close()
    }
    return path
}

@Composable
private fun GeomapLegend(
    entries: List<GeomapLegendEntry>,
    nodeId: String,
    isLayerShown: (String) -> Boolean,
    onToggleLayer: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp).background(LocalSpecTheme.current.ground.copy(alpha = 0.9f)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            // An entry that names a layer is its switch; one that names none — a class within a
            // single choropleth — is a key only, and tapping it does nothing.
            val layerId = entry.layer
            val shown = layerId == null || isLayerShown(layerId)
            val rowModifier = Modifier.nodeTag("$nodeId:legend:${layerId ?: index}")
                .let { base ->
                    if (layerId == null) base
                    else base.clickable(interactionSource = null, indication = null) { onToggleLayer(layerId) }
                }
                .alpha(if (shown) 1f else 0.35f)
            Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(width = 28.dp, height = 18.dp)) {
                    val swatch = Path().apply { addRect(Rect(Offset.Zero, size)) }
                    entry.fill?.let(::parseGeomapColor)?.let { drawPath(swatch, Color(it)) }
                    entry.pattern?.let { paintGeomapPattern(swatch, it) }
                }
                Text(
                    text = entry.label,
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSpecTheme.current.ink,
                )
            }
        }
    }
}

private class GeomapSelectionStyle(val strokeArgb: Int, val strokeWidth: Float)

private const val DEFAULT_SELECTION_STROKE_WIDTH = 3f

internal fun selectionAfterTap(selection: Set<String>, hit: GeomapHit?): List<String> = when {
    hit == null -> emptyList()
    selection == setOf(hit.featureId) -> emptyList()
    else -> listOf(hit.featureId)
}

private fun featureTapAction(action: String, hit: GeomapHit) = ActionRef(
    action = action,
    params = mapOf(
        "layer_id" to SpecValue.StringValue(hit.layerId),
        "feature_id" to SpecValue.StringValue(hit.featureId),
    ),
)

private fun viewportChangedAction(action: String, viewport: GeomapViewport) = ActionRef(
    action = action,
    params = mapOf(
        "lon" to SpecValue.NumberValue(viewport.lon),
        "lat" to SpecValue.NumberValue(viewport.lat),
        "zoom" to SpecValue.NumberValue(viewport.zoom.toDouble()),
    ),
)
