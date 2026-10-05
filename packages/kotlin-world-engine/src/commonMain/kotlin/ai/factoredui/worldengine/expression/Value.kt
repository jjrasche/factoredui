package ai.factoredui.worldengine.expression

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.pythonFloat
import ai.factoredui.worldengine.state.Instance
import ai.factoredui.worldengine.text.pythonFloatRepr
import ai.factoredui.worldengine.units.parseUnit
import ai.factoredui.worldengine.world.PropertySpec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

sealed interface Value {
    data class Num(val value: Double) : Value
    data class Bool(val value: Boolean) : Value
    data class Text(val value: String) : Value
    data class TileRef(val instance: Instance?) : Value
    data object Null : Value
}

fun isTruthyValue(value: Value): Boolean = when (value) {
    is Value.Num -> value.value != 0.0
    is Value.Bool -> value.value
    is Value.Text -> value.value.isNotEmpty()
    is Value.TileRef -> value.instance != null
    Value.Null -> false
}

fun numericOf(value: Value, where: String): Double = when (value) {
    is Value.Num -> value.value
    is Value.Bool -> if (value.value) 1.0 else 0.0
    else -> throw MalformedDataException("$where needs a number, found ${describeValue(value)}")
}

fun isNumeric(value: Value): Boolean = value is Value.Num || value is Value.Bool

fun describeValue(value: Value): String = when (value) {
    is Value.Num -> pythonFloatRepr(value.value)
    is Value.Bool -> if (value.value) "True" else "False"
    is Value.Text -> value.value
    is Value.TileRef -> value.instance?.id ?: "None"
    Value.Null -> "None"
}

fun valueOfJson(element: JsonElement?): Value = when {
    element == null || element is JsonNull -> Value.Null
    element is JsonPrimitive && element.isString -> Value.Text(element.content)
    element is JsonPrimitive && element.booleanOrNull != null -> Value.Bool(element.content == "true")
    element is JsonPrimitive -> Value.Num(element.content.toDouble())
    else -> throw MalformedDataException("a property holds a list or an object")
}

fun storedProperty(spec: PropertySpec, value: JsonElement?): Value {
    if (spec.isText) return valueOfJson(value)
    return Value.Num(pythonFloat(value) * parseUnit(spec.unit).factor)
}
