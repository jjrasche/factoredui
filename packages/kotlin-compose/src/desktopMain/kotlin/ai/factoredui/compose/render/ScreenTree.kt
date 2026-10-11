package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.CanvasPlacement
import ai.factoredui.compose.renderer.NodeRecord
import ai.factoredui.compose.renderer.ScenePartRecord
import ai.factoredui.compose.renderer.SceneRecord
import ai.factoredui.compose.renderer.ScreenBounds
import ai.factoredui.compose.renderer.ScreenRegistry
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.asListProps
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.Point
import kotlin.math.roundToInt

class ScreenNode(val record: NodeRecord, val children: List<ScreenNode>)

/** The drawn tree in spec order; a list's template appears once per row, its paths prefixed with the list's path and the row index. */
fun screenNodes(registry: ScreenRegistry): ScreenNode? = registry.root?.let { nodeAt(registry, it, "") }

private fun nodeAt(registry: ScreenRegistry, node: SpecNode, prefix: String): ScreenNode? {
    val path = prefix + node.id
    val record = registry.recordOf(path) ?: return null
    return ScreenNode(record, childrenOf(registry, node, record, prefix))
}

private fun childrenOf(registry: ScreenRegistry, node: SpecNode, record: NodeRecord, prefix: String): List<ScreenNode> {
    val template = if (node.type == SpecNodeType.LIST) node.props.asListProps().itemTemplate else null
    val rows = record.rowCount
    if (template != null && rows != null) return (0 until rows).mapNotNull { index -> nodeAt(registry, template, "${record.path}/$index/") }
    return node.children.mapNotNull { nodeAt(registry, it, prefix) }
}

fun findNode(root: ScreenNode, path: String): ScreenNode? =
    if (root.record.path == path) root else root.children.firstNotNullOfOrNull { findNode(it, path) }

fun visibleOnly(node: ScreenNode): ScreenNode? =
    if (!node.record.visible) null else ScreenNode(node.record, node.children.mapNotNull(::visibleOnly))

private fun allNodes(node: ScreenNode): List<ScreenNode> = listOf(node) + node.children.flatMap(::allNodes)

fun idsShowing(root: ScreenNode, substring: String): List<String> =
    allNodes(root).filter { it.record.text?.contains(substring) == true }.map { it.record.path }

private fun hexOf(colour: Long): String = "#" + (colour and 0xFFFFFF).toString(16).padStart(6, '0')

private fun boundsJson(bounds: ScreenBounds?): JsonElement = bounds?.let {
    buildJsonObject {
        put("x", it.x)
        put("y", it.y)
        put("w", it.w)
        put("h", it.h)
    }
} ?: JsonNull

fun valueJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map(::valueJson))
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to valueJson(it.value) })
    else -> JsonPrimitive(value.toString())
}

fun partBounds(part: ScenePartRecord, canvas: CanvasPlacement?): ScreenBounds? {
    canvas ?: return null
    val left = canvas.originX + part.canvasLeft / canvas.pixelsPerUnit
    val top = canvas.originY + part.canvasTop / canvas.pixelsPerUnit
    val right = canvas.originX + part.canvasRight / canvas.pixelsPerUnit
    val bottom = canvas.originY + part.canvasBottom / canvas.pixelsPerUnit
    return ScreenBounds(left.roundToInt(), top.roundToInt(), (right - left).roundToInt(), (bottom - top).roundToInt())
}

fun partCentre(part: ScenePartRecord, canvas: CanvasPlacement): Point =
    Point((canvas.originX + part.canvasCentreX / canvas.pixelsPerUnit).roundToInt(), (canvas.originY + part.canvasCentreY / canvas.pixelsPerUnit).roundToInt())

private fun partJson(part: ScenePartRecord, canvas: CanvasPlacement?): JsonObject = buildJsonObject {
    put("id", part.id)
    put("type_id", part.typeId)
    put("level", part.level)
    put("fill", hexOf(part.fill))
    put("outline", part.outline)
    put("value", valueJson(part.value))
    put("bounds", boundsJson(partBounds(part, canvas)))
    canvas?.let { put("centre", buildJsonObject { partCentre(part, it).let { point -> put("x", point.x); put("y", point.y) } }) }
}

private fun sceneJson(scene: SceneRecord, canvas: CanvasPlacement?): JsonObject = buildJsonObject {
    put("view", scene.view)
    put("focus", scene.focus)
    put("hide_top", scene.hideTop)
    put("show_lower", scene.showLower)
    put("y_axis", scene.yAxis)
    put("active_scheme", valueJson(scene.activeScheme))
    put("legend", buildJsonArray { scene.legend.forEach { entry -> add(buildJsonObject { put("text", entry.text); put("color", hexOf(entry.colour)) }) } })
    put("notices", JsonArray(scene.notices.map(::JsonPrimitive)))
    put("parts", JsonArray(scene.parts.map { partJson(it, canvas) }))
    put("popup", valueJson(scene.popup))
}

fun screenJson(node: ScreenNode): JsonObject = buildJsonObject {
    val record = node.record
    put("id", record.path)
    put("type", record.type)
    put("text", valueJson(record.text))
    put("props", JsonObject(record.boundProps.mapValues { valueJson(it.value) }))
    put("visible", record.visible)
    put("enabled", record.enabled)
    put("action", valueJson(record.action))
    put("bounds", boundsJson(record.bounds))
    if (record.notices.isNotEmpty()) put("notices", JsonArray(record.notices.map(::JsonPrimitive)))
    record.scene?.let { put("scene", sceneJson(it, record.canvas)) }
    put("children", JsonArray(node.children.map(::screenJson)))
}

/** Where a click on [id] lands: a node's centre, or for "<scene node>/<part id>" the part's centre. */
fun clickPointFor(registry: ScreenRegistry, id: String): Point {
    val record = registry.recordOf(id)
    if (record != null) {
        val bounds = record.bounds ?: error("'$id' is drawn with no bounds")
        require(bounds.hasSize) { "'$id' is drawn with no size" }
        return Point(bounds.centreX, bounds.centreY)
    }
    val scenePath = id.substringBeforeLast('/', missingDelimiterValue = "")
    val partId = id.substringAfterLast('/')
    val scene = registry.recordOf(scenePath)?.takeIf { it.scene != null } ?: error("no node or scene part has id '$id'")
    val part = scene.scene!!.parts.firstOrNull { it.id == partId } ?: error("scene '$scenePath' draws no part '$partId'")
    val canvas = scene.canvas ?: error("scene '$scenePath' has not been placed yet")
    val bounds = partBounds(part, canvas)
    require(bounds != null && bounds.hasSize) { "part '$partId' is drawn with no size" }
    return partCentre(part, canvas)
}
