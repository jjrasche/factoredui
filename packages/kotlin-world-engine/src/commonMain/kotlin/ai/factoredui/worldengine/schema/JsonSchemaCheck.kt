package ai.factoredui.worldengine.schema

import ai.factoredui.worldengine.json.isBooleanLiteral
import ai.factoredui.worldengine.json.pythonEquals
import ai.factoredui.worldengine.text.pythonRepr
import ai.factoredui.worldengine.text.pythonStr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

fun schemaErrors(instance: JsonElement, schema: JsonElement): List<String> = SchemaCheck(schema as JsonObject).errorsOf(instance, schema, "$")

private class SchemaCheck(private val root: JsonObject) {
    private val patternCache = mutableMapOf<String, Regex>()

    fun errorsOf(instance: JsonElement, schemaElement: JsonElement, where: String): List<String> {
        val schema = schemaElement as? JsonObject ?: return emptyList()
        schema["\$ref"]?.let { reference -> return errorsOf(instance, resolveReference(pythonStr(reference)), where) }
        val errors = mutableListOf<String>()
        errors += anyOfErrors(instance, schema, where)
        errors += constErrors(instance, schema, where)
        errors += enumErrors(instance, schema, where)
        typeMismatch(instance, schema, where)?.let { return errors + it }
        if (instance is JsonObject) errors += objectErrors(instance, schema, where)
        if (instance is JsonArray) errors += arrayErrors(instance, schema, where)
        if (isJsonType(instance, "string")) errors += stringErrors((instance as JsonPrimitive).content, schema, where)
        if (isJsonType(instance, "number")) errors += numberErrors(instance as JsonPrimitive, schema, where)
        return errors
    }

    private fun resolveReference(reference: String): JsonElement =
        reference.trimStart('#', '/').split('/').fold(root as JsonElement) { node, part -> (node as JsonObject).getValue(part) }

    private fun anyOfErrors(instance: JsonElement, schema: JsonObject, where: String): List<String> {
        val options = schema["anyOf"] as? JsonArray ?: return emptyList()
        val matchesNone = options.all { errorsOf(instance, it, where).isNotEmpty() }
        return if (matchesNone) listOf("$where: matches none of its allowed shapes") else emptyList()
    }

    private fun constErrors(instance: JsonElement, schema: JsonObject, where: String): List<String> {
        val constant = schema["const"] ?: return emptyList()
        return if (pythonEquals(instance, constant)) emptyList() else listOf("$where: must be ${pythonRepr(constant)}")
    }

    private fun enumErrors(instance: JsonElement, schema: JsonObject, where: String): List<String> {
        val allowed = schema["enum"] as? JsonArray ?: return emptyList()
        return if (allowed.any { pythonEquals(instance, it) }) emptyList() else listOf("$where: ${pythonRepr(instance)} is not one of ${pythonRepr(allowed)}")
    }

    private fun typeMismatch(instance: JsonElement, schema: JsonObject, where: String): String? {
        val declared = schema["type"] ?: return null
        val allowed = if (declared is JsonArray) declared.map { pythonStr(it) } else listOf(pythonStr(declared))
        return if (allowed.any { isJsonType(instance, it) }) null else "$where: expected ${allowed.joinToString("/")}"
    }

    private fun objectErrors(instance: JsonObject, schema: JsonObject, where: String): List<String> {
        val errors = mutableListOf<String>()
        (schema["required"] as? JsonArray)?.map { pythonStr(it) }?.filter { it !in instance }?.forEach { errors += "$where: missing '$it'" }
        val declared = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val isClosed = (schema["additionalProperties"] as? JsonPrimitive)?.let { isBooleanLiteral(it) && it.content == "false" } == true
        instance.forEach { (key, value) ->
            val propertySchema = declared[key]
            if (propertySchema != null) errors += errorsOf(value, propertySchema, "$where.$key") else if (isClosed) errors += "$where: unexpected '$key'"
        }
        return errors
    }

    private fun arrayErrors(instance: JsonArray, schema: JsonObject, where: String): List<String> {
        val errors = mutableListOf<String>()
        val minimum = schema.integer("minItems") ?: 0
        if (instance.size < minimum) errors += "$where: needs at least $minimum items"
        schema.integer("maxItems")?.let { if (instance.size > it) errors += "$where: allows at most $it items" }
        schema["items"]?.let { itemSchema -> instance.forEachIndexed { index, item -> errors += errorsOf(item, itemSchema, "$where[$index]") } }
        return errors
    }

    private fun stringErrors(instance: String, schema: JsonObject, where: String): List<String> {
        val errors = mutableListOf<String>()
        val length = instance.codePointLength()
        val minimum = schema.integer("minLength") ?: 0
        if (length < minimum) errors += "$where: shorter than $minimum"
        schema.integer("maxLength")?.let { if (length > it) errors += "$where: longer than $it" }
        schema["pattern"]?.let { pattern ->
            val text = pythonStr(pattern)
            if (!isPythonSearchMatch(patternCache.getOrPut(text) { Regex(text) }, instance)) errors += "$where: does not match $text"
        }
        return errors
    }

    private fun numberErrors(instance: JsonPrimitive, schema: JsonObject, where: String): List<String> {
        val errors = mutableListOf<String>()
        val value = instance.doubleOrNull ?: return errors
        schema["minimum"]?.let { bound -> if (value < (bound as JsonPrimitive).doubleOrNull!!) errors += "$where: below ${pythonStr(bound)}" }
        schema["maximum"]?.let { bound -> if (value > (bound as JsonPrimitive).doubleOrNull!!) errors += "$where: above ${pythonStr(bound)}" }
        return errors
    }
}

private fun isPythonSearchMatch(pattern: Regex, text: String): Boolean =
    pattern.containsMatchIn(text) || (text.endsWith("\n") && pattern.containsMatchIn(text.dropLast(1)))

private fun JsonObject.integer(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun String.codePointLength(): Int = length - indices.count { index -> this[index].isLowSurrogate() && index > 0 && this[index - 1].isHighSurrogate() }

fun isJsonType(instance: JsonElement, typeName: String): Boolean = when (typeName) {
    "object" -> instance is JsonObject
    "array" -> instance is JsonArray
    "string" -> instance is JsonPrimitive && instance !is JsonNull && instance.isString
    "boolean" -> instance is JsonPrimitive && instance !is JsonNull && isBooleanLiteral(instance)
    "null" -> instance is JsonNull
    "integer" -> isNumberPrimitive(instance) && isIntegerLiteral((instance as JsonPrimitive).content)
    "number" -> isNumberPrimitive(instance)
    else -> false
}

private fun isNumberPrimitive(instance: JsonElement): Boolean =
    instance is JsonPrimitive && instance !is JsonNull && !instance.isString && !isBooleanLiteral(instance)

private fun isIntegerLiteral(literal: String): Boolean = literal.removePrefix("-").let { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
