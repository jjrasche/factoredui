package ai.factoredui.worldengine.validate

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.pythonEquals
import ai.factoredui.worldengine.json.pythonInt
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.text.pythonStr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val INDENTED_JSON = Json { prettyPrint = true }

fun applyMutation(files: Map<String, String>, mutation: JsonObject): Map<String, String> {
    val edited = LinkedHashMap(files)
    (mutation["edits"] as? JsonArray ?: JsonArray(emptyList())).forEach { applyFileEdit(edited, mutation["world"], it as JsonObject) }
    return edited
}

private fun applyFileEdit(files: MutableMap<String, String>, worldElement: JsonElement?, edit: JsonObject) {
    val operation = edit.requiredText("op")
    if (operation == "delete_all_worlds") {
        files.keys.filter { it.endsWith(".world.json") }.forEach { files.remove(it) }
        return
    }
    val target = worldElement?.let { pythonStr(it) } ?: throw MalformedDataException("a mutation edit names no world")
    if (operation == "write_text") {
        files[target] = edit.requiredText("value")
        return
    }
    val document = Json.parseToJsonElement(files[target] ?: throw MalformedDataException("no world file $target"))
    files[target] = INDENTED_JSON.encodeToString(JsonElement.serializer(), applyDocumentEdit(document, edit))
}

fun applyDocumentEdit(document: JsonElement, edit: JsonObject): JsonElement {
    val path = edit["path"] as? JsonArray ?: throw MalformedDataException("an edit has no path")
    val leaf: (JsonElement?) -> JsonElement? = when (val operation = edit.requiredText("op")) {
        "set" -> { _ -> edit["value"] ?: throw MalformedDataException("a set edit has no value") }
        "set_repeated" -> { _ -> JsonPrimitive(edit.requiredText("unit").repeat(pythonInt(edit["times"]).toInt()) + edit.requiredText("tail")) }
        "set_items" -> { _ -> numberedItems(edit) }
        "delete" -> { _ -> null }
        else -> throw MalformedDataException("mutations.json uses unknown op $operation")
    }
    return rewriteAt(document, path.toList(), leaf) ?: throw MalformedDataException("an edit deleted the whole document")
}

private fun rewriteAt(node: JsonElement, steps: List<JsonElement>, leaf: (JsonElement?) -> JsonElement?): JsonElement? {
    val step = steps.first()
    val rest = steps.drop(1)
    return when (node) {
        is JsonObject -> rewriteObjectMember(node, pythonStr(step), rest, leaf)
        is JsonArray -> rewriteArrayMember(node, indexOfStep(node, step), rest, leaf)
        else -> throw MalformedDataException("a path step reaches into a value that is not a list or an object")
    }
}

private fun rewriteObjectMember(node: JsonObject, key: String, rest: List<JsonElement>, leaf: (JsonElement?) -> JsonElement?): JsonElement {
    val replaced = if (rest.isEmpty()) leaf(node[key]) else rewriteAt(node[key] ?: throw MalformedDataException("'$key'"), rest, leaf)
    val members = LinkedHashMap(node)
    if (replaced == null) members.remove(key) else members[key] = replaced
    return JsonObject(members)
}

private fun rewriteArrayMember(node: JsonArray, index: Int, rest: List<JsonElement>, leaf: (JsonElement?) -> JsonElement?): JsonElement {
    val replaced = if (rest.isEmpty()) leaf(node[index]) else rewriteAt(node[index], rest, leaf)
    val items = node.toMutableList()
    if (replaced == null) items.removeAt(index) else items[index] = replaced
    return JsonArray(items)
}

private fun indexOfStep(node: JsonArray, step: JsonElement): Int {
    if (step is JsonObject) {
        val found = node.indexOfFirst { item -> item is JsonObject && step.all { (key, value) -> pythonEquals(item[key] ?: kotlinx.serialization.json.JsonNull, value) } }
        if (found < 0) throw MalformedDataException("no list member matches ${pythonStr(step)}")
        return found
    }
    val index = pythonInt(step).toInt()
    val resolved = if (index < 0) node.size + index else index
    if (resolved !in node.indices) throw MalformedDataException("list index out of range")
    return resolved
}

private fun numberedItems(edit: JsonObject): JsonArray {
    val template = edit["template"] ?: throw MalformedDataException("a set_items edit has no template")
    return JsonArray((1..pythonInt(edit["times"]).toInt()).map { index -> numberTemplate(template, index) })
}

private fun numberTemplate(template: JsonElement, index: Int): JsonElement = when {
    template is JsonPrimitive && template.isString -> JsonPrimitive(template.content.replace("{i}", index.toString()))
    template is JsonArray -> JsonArray(template.map { numberTemplate(it, index) })
    template is JsonObject -> JsonObject(template.mapValues { numberTemplate(it.value, index) })
    else -> template
}
