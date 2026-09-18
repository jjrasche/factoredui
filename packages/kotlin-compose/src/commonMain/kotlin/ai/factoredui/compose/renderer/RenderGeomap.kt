package ai.factoredui.compose.renderer

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
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
import kotlinx.coroutines.launch

private const val DEFAULT_STROKE_ARGB = 0xFF2C3E50.toInt()

internal class TessellatedFeature(
    val featureId: String,
    val fillArgb: Int?,
    val strokeArgb: Int?,
    val strokeWidth: Float,
    val worldRings: List<DoubleArray>,
    val triangleWorld: DoubleArray,
)

internal class TessellatedLayer(
    val layerId: String,
    val kind: GeomapLayerKind,
    val visible: Boolean,
    val features: List<TessellatedFeature>,
)

internal class GeomapTessellation(
    val layers: List<TessellatedLayer>,
    val worldBounds: WorldBounds?,
)

internal data class GeomapHit(val layerId: String, val featureId: String)

@Composable
internal fun RenderGeomap(node: SpecNode, resolvedProps: Map<String, Any?>, context: RenderContext) {
    val props = node.props.asGeomapProps()
    val layersData = resolvedProps["layers"]
    val tessellation = remember(layersData) { tessellateGeomapLayers(resolveGeomapLayers(layersData)) }
    val hostViewport = resolveGeomapViewport(resolvedProps["viewport"])
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize().nodeTag(node.id).clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        var viewport by remember(widthPx, heightPx) {
            mutableStateOf(hostViewport ?: fitGeomapViewport(tessellation.worldBounds, widthPx, heightPx))
        }
        LaunchedEffect(hostViewport) { hostViewport?.let { viewport = it } }
        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(tessellation, props.onFeatureTap) {
                    val onFeatureTap = props.onFeatureTap
                    detectTapGestures(onTap = { offset ->
                        val hit = hitTestGeomap(tessellation, viewport, widthPx, heightPx, offset.x, offset.y)
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
            drawGeomapTessellation(tessellation, viewport, widthPx, heightPx)
        }
    }
}

internal fun tessellateGeomapLayers(layers: List<GeomapLayer>): GeomapTessellation {
    var bounds: WorldBounds? = null
    val tessellatedLayers = layers.map { layer ->
        val features = layer.features.map { feature ->
            worldBoundsOf(feature.rings)?.let { featureBounds ->
                bounds = bounds?.union(featureBounds) ?: featureBounds
            }
            tessellateFeature(feature, layer.kind)
        }
        TessellatedLayer(layer.id, layer.kind, layer.visible, features)
    }
    return GeomapTessellation(tessellatedLayers, bounds)
}

private fun tessellateFeature(feature: GeomapFeature, kind: GeomapLayerKind): TessellatedFeature {
    val worldRings = feature.rings.map { ring ->
        DoubleArray(ring.size * 2).also { flat ->
            ring.forEachIndexed { index, point ->
                flat[index * 2] = lonToWorldX(point.lon)
                flat[index * 2 + 1] = latToWorldY(point.lat)
            }
        }
    }
    val fillArgb = if (kind == GeomapLayerKind.FILL) parseGeomapColor(feature.fill) else null
    val triangleWorld = if (fillArgb != null) expandTriangles(worldRings) else DoubleArray(0)
    return TessellatedFeature(
        featureId = feature.id,
        fillArgb = fillArgb,
        strokeArgb = parseGeomapColor(feature.stroke)
            ?: if (kind == GeomapLayerKind.LINE) parseGeomapColor(feature.fill) ?: DEFAULT_STROKE_ARGB else null,
        strokeWidth = feature.strokeWidth,
        worldRings = worldRings,
        triangleWorld = triangleWorld,
    )
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
        if (!layer.visible) continue
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
) {
    val scale = geomapScalePx(viewport.zoom)
    val centerX = lonToWorldX(viewport.lon)
    val centerY = latToWorldY(viewport.lat)
    fun screenX(worldX: Double): Float = ((worldX - centerX) * scale + widthPx / 2.0).toFloat()
    fun screenY(worldY: Double): Float = ((worldY - centerY) * scale + heightPx / 2.0).toFloat()

    drawIntoCanvas { canvas ->
        for (layer in tessellation.layers) {
            if (!layer.visible || layer.kind != GeomapLayerKind.FILL) continue
            var vertexCount = 0
            for (feature in layer.features) vertexCount += feature.triangleWorld.size / 2
            if (vertexCount < 3) continue
            val positions = FloatArray(vertexCount * 2)
            val colors = IntArray(vertexCount)
            var vertex = 0
            for (feature in layer.features) {
                val fill = feature.fillArgb ?: continue
                val triangles = feature.triangleWorld
                for (index in 0 until triangles.size / 2) {
                    positions[vertex * 2] = screenX(triangles[index * 2])
                    positions[vertex * 2 + 1] = screenY(triangles[index * 2 + 1])
                    colors[vertex] = fill
                    vertex++
                }
            }
            drawTriangleBatch(canvas, positions, colors, vertex)
        }
    }

    for (layer in tessellation.layers) {
        if (!layer.visible) continue
        for (feature in layer.features) {
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
            drawPath(path, color = Color(strokeArgb), style = Stroke(width = feature.strokeWidth * density))
        }
    }
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
