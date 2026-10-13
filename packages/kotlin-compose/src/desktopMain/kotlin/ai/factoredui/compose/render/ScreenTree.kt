package ai.factoredui.compose.render

import ai.factoredui.compose.renderer.CanvasPlacement
import ai.factoredui.compose.renderer.MenuItemRecord
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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    if (record.syntheticChildren.isNotEmpty()) return record.syntheticChildren.mapNotNull { (childPrefix, child) -> nodeAt(registry, child, childPrefix) }
    val template = if (node.type == SpecNodeType.LIST) node.props.asListProps().itemTemplate else null
    val rows = record.rowCount
    if (template != null && rows != null) return (0 until rows).mapNotNull { index -> nodeAt(registry, template, "${record.path}/$index/") }
    return node.children.mapNotNull { nodeAt(registry, it, prefix) }
}

fun visibleOnly(node: ScreenNode): ScreenNode? =
    if (!node.record.visible) null else ScreenNode(node.record, node.children.mapNotNull(::visibleOnly))

private fun childrenOf(tree: JsonObject): List<JsonObject> = tree.getValue("children").jsonArray.map { it.jsonObject }

private fun everyNode(tree: JsonObject): List<JsonObject> = listOf(tree) + childrenOf(tree).flatMap(::everyNode)

private fun idOf(tree: JsonObject): String = tree.getValue("id").jsonPrimitive.content

