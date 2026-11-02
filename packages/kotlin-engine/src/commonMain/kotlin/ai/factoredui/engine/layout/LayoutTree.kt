package ai.factoredui.engine.layout

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

data class Box(val x: Double, val y: Double, val w: Double, val h: Double) {
    val right: Double get() = x + w
    val bottom: Double get() = y + h
    val area: Double get() = w * h
}

/** One drawn node: where it shows, its colours and what it is, with the id of the node that holds it. */
data class DrawnNode(
    val id: String,
    val type: String,
    val parentId: String?,
    val box: Box?,
    val isVisible: Boolean,
    val hasAction: Boolean,
    val fillHex: String?,
    val inkHex: String?,
    val text: String?,
    val stop: String?,
)

val CONTROL_TYPES = setOf("button", "toggle", "select", "slider", "textinput", "chip", "tabs")
val MAIN_VIEW_TYPES = setOf("geomap", "plan", "scene3d", "scene", "canvas", "chart", "graph", "heatmap", "volume", "table")
val OVERLAY_TYPES = setOf("sheet", "modal")

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun readBox(bounds: JsonElement?): Box? {
    val box = bounds.asObject() ?: return null
    fun number(key: String): Double? = (box[key] as? JsonPrimitive)?.doubleOrNull
    return Box(number("x") ?: return null, number("y") ?: return null, number("w") ?: return null, number("h") ?: return null)
}

private fun readNode(node: JsonObject, parentId: String?): DrawnNode {
    val props = node["props"].asObject()
    return DrawnNode(
        id = node["id"].asText().orEmpty(),
        type = node["type"].asText().orEmpty(),
        parentId = parentId,
        box = if ((node["composed"] as? JsonPrimitive)?.booleanOrNull == false) null else readBox(node["bounds"]),
        isVisible = (node["visible"] as? JsonPrimitive)?.booleanOrNull != false,
        hasAction = node["action"] != null && node["action"] !is JsonNull,
        fillHex = node["fill"].asText(),
        inkHex = node["ink"].asText(),
        text = node["text"].asText(),
        stop = props?.get("stop").asText(),
    )
}

/** Every node of the /tree, parents before children. */
fun flattenTree(tree: JsonObject, parentId: String? = null): List<DrawnNode> {
    val node = readNode(tree, parentId)
    val children = (tree["children"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    return listOf(node) + children.flatMap { flattenTree(it, node.id) }
}

fun DrawnNode.isShown(): Boolean = isVisible && box != null && box.w > 0 && box.h > 0
