package ai.factoredui.worldengine.world

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.asTextOrNull
import ai.factoredui.worldengine.json.isTruthy
import ai.factoredui.worldengine.json.missingKey
import ai.factoredui.worldengine.json.objects
import ai.factoredui.worldengine.json.optionalList
import ai.factoredui.worldengine.json.optionalText
import ai.factoredui.worldengine.json.pythonInt
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.json.texts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class PropertySpec(
    val name: String,
    val type: String?,
    val unit: String?,
    val default: JsonElement?,
    val kind: String?,
    val hasSource: Boolean,
) {
    val isText: Boolean get() = type == "text"

    companion object {
        fun from(raw: JsonObject): PropertySpec = PropertySpec(
            name = raw.requiredText("name"),
            type = raw.optionalText("type"),
            unit = raw.optionalText("unit"),
            default = raw["default"],
            kind = raw.optionalText("kind"),
            hasSource = "source" in raw,
        )
    }
}

data class ObjectType(
    val raw: JsonObject,
    val id: String,
    val tags: List<String>,
    val properties: List<PropertySpec>,
) {
    val label: String? get() = raw.optionalText("label")
    val color: String? get() = raw.optionalText("color")
    val sprite: String? get() = raw.optionalText("sprite")

    val hasTileFootprint: Boolean get() = "footprint" in raw

    val heightMm: JsonElement? get() = raw["height_mm"]

    fun tileFootprint(): Pair<Int, Int> = twoNumbers(raw["footprint"] ?: throw missingKey("footprint"), "footprint").let { (width, height) ->
        pythonInt(width).toInt() to pythonInt(height).toInt()
    }

    fun footprintMm(): List<JsonElement> = (raw["footprint_mm"] ?: throw missingKey("footprint_mm")) as? JsonArray
        ?: throw MalformedDataException("'footprint_mm' is not a list")

    private fun twoNumbers(element: JsonElement, field: String): List<JsonElement> {
        val cells = element as? JsonArray ?: throw MalformedDataException("'$field' is not a list")
        if (cells.size != 2) throw MalformedDataException("$field must hold exactly two numbers")
        return cells
    }

    fun property(name: String): PropertySpec? = properties.firstOrNull { it.name == name }

    companion object {
        fun from(raw: JsonObject): ObjectType = ObjectType(
            raw = raw,
            id = raw.requiredText("id"),
            tags = raw.optionalList("tags").texts(),
            properties = raw.optionalList("properties").objects().map { PropertySpec.from(it) },
        )
    }
}

data class EffectSpec(val set: String?, val to: String?)

data class RuleSpec(
    val raw: JsonObject,
    val id: String,
    val isInherited: Boolean,
) {
    val on: String? get() = raw.optionalText("on")
    val scope: String get() = raw.optionalText("scope") ?: "self"
    val appliesTo: List<String>? get() = (raw["applies_to"] as? JsonArray)?.texts()
    val appliesToTag: String? get() = raw.optionalText("applies_to_tag")
    val hasRequire: Boolean get() = "require" in raw
    val require: String? get() = raw["require"].asTextOrNull()
    val message: String? get() = raw.optionalText("message")
    val effect: EffectSpec? get() = (raw["effect"] as? JsonObject)?.let { EffectSpec(it.optionalText("set"), it["to"].asTextOrNull()) }
    val hasEffect: Boolean get() = "effect" in raw

    fun inherited(): RuleSpec = copy(isInherited = true)

    companion object {
        fun from(raw: JsonObject): RuleSpec = RuleSpec(raw, raw.requiredText("id"), isInherited = false)
    }
}

data class EquationSpec(val id: String, val expr: String?, val unit: String?) {
    fun requiredUnit(): String = unit ?: throw missingKey("unit")

    companion object {
        fun from(raw: JsonObject): EquationSpec = EquationSpec(raw.requiredText("id"), raw["expr"].asTextOrNull(), raw.optionalText("unit"))
    }
}

data class StockSpec(val id: String, val unit: String?, val initial: JsonElement?, val next: String?) {
    fun requiredUnit(): String = unit ?: throw missingKey("unit")

    companion object {
        fun from(raw: JsonObject): StockSpec =
            StockSpec(raw.requiredText("id"), raw.optionalText("unit"), raw["initial"], raw["next"].asTextOrNull())
    }
}

data class ScoreSpec(
    val id: String,
    val label: String?,
    val expr: String?,
    val unit: String?,
    val isBinding: Boolean,
    val note: String? = null,
    val source: String? = null,
) {
    fun requiredUnit(): String = unit ?: throw missingKey("unit")

    companion object {
        fun from(raw: JsonObject): ScoreSpec = ScoreSpec(
            id = raw.requiredText("id"),
            label = raw.optionalText("label"),
            expr = raw["expr"].asTextOrNull(),
            unit = raw.optionalText("unit"),
            isBinding = isTruthy(raw["binding"]),
            note = raw.optionalText("note"),
            source = describeSource(raw["source"]),
        )
    }
}

private val SOURCE_KEYS = listOf("twin", "binding", "row")

private fun describeSource(source: JsonElement?): String? {
    val fields = source as? JsonObject ?: return null
    val cited = SOURCE_KEYS.mapNotNull { key -> fields.optionalText(key)?.let { "$key $it" } }
    val placeholder = if (isTruthy(fields["placeholder"])) listOfNotNull("placeholder" + (fields.optionalText("reason")?.let { " ($it)" } ?: "")) else emptyList()
    return (cited + placeholder).joinToString("; ").ifEmpty { null }
}

data class AttributeSpec(val name: String, val unit: String?, val default: JsonElement?) {
    companion object {
        fun from(raw: JsonObject): AttributeSpec = AttributeSpec(raw.requiredText("name"), raw.optionalText("unit"), raw["default"])
    }
}

data class AgentSpec(
    val type: String,
    val label: String?,
    val attributes: List<AttributeSpec>,
    val weight: String?,
    val utility: String?,
    val weightRaw: JsonElement?,
    val utilityRaw: JsonElement?,
) {
    companion object {
        fun from(raw: JsonObject): AgentSpec = AgentSpec(
            type = raw.requiredText("type"),
            label = raw.optionalText("label"),
            attributes = raw.optionalList("attributes").objects().map { AttributeSpec.from(it) },
            weight = raw["weight"].asTextOrNull(),
            utility = raw["utility"].asTextOrNull(),
            weightRaw = raw["weight"],
            utilityRaw = raw["utility"],
        )
    }
}

data class ActionSpec(val verb: String, val emits: JsonElement?) {
    companion object {
        fun from(raw: JsonObject): ActionSpec = ActionSpec(raw.requiredText("verb"), raw["emits"])
    }
}

data class LinkSpec(val raw: JsonObject) {
    val parent: String get() = raw.requiredText("parent")
    val parcel: String get() = raw.requiredText("parcel")
    val alias: String? get() = raw.optionalText("as")
    val inherit: List<String> get() = raw.optionalList("inherit").texts()

    val isPresent: Boolean get() = raw.isNotEmpty()
}