private fun drawnTextOf(tree: JsonObject): String? = (tree["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Any node the tree emits by its id, synthetic children (an empty message, an open menu's options) included. */
fun nodeById(tree: JsonObject, id: String): JsonObject? = everyNode(tree).firstOrNull { idOf(it) == id }

/** A scene part by "<scene node id>/<part id>": the part as the scene lists it, addressed by that id and typed part. */
fun scenePartById(tree: JsonObject, id: String): JsonObject? {
    val scene = nodeById(tree, id.substringBeforeLast('/', missingDelimiterValue = ""))?.get("scene") as? JsonObject ?: return null
    val partId = id.substringAfterLast('/')
    val part = scene.getValue("parts").jsonArray.map { it.jsonObject }.firstOrNull { idOf(it) == partId } ?: return null
    return JsonObject(mapOf("id" to JsonPrimitive(id), "type" to JsonPrimitive("part")) + part.filterKeys { it != "id" })
}

/** The nodes whose drawn text holds [substring]: their ids, and each as {id, text, bounds}. */
fun textHits(tree: JsonObject, substring: String): JsonObject {
    val hits = everyNode(tree).filter { drawnTextOf(it)?.contains(substring) == true }
    return buildJsonObject {
        put("ids", JsonArray(hits.map { JsonPrimitive(idOf(it)) }))
        put("hits", JsonArray(hits.map { hit -> JsonObject(mapOf("id" to hit.getValue("id"), "text" to hit.getValue("text"), "bounds" to hit.getValue("bounds"))) }))
    }
}

private fun hexOf(colour: Long): String = "#" + (colour and 0xFFFFFF).toString(16).padStart(6, '0')

private fun hexWithAlphaOf(colour: Long): String {
    val alpha = (colour shr 24) and 0xFF
    return if (alpha == 0xFFL) hexOf(colour) else hexOf(colour) + alpha.toString(16).padStart(2, '0')
}

private fun boundsJson(bounds: ScreenBounds?): JsonElement = bounds?.let {
    buildJsonObject {
        put("x", it.x)
        put("y", it.y)
        put("w", it.w)
        put("h", it.h)
    }
} ?: JsonNull

/** A node with no place on screen (clipped out of view, or not placed yet) is not composed and has null bounds. */
private fun JsonObjectBuilder.putPlacement(bounds: ScreenBounds?) {
    put("composed", bounds != null)
    put("bounds", boundsJson(bounds))
}

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
    put("covered_by", JsonArray(part.coveredBy.map(::JsonPrimitive)))
    canvas?.let { put("centre", buildJsonObject { partCentre(part, it).let { point -> put("x", point.x); put("y", point.y) } }) }
}

private fun sceneJson(scene: SceneRecord, canvas: CanvasPlacement?): JsonObject = buildJsonObject {
    put("view", scene.view)
    put("focus", scene.focus)
    put("hide_top", scene.hideTop)
    put("show_lower", scene.showLower)
    put("y_axis", scene.yAxis)
    put("north", valueJson(scene.north))
    put("active_scheme", valueJson(scene.activeScheme))
    put("legend", buildJsonArray { scene.legend.forEach { entry -> add(buildJsonObject { put("text", entry.text); put("color", hexOf(entry.colour)) }) } })
    put("notices", JsonArray(scene.notices.map(::JsonPrimitive)))
    put("parts", JsonArray(scene.parts.map { partJson(it, canvas) }))
    put("popup", valueJson(scene.popup))
}

fun optionPath(selectPath: String, value: String): String = "$selectPath/option/$value"

private fun optionJson(record: NodeRecord, item: MenuItemRecord): JsonObject = buildJsonObject {
    put("id", optionPath(record.path, item.value))
    put("type", "option")
    put("text", item.label)
    put("props", buildJsonObject { put("value", item.value) })
    put("visible", true)
    put("enabled", true)
    put("action", JsonNull)
    put("selected", item.selected)
    putPlacement(record.menuBounds[item.value])
    put("children", JsonArray(emptyList()))
}

private fun emptyJson(record: NodeRecord, message: String): JsonObject = buildJsonObject {
    put("id", "${record.path}/empty")
    put("type", "empty")
    put("text", message)
    put("props", JsonObject(emptyMap()))
    put("visible", true)
    put("enabled", true)
    put("action", JsonNull)
    putPlacement(record.emptyBounds)
    put("children", JsonArray(emptyList()))
}

private fun drawnChildren(node: ScreenNode): List<JsonObject> {
    val record = node.record
    val options = record.menu.orEmpty().map { optionJson(record, it) }
    val empty = listOfNotNull(record.emptyMessage?.let { emptyJson(record, it) })
    return node.children.map(::screenJson) + options + empty
}

/** The tree to [depth] levels below its top; a node at the limit keeps its own fields and says how many children it left out. */
fun limitedTo(tree: JsonObject, depth: Int): JsonObject {
    val children = tree.getValue("children").jsonArray
    if (depth > 0) return JsonObject(tree + ("children" to JsonArray(children.map { limitedTo(it.jsonObject, depth - 1) })))
    if (children.isEmpty()) return tree
    return JsonObject(tree + ("children" to JsonArray(emptyList())) + ("children_omitted" to JsonPrimitive(children.size)))
}

fun screenJson(node: ScreenNode): JsonObject = buildJsonObject {
    val record = node.record
    put("id", record.path)
    put("type", record.type)
    put("text", valueJson(record.text))
    put("props", JsonObject((record.boundProps + record.shownProps).toSortedMap().mapValues { valueJson(it.value) }))
    put("visible", record.visible)
    put("enabled", record.enabled)
    put("action", valueJson(record.action))
    putPlacement(record.bounds)
    if (record.isBoundToNull) put("null", true)
    record.fill?.let { put("fill", hexWithAlphaOf(it)) }
    record.ink?.let { put("ink", hexOf(it)) }
    if (record.notices.isNotEmpty()) put("notices", JsonArray(record.notices.map(::JsonPrimitive)))
    record.scene?.let { put("scene", sceneJson(it, record.canvas)) }
    put("children", JsonArray(drawnChildren(node)))
}

sealed interface ClickTarget {
    data class AtPoint(val point: Point) : ClickTarget
    data class ScenePart(val scenePath: String, val partId: String) : ClickTarget
}

private fun nodeCentre(id: String, bounds: ScreenBounds?): Point {
    bounds ?: error("'$id' is drawn with no bounds")
    require(bounds.hasSize) { "'$id' is drawn with no size" }
    return Point(bounds.centreX, bounds.centreY)
}

private fun optionTarget(registry: ScreenRegistry, id: String): ClickTarget? {
    val selectPath = id.substringBefore("/option/", missingDelimiterValue = "")
    val select = registry.recordOf(selectPath)?.takeIf { it.type == "select" } ?: return null
    val value = id.substringAfter("/option/")
    val menu = select.menu ?: error("select '$selectPath' is not open")
    require(menu.any { it.value == value }) { "select '$selectPath' has no option '$value'" }
    return ClickTarget.AtPoint(nodeCentre(id, select.menuBounds[value]))
}

/** A node or an open select's option is clicked at its centre; a scene part is tapped by its id, so a part under another is still the one tapped. */
fun clickTargetFor(registry: ScreenRegistry, id: String): ClickTarget {
    registry.recordOf(id)?.let { return ClickTarget.AtPoint(nodeCentre(id, it.bounds)) }
    optionTarget(registry, id)?.let { return it }
    val scenePath = id.substringBeforeLast('/', missingDelimiterValue = "")
    val partId = id.substringAfterLast('/')
    val scene = registry.recordOf(scenePath)?.scene ?: error("no node or scene part has id '$id'")
    require(scene.parts.any { it.id == partId }) { "scene '$scenePath' draws no part '$partId'" }
    return ClickTarget.ScenePart(scenePath, partId)
}

fun tapScenePart(registry: ScreenRegistry, target: ClickTarget.ScenePart): String {
    val tap = registry.recordOf(target.scenePath)?.partTap ?: error("scene '${target.scenePath}' takes no taps")
    require(tap(target.partId)) { "scene '${target.scenePath}' draws no part '${target.partId}'" }
    return "tapped ${target.scenePath}/${target.partId}"
}

private fun isMenuPlaced(record: NodeRecord): Boolean =
    record.menu.orEmpty().all { item -> item.value in record.menuComposed && record.menuBounds[item.value]?.hasSize == true }

private fun isMenuGone(record: NodeRecord): Boolean = record.menu == null && record.menuComposed.isEmpty()

/** An open menu whose items are not all placed yet is a popup still composing. */
fun openMenusArePlaced(registry: ScreenRegistry): Boolean = registry.paths().mapNotNull(registry::recordOf).all(::isMenuPlaced)

/** What a click on [id] should leave on screen once drawn: a closed select open with its items placed, an option's menu closed. */
fun clickOutcomeOf(registry: ScreenRegistry, id: String): (ScreenRegistry) -> Boolean {
    val select = registry.recordOf(id)?.takeIf { it.type == "select" && it.menu == null }
    if (select != null) return { screen -> screen.recordOf(id)?.let { it.menu != null && isMenuPlaced(it) } ?: true }
    val selectPath = id.substringBefore("/option/", missingDelimiterValue = "")
    if (selectPath.isNotEmpty() && registry.recordOf(selectPath)?.menu != null) return { screen -> screen.recordOf(selectPath)?.let(::isMenuGone) ?: true }
    return { true }
}
