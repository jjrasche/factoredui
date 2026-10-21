package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

private const val OPEN_SPEC = "open_spec"
private const val SPEC_ROUTE = "/spec.json"

private fun encoded(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

internal fun specTarget(worldId: String, viewId: String? = null): String =
    "$SPEC_ROUTE?world=${encoded(worldId)}" + (viewId?.let { "&view=${encoded(it)}" } ?: "")

private const val WORLDS_PER_ROW = 2
private const val VIEWS_PER_ROW = 3

private fun chip(id: String, label: String, target: String, isChosen: Boolean): JsonObject =
    JsonObject(
        specNode(id, "button", mapOf("label" to label, "variant" to if (isChosen) "primary" else "outline", "size" to "sm", "flex" to 1)) +
            ("action" to jsonOf(mapOf("action" to OPEN_SPEC, "params" to mapOf("spec" to target)))),
    )

private fun chipRows(group: String, chips: List<JsonObject>, perRow: Int): List<JsonObject> =
    chips.chunked(perRow).mapIndexed { index, row -> specNode("bar.$group.$index", "row", mapOf("gap" to 4), row) }

private fun worldRows(worlds: List<EngineWorld>, chosen: EngineWorld?): List<JsonObject> =
    chipRows("worlds", worlds.map { chip("worlds.${it.id}", it.id, specTarget(it.id), it.id == chosen?.id) }, WORLDS_PER_ROW)

private fun viewRows(world: EngineWorld, views: List<WorldView>, chosen: WorldView?): List<JsonObject> =
    chipRows("views", views.map { chip("views.${it.id}", it.label, specTarget(world.id, it.id), it.id == chosen?.id) }, VIEWS_PER_ROW)

internal fun worldBar(worlds: List<EngineWorld>, chosen: EngineWorld?, views: List<WorldView>, chosenView: WorldView?): JsonObject {
    val viewRows = if (chosen != null && views.size > 1) viewRows(chosen, views, chosenView) else emptyList()
    return specNode("bar", "column", mapOf("gap" to 4), worldRows(worlds, chosen) + viewRows)
}

internal fun noticePage(bar: JsonObject, message: String): JsonObject {
    val root = specNode("root", "column", mapOf("gap" to 12, "padding" to 16, "background" to HUD), listOf(bar, textNode("notice", message, color = INK)))
    return JsonObject(mapOf("spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "root" to root))
}

private fun childrenOf(node: JsonObject): List<JsonObject> = (node["children"] as JsonArray).map { it as JsonObject }

private fun withChildren(node: JsonObject, children: List<JsonObject>): JsonObject = JsonObject(node + ("children" to JsonArray(children)))

internal fun withWorldBar(spec: JsonObject, bar: JsonObject): JsonObject {
    val root = spec["root"] as JsonObject
    val children = childrenOf(root)
    val dock = children.firstOrNull { (it["id"] as JsonPrimitive).content == "dock" }
    val barred = if (dock == null) listOf(bar) + children else children.map { if (it === dock) withChildren(dock, listOf(bar) + childrenOf(dock)) else it }
    return JsonObject(spec + ("root" to withChildren(root, barred)))
}
