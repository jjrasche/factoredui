package ai.factoredui.compose.tokens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class OtherValue(val type: String) : TokenValue

class TokenFileRead(val tokens: TokenSet, val mode: String?, val notices: List<String>, val isUsable: Boolean)

private const val VALUE = "\$value"
private const val TYPE = "\$type"
private const val EXTENSIONS = "\$extensions"
private const val EXTENSION_KEY = "ai.factoredui"
private const val PX_PER_REM = 16.0

private val ALIAS = Regex("^\\{([^{}]+)}$")

private val WEIGHTS = mapOf(
    "thin" to 100, "hairline" to 100, "extra-light" to 200, "ultra-light" to 200, "light" to 300, "normal" to 400, "regular" to 400, "book" to 400, "medium" to 500,
    "semi-bold" to 600, "demi-bold" to 600, "bold" to 700, "extra-bold" to 800, "ultra-bold" to 800, "black" to 900, "heavy" to 900, "extra-black" to 950, "ultra-black" to 950,
)

private class Raw(val path: String, val type: String?, val value: JsonElement)

private class TokenProblem(message: String) : Exception(message)

private fun walk(group: JsonObject, path: List<String>, inheritedType: String?, found: MutableList<Raw>) {
    group.forEach { (name, child) ->
        if (name.startsWith("\$") || child !is JsonObject) return@forEach
        val type = (child[TYPE] as? JsonPrimitive)?.content ?: inheritedType
        if (VALUE in child) found += Raw((path + name).joinToString("."), type, child.getValue(VALUE)) else walk(child, path + name, type, found)
    }
}

private fun aliasOf(element: JsonElement): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.let { ALIAS.matchEntire(it.content)?.groupValues?.get(1) }

private fun deref(element: JsonElement, byPath: Map<String, Raw>, seen: List<String>): JsonElement {
    val alias = aliasOf(element) ?: return element
    if (alias in seen) throw TokenProblem("circular alias through {$alias}")
    val target = byPath[alias] ?: throw TokenProblem("alias {$alias} names no token")
    return deref(target.value, byPath, seen + alias)
}

private fun typeOf(raw: Raw, byPath: Map<String, Raw>, seen: List<String>): String? {
    val alias = aliasOf(raw.value) ?: return raw.type
    if (alias in seen) throw TokenProblem("circular alias through {$alias}")
    val target = byPath[alias] ?: throw TokenProblem("alias {$alias} names no token")
    return raw.type ?: typeOf(target, byPath, seen + alias)
}

