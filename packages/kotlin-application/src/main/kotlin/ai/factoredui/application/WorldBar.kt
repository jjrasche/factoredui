package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

private const val OPEN_SPEC = "open_spec"
private const val SPEC_ROUTE = "/spec.json"
private const val WORLD_PATH = "nav.world"
private const val VIEWS_PER_ROW = 3

private fun encoded(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

internal fun specTarget(worldId: String, viewId: String? = null): String =
    "$SPEC_ROUTE?world=${encoded(worldId)}" + (viewId?.let { "&view=${encoded(it)}" } ?: "")

private fun openSpec(target: String): Map<String, Any?> = mapOf("action" to OPEN_SPEC, "params" to mapOf("spec" to target))

private fun withAction(node: JsonObject, action: Map<String, Any?>): JsonObject = JsonObject(node + ("action" to jsonOf(action)))

private fun viewChip(world: EngineWorld, view: WorldView, isChosen: Boolean): JsonObject =
    withAction(
        specNode("views.${view.id}", "button", mapOf("label" to view.label, "variant" to if (isChosen) "primary" else "outline", "size" to "sm", "flex" to 1)),
        openSpec(specTarget(world.id, view.id)),
    )

private fun worldSelect(labels: List<WorldLabel>): JsonObject =
    withAction(
        specNode(
            "bar.world", "select",
            mapOf("value" to "{$WORLD_PATH}", "placeholder" to "Choose a world", "options" to labels.map { mapOf("value" to specTarget(it.id), "label" to it.short) }),
        ),
        openSpec("{$WORLD_PATH}"),
    )

private fun viewRows(world: EngineWorld, views: List<WorldView>, chosen: WorldView?): List<JsonObject> =
    views.map { viewChip(world, it, it.id == chosen?.id) }.chunked(VIEWS_PER_ROW).mapIndexed { index, row -> specNode("bar.views.$index", "row", mapOf("gap" to 4), row) }

internal fun worldBar(labels: List<WorldLabel>, chosen: EngineWorld?, views: List<WorldView>, chosenView: WorldView?): JsonObject {
    val title = labels.firstOrNull { it.id == chosen?.id }?.title?.let { textNode("bar.title", it, variant = "caption", color = MUTED) }
    val viewRows = if (chosen != null && views.size > 1) viewRows(chosen, views, chosenView) else emptyList()
    return specNode("bar", "column", mapOf("gap" to 8), listOfNotNull(worldSelect(labels), title) + viewRows)
}

internal fun barState(chosen: EngineWorld?): Map<String, Any?> = chosen?.let { mapOf(WORLD_PATH to specTarget(it.id)) } ?: emptyMap()

internal fun noticePage(bar: JsonObject, message: String, state: Map<String, Any?> = emptyMap()): JsonObject {
    val root = specNode("root", "column", mapOf("gap" to 12, "padding" to 16, "background" to HUD), listOf(bar, textNode("notice", message, color = INK)))
    return JsonObject(mapOf("spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"), "state" to jsonOf(state), "root" to root))
}

private fun childrenOf(node: JsonObject): List<JsonObject> = (node["children"] as JsonArray).map { it as JsonObject }

private fun withChildren(node: JsonObject, children: List<JsonObject>): JsonObject = JsonObject(node + ("children" to JsonArray(children)))

private fun mergedState(spec: JsonObject, state: Map<String, Any?>): JsonObject = JsonObject((spec["state"] as? JsonObject ?: JsonObject(emptyMap())) + (jsonOf(state) as JsonObject))

internal fun withWorldBar(spec: JsonObject, bar: JsonObject, state: Map<String, Any?> = emptyMap()): JsonObject {
    val root = spec["root"] as JsonObject
    val children = childrenOf(root)
    val dock = children.firstOrNull { (it["id"] as JsonPrimitive).content == "dock" }
    val barred = if (dock == null) listOf(bar) + children else children.map { if (it === dock) withChildren(dock, listOf(bar) + childrenOf(dock)) else it }
    return JsonObject(spec + mapOf("root" to withChildren(root, barred), "state" to mergedState(spec, state)))
}
