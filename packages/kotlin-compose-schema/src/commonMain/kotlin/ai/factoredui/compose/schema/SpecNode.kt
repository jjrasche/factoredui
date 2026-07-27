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
    /**
     * Optional spec-level keyboard shortcuts: a key fires its action exactly as
     * a tap would (same [ActionRef] dispatch, same resolved-params → capture
     * path). One spec-level map is the single source of truth for "what
     * shortcuts this screen accepts" — cheaper to audit and render a help
     * overlay than per-node bindings.
     */
    val keybindings: Map<ShortcutKey, ActionRef> = emptyMap(),
)

/**
 * Closed set of keys bindable via [Spec.keybindings].
 *
 * A closed enum (not free strings) sidesteps cross-platform key-name drift
 * ("Enter" vs "Return", "Esc" vs "Escape") and makes a typo fail at spec-parse
 * time. Wire form is lowercase snake_case, matching [SpecNodeType] /
 * capture event types. Add entries via a schema version bump.
 */
@Serializable
enum class ShortcutKey {
    @SerialName("y") Y,
    @SerialName("n") N,
    @SerialName("space") SPACE,
    @SerialName("enter") ENTER,
    @SerialName("escape") ESCAPE,
    @SerialName("arrow_up") ARROW_UP,
    @SerialName("arrow_down") ARROW_DOWN,
    @SerialName("arrow_left") ARROW_LEFT,
    @SerialName("arrow_right") ARROW_RIGHT,
    @SerialName("tab") TAB,
}

/**
 * All 21 SDUI primitive types — matches SpecNodeType union in spec-types.ts.
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
    @SerialName("scene3d") SCENE3D,
    @SerialName("canvas") CANVAS,
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
    @Serializable(with = SpecPropsSerializer::class)
    val props: Map<String, SpecValue> = emptyMap(),
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
    @Serializable(with = ActionParamsSerializer::class)
    val params: Map<String, SpecValue> = emptyMap(),
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
 * Prop keys declared to carry a nested [SpecNode]. Nodehood is declared, never inferred:
 * the prior "object with `id` and `type` is a node" rule made a stored spec's meaning
 * depend on a guess, and ordinary data carrying both keys either threw or silently
 * decoded as a node. A node lives at a key listed here or it is data.
 */
val NODE_BEARING_PROP_KEYS: Set<String> = setOf("itemTemplate")

/**
 * Reads/writes SpecValue as bare JSON primitives, arrays, or objects — matching the
 * TypeScript spec format exactly. No type discriminator wrapper. Never yields a
 * [SpecValue.NodeValue]; only [SpecPropsSerializer] can see a key and decode a node.
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

    internal fun encodeToJsonElement(value: SpecValue): JsonElement = when (value) {
        is SpecValue.StringValue -> JsonPrimitive(value.value)
        is SpecValue.NumberValue -> JsonPrimitive(value.value)
        is SpecValue.BooleanValue -> JsonPrimitive(value.value)
        is SpecValue.NullValue -> JsonPrimitive(null as String?)
        is SpecValue.NodeValue -> kotlinx.serialization.json.Json.encodeToJsonElement(SpecNode.serializer(), value.value)
        is SpecValue.ArrayValue -> kotlinx.serialization.json.JsonArray(value.value.map { encodeToJsonElement(it) })
        is SpecValue.ObjectValue -> JsonObject(value.value.mapValues { encodeToJsonElement(it.value) })
    }

    internal fun decodeFromJsonElement(element: JsonElement): SpecValue = when (element) {
        is JsonPrimitive -> when {
            element.isString -> SpecValue.StringValue(element.content)
            element.content == "null" -> SpecValue.NullValue
            element.content == "true" || element.content == "false" ->
                SpecValue.BooleanValue(element.content.toBoolean())
            else -> SpecValue.NumberValue(element.content.toDouble())
        }
        is kotlinx.serialization.json.JsonArray ->
            SpecValue.ArrayValue(element.map { decodeFromJsonElement(it) })
        is JsonObject -> SpecValue.ObjectValue(element.mapValues { decodeFromJsonElement(it.value) })
    }
}

/**
 * Serializes a `Map<String, SpecValue>` where the KEY decides whether an object value is a
 * nested [SpecNode] — a value serializer alone cannot see its key, so it can only guess.
 */
sealed class SpecValueMapSerializer(
    private val nodeBearingKeys: Set<String>,
) : KSerializer<Map<String, SpecValue>> {

    override val descriptor: SerialDescriptor =
        MapSerializer(String.serializer(), SpecValueSerializer).descriptor

    override fun serialize(encoder: Encoder, value: Map<String, SpecValue>) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: error("SpecValueMapSerializer requires JSON encoding")
        requireNodesAtDeclaredKeys(value)
        jsonEncoder.encodeJsonElement(
            JsonObject(value.mapValues { SpecValueSerializer.encodeToJsonElement(it.value) })
        )
    }

    private fun requireNodesAtDeclaredKeys(value: Map<String, SpecValue>) {
        val undeclared = value.filterValues { it is SpecValue.NodeValue }.keys - nodeBearingKeys
        if (undeclared.isNotEmpty()) {
            error(
                "Cannot encode a nested SpecNode at prop(s) $undeclared: not declared " +
                    "node-bearing keys. Declared keys are $nodeBearingKeys. Either add to " +
                    "NODE_BEARING_PROP_KEYS, or store the value as data rather than a node. " +
                    "Encoding it here would decode back as an ObjectValue."
            )
        }
    }

    override fun deserialize(decoder: Decoder): Map<String, SpecValue> {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: error("SpecValueMapSerializer requires JSON decoding")
        val element = jsonDecoder.decodeJsonElement()
        val obj = element as? JsonObject
            ?: error("Expected a JSON object for a SpecValue map, got ${element::class.simpleName}")
        return obj.mapValues { (key, value) ->
            // Caller's Json, so a nested node never parses under stricter rules than its parent.
            if (key in nodeBearingKeys && value is JsonObject) {
                SpecValue.NodeValue(jsonDecoder.json.decodeFromJsonElement(SpecNode.serializer(), value))
            } else {
                SpecValueSerializer.decodeFromJsonElement(value)
            }
        }
    }
}

/** Props map: object values at [NODE_BEARING_PROP_KEYS] decode as nested nodes. */
object SpecPropsSerializer : SpecValueMapSerializer(NODE_BEARING_PROP_KEYS)

/** Action params: pure data, never a node, regardless of key. */
object ActionParamsSerializer : SpecValueMapSerializer(emptySet())

/** Renderer version constant — must match RENDERER_VERSION in spec-types.ts. */
const val RENDERER_VERSION = 1