private fun numberOf(element: JsonElement, what: String): Double = (element as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull() ?: throw TokenProblem("$what must be a number")

private fun hexOf(text: String): Long? {
    val digits = text.removePrefix("#")
    return if (digits.length == 6 && digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) 0xFF000000L or digits.toLong(16) else null
}

private fun colourOf(element: JsonElement): ColorValue {
    (element as? JsonPrimitive)?.takeIf { it.isString }?.let { text -> return ColorValue(hexOf(text.content) ?: throw TokenProblem("the color '${text.content}' is not a 6 digit hex")) }
    val fields = element as? JsonObject ?: throw TokenProblem("a color needs an object with colorSpace and components")
    val space = (fields["colorSpace"] as? JsonPrimitive)?.content
    val components = fields["components"] as? JsonArray
    if (space == null || components == null) throw TokenProblem("a color needs colorSpace and components")
    val alpha = fields["alpha"]?.let { numberOf(it, "alpha") } ?: 1.0
    if (alpha != 1.0) throw TokenProblem("a color must be opaque (alpha 1), not $alpha")
    if (space == "srgb" && components.size == 3) {
        val channels = components.map { (numberOf(it, "a component") * 255).roundToLong().coerceIn(0, 255) }
        return ColorValue(0xFF000000L or (channels[0] shl 16) or (channels[1] shl 8) or channels[2])
    }
    val hex = (fields["hex"] as? JsonPrimitive)?.content?.let(::hexOf) ?: throw TokenProblem("the color space '$space' is not read here and no 6 digit hex fallback is given")
    return ColorValue(hex)
}

private fun dimensionOf(element: JsonElement): DimensionValue {
    val fields = element as? JsonObject ?: throw TokenProblem("a dimension needs an object with value and unit")
    val value = numberOf(fields["value"] ?: throw TokenProblem("a dimension needs a value"), "the dimension's value")
    return when (val unit = (fields["unit"] as? JsonPrimitive)?.content) {
        "px" -> DimensionValue(value)
        "rem" -> DimensionValue(value * PX_PER_REM)
        else -> throw TokenProblem("the dimension's unit must be px or rem, not '$unit'")
    }
}

private fun weightOf(element: JsonElement): Int {
    val primitive = element as? JsonPrimitive ?: throw TokenProblem("a font weight must be a number or a name")
    if (primitive.isString) return WEIGHTS[primitive.content] ?: throw TokenProblem("the font weight '${primitive.content}' is not a known name")
    return numberOf(primitive, "a font weight").roundToInt().also { if (it !in 1..1000) throw TokenProblem("a font weight must be from 1 to 1000, not $it") }
}

private fun familyOf(element: JsonElement): String =
    when (element) {
        is JsonArray -> (element.firstOrNull() as? JsonPrimitive)?.content ?: throw TokenProblem("a font family list is empty")
        is JsonPrimitive -> element.content
        else -> throw TokenProblem("a font family must be a name or a list of names")
    }

private fun typographyOf(element: JsonElement, byPath: Map<String, Raw>): TypographyValue {
    val fields = element as? JsonObject ?: throw TokenProblem("a typography value needs an object")
    fun part(name: String) = deref(fields[name] ?: throw TokenProblem("typography needs $name"), byPath, emptyList())
    return TypographyValue(
        familyOf(part("fontFamily")), dimensionOf(part("fontSize")).px, weightOf(part("fontWeight")),
        fields["letterSpacing"]?.let { dimensionOf(deref(it, byPath, emptyList())).px } ?: 0.0, numberOf(part("lineHeight"), "lineHeight"),
    )
}

private fun valueOf(raw: Raw, byPath: Map<String, Raw>): TokenValue {
    val type = typeOf(raw, byPath, listOf(raw.path)) ?: throw TokenProblem("it has no type; a type is never guessed from the value, so set \$type on the token or its group")
    val value = deref(raw.value, byPath, listOf(raw.path))
    return when (type) {
        "color" -> colourOf(value)
        "dimension" -> dimensionOf(value)
        "number" -> NumberValue(numberOf(value, "a number"))
        "typography" -> typographyOf(value, byPath)
        else -> OtherValue(type)
    }
}

private fun modeOf(root: JsonObject): String? = (((root[EXTENSIONS] as? JsonObject)?.get(EXTENSION_KEY) as? JsonObject)?.get("mode") as? JsonPrimitive)?.content

fun readTokenFile(text: String): TokenFileRead {
    val root = try {
        Json.parseToJsonElement(text) as? JsonObject ?: return TokenFileRead(emptyMap(), null, listOf("the theme file is not valid JSON: it is not an object"), false)
    } catch (invalid: Exception) {
        return TokenFileRead(emptyMap(), null, listOf("the theme file is not valid JSON: ${invalid.message?.lineSequence()?.firstOrNull()}"), false)
    }
    val found = mutableListOf<Raw>()
    walk(root, emptyList(), (root[TYPE] as? JsonPrimitive)?.content, found)
    val byPath = found.associateBy { it.path }
    val notices = mutableListOf<String>()
    val tokens = LinkedHashMap<String, TokenValue>()
    found.forEach { raw ->
        try {
            tokens[raw.path] = valueOf(raw, byPath)
        } catch (problem: TokenProblem) {
            notices += "token ${raw.path}: ${problem.message}"
        }
    }
    return TokenFileRead(tokens, modeOf(root), notices, true)
}

private fun hexText(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')

private fun roundedComponent(channel: Long): JsonPrimitive = JsonPrimitive((channel / 255.0 * 10000).roundToLong() / 10000.0)

private fun dimensionJson(px: Double) = JsonObject(mapOf("value" to JsonPrimitive(px), "unit" to JsonPrimitive("px")))

private fun leaf(type: String, value: JsonElement) = JsonObject(mapOf(TYPE to JsonPrimitive(type), VALUE to value))

private fun tokenJson(value: TokenValue): JsonObject = when (value) {
    is ColorValue -> leaf(
        "color",
        JsonObject(
            mapOf(
                "colorSpace" to JsonPrimitive("srgb"),
                "components" to JsonArray(listOf(16, 8, 0).map { roundedComponent((value.argb shr it) and 0xFF) }),
                "hex" to JsonPrimitive(hexText(value.argb)),
            ),
        ),
    )
    is DimensionValue -> leaf("dimension", dimensionJson(value.px))
    is NumberValue -> leaf("number", JsonPrimitive(value.value))
    is TypographyValue -> leaf(
        "typography",
        JsonObject(
            mapOf(
                "fontFamily" to JsonPrimitive(value.family), "fontSize" to dimensionJson(value.sizePx), "fontWeight" to JsonPrimitive(value.weight),
                "letterSpacing" to dimensionJson(value.letterSpacingPx), "lineHeight" to JsonPrimitive(value.lineMultiplier),
            ),
        ),
    )
    is OtherValue -> throw IllegalArgumentException("a token of type ${value.type} cannot be written")
}

private fun inserted(into: MutableMap<String, Any>, path: List<String>, token: JsonObject) {
    if (path.size == 1) {
        into[path.first()] = token
        return
    }
    @Suppress("UNCHECKED_CAST")
    val group = into.getOrPut(path.first()) { LinkedHashMap<String, Any>() } as MutableMap<String, Any>
    inserted(group, path.drop(1), token)
}

private fun jsonOf(node: Any): JsonElement = when (node) {
    is JsonElement -> node
    is Map<*, *> -> JsonObject(node.entries.associate { (key, inner) -> key as String to jsonOf(inner!!) })
    else -> throw IllegalArgumentException("unexpected ${node::class}")
}

private val PRETTY = Json { prettyPrint = true }

fun writeTokenFile(tokens: TokenSet, mode: String): String {
    val root = LinkedHashMap<String, Any>()
    root["\$description"] = JsonPrimitive("A factored-ui $mode theme in the W3C Design Tokens format.")
    root[EXTENSIONS] = JsonObject(mapOf(EXTENSION_KEY to JsonObject(mapOf("mode" to JsonPrimitive(mode)))))
    tokens.forEach { (path, value) -> inserted(root, path.split("."), tokenJson(value)) }
    return PRETTY.encodeToString(JsonElement.serializer(), jsonOf(root)) + "\n"
}
