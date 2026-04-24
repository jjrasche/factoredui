package ai.factoredui.compose.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mirrors the TypeScript `Spec` envelope from @factoredui/core spec-types.ts.
 * Round-trips cleanly with the JSON the TS core produces.
 */
@Serializable
data class Spec(
    @SerialName("spec_version") val specVersion: Int,
    @SerialName("renderer_min") val rendererMin: Int,
    val root: SpecNode,
)

/**
 * All 20 SDUI primitive types — matches SpecNodeType union in spec-types.ts.
 */
@Serializable
enum class SpecNodeType {
    @SerialName("column") COLUMN,
    @SerialName("row") ROW,
    @SerialName("stack") STACK,
    @SerialName("scrollview") SCROLLVIEW,
    @SerialName("grid") GRID,
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("icon") ICON,
    @SerialName("divider") DIVIDER,
    @SerialName("spacer") SPACER,
    @SerialName("textinput") TEXTINPUT,
    @SerialName("button") BUTTON,
    @SerialName("toggle") TOGGLE,
    @SerialName("select") SELECT,
    @SerialName("slider") SLIDER,
    @SerialName("card") CARD,
    @SerialName("list") LIST,
    @SerialName("tabs") TABS,
    @SerialName("modal") MODAL,
    @SerialName("chip") CHIP,
    @SerialName("forcegraph") FORCE_GRAPH,
}

/**
 * A single node in a SDUI spec tree.
 *
 * `props` values may be literals, binding refs ("{path.to.value}"), or nested nodes.
 * `visible` is an optional binding ref — node is hidden when it resolves to falsy.
 * `action` is an optional named action dispatched on primary interaction.
 */
@Serializable
data class SpecNode(
    val id: String,
    val type: SpecNodeType,
    val props: Map<String, @Serializable(with = SpecValueSerializer::class) SpecValue> = emptyMap(),
    val children: List<SpecNode> = emptyList(),
    val visible: String? = null,
    val action: ActionRef? = null,
)

/**
 * Named action reference dispatched by the host app's ActionRegistry.
 * Matches ActionRef in spec-types.ts.
 */
@Serializable
data class ActionRef(
    val action: String,
    val params: Map<String, @Serializable(with = SpecValueSerializer::class) SpecValue> = emptyMap(),
)

/**
 * Polymorphic value type matching SpecValue in spec-types.ts.
 * Covers: string (including binding refs), number, boolean, null, and nested objects/arrays.
 */
@Serializable(with = SpecValueSerializer::class)
sealed class SpecValue {
    @Serializable data class StringValue(val value: String) : SpecValue()
    @Serializable data class NumberValue(val value: Double) : SpecValue()
    @Serializable data class BooleanValue(val value: Boolean) : SpecValue()
    @Serializable object NullValue : SpecValue()
    @Serializable data class NodeValue(val value: SpecNode) : SpecValue()
    @Serializable data class ArrayValue(val value: List<SpecValue>) : SpecValue()
    @Serializable data class ObjectValue(val value: Map<String, SpecValue>) : SpecValue()
}

/** Returns true when this value is a binding reference like "{path.to.value}". */
fun SpecValue.isBindingRef(): Boolean =
    this is SpecValue.StringValue && value.startsWith("{") && value.endsWith("}")

/** Extracts the path from a binding ref, e.g. "{shell.inputText}" → "shell.inputText". */
fun SpecValue.bindingPath(): String? =
    if (isBindingRef()) (this as SpecValue.StringValue).value.removeSurrounding("{", "}") else null

/**
 * Custom serializer that reads/writes SpecValue as bare JSON primitives, arrays, or objects —
 * matching the TypeScript spec format exactly. No type discriminator wrapper.
 */
object SpecValueSerializer : KSerializer<SpecValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SpecValue")

    override fun serialize(encoder: Encoder, value: SpecValue) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: error("SpecValueSerializer requires JSON encoding")
        jsonEncoder.encodeJsonElement(encodeToJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): SpecValue {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: error("SpecValueSerializer requires JSON decoding")
        return decodeFromJsonElement(jsonDecoder.decodeJsonElement())
    }

    private fun encodeToJsonElement(value: SpecValue): JsonElement = when (value) {
        is SpecValue.StringValue -> JsonPrimitive(value.value)
        is SpecValue.NumberValue -> JsonPrimitive(value.value)
        is SpecValue.BooleanValue -> JsonPrimitive(value.value)
        is SpecValue.NullValue -> JsonPrimitive(null as String?)
        is SpecValue.NodeValue -> kotlinx.serialization.json.Json.encodeToJsonElement(SpecNode.serializer(), value.value)
        is SpecValue.ArrayValue -> kotlinx.serialization.json.JsonArray(value.value.map { encodeToJsonElement(it) })
        is SpecValue.ObjectValue -> JsonObject(value.value.mapValues { encodeToJsonElement(it.value) })
    }

    private fun decodeFromJsonElement(element: JsonElement): SpecValue = when (element) {
        is JsonPrimitive -> when {
            element.isString -> SpecValue.StringValue(element.content)
            element.content == "null" -> SpecValue.NullValue
            element.content == "true" || element.content == "false" ->
                SpecValue.BooleanValue(element.content.toBoolean())
            else -> SpecValue.NumberValue(element.content.toDouble())
        }
        is kotlinx.serialization.json.JsonArray ->
            SpecValue.ArrayValue(element.map { decodeFromJsonElement(it) })
        is JsonObject -> {
            // Check if this looks like a SpecNode (has "id" and "type" fields)
            if (element.containsKey("id") && element.containsKey("type")) {
                SpecValue.NodeValue(kotlinx.serialization.json.Json.decodeFromJsonElement(SpecNode.serializer(), element))
            } else {
                SpecValue.ObjectValue(element.mapValues { decodeFromJsonElement(it.value) })
            }
        }
        else -> SpecValue.NullValue
    }
}

/** Renderer version constant — must match RENDERER_VERSION in spec-types.ts. */
const val RENDERER_VERSION = 1
