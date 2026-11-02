package ai.factoredui.compose.design

import ai.factoredui.compose.render.renderSpecToPng
import ai.factoredui.compose.renderer.SpecTheme
import ai.factoredui.compose.tokens.contrastRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.math.sin

private const val SPECIMEN_WIDTH_DP = 430
private const val SPECIMEN_HEIGHT_DP = 4050
private const val SPECIMEN_DENSITY = 1.25f

private fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map(::json))
    is Map<*, *> -> JsonObject(value.entries.associate { (key, inner) -> key.toString() to json(inner) })
    else -> error("cannot write ${value::class}")
}

private fun node(id: String, type: String, props: Map<String, Any?> = emptyMap(), children: List<JsonObject> = emptyList(), visible: String? = null): JsonObject =
    JsonObject(
        listOfNotNull(
            "id" to JsonPrimitive(id), "type" to JsonPrimitive(type), "props" to json(props), "children" to JsonArray(children),
            visible?.let { "visible" to JsonPrimitive(it) },
        ).toMap(),
    )

private fun text(id: String, value: String, variant: String? = null, color: String? = null, flex: Int? = null) =
    node(id, "text", listOfNotNull("value" to value, variant?.let { "variant" to it }, color?.let { "color" to it }, flex?.let { "flex" to it }).toMap())

private fun argbOf(colour: Color): Long = colour.toArgb().toLong() and 0xFFFFFFFFL

private fun hexOf(colour: Color): String = "#" + (argbOf(colour) and 0xFFFFFF).toString(16).padStart(6, '0')

private fun ratioOf(foreground: Color, background: Color): String = "%.1f:1".format(contrastRatio(argbOf(foreground), argbOf(background)))

private fun section(id: String, title: String, note: String, content: List<JsonObject>): JsonObject =
    node(id, "column", mapOf("gap" to 12), listOf(node("$id.rule", "divider"), text("$id.title", title, "heading"), text("$id.note", note, "caption", "muted")) + content)

private fun swatch(id: String, name: String, colour: Color, detail: String): JsonObject =
    node(
        id, "row", mapOf("gap" to 12),
        listOf(
            node("$id.chip", "card", mapOf("background" to hexOf(colour), "padding" to 0, "cornerRadius" to 8, "width" to 64), listOf(node("$id.chip.fill", "spacer", mapOf("size" to 40)))),
            node("$id.words", "column", mapOf("gap" to 2), listOf(text("$id.name", name, "label"), text("$id.detail", "${hexOf(colour)} · $detail", "caption", "muted"))),
        ),
    )

private fun palette(theme: SpecTheme): List<JsonObject> {
    val ground = theme.ground
    fun onGround(colour: Color) = "${ratioOf(colour, ground)} on ground " + if (contrastRatio(argbOf(colour), argbOf(ground)) >= 4.5) "· AA text" else ""
    fun band(id: String, name: String, colour: Color, detail: String) =
        node(id, "card", mapOf("background" to hexOf(colour), "padding" to 12, "cornerRadius" to 0), listOf(text("$id.t", "$name  ${hexOf(colour)}  $detail", "caption", "ink")))
    val ladder = node(
        "pal.ladder", "column", mapOf("gap" to 0),
        listOf(
            band("pal.ground", "Ground", ground, "the page"),
            band("pal.surface", "Surface", theme.surface, "cards · +${(theme.elevation.surface * 100).toInt()}% toward ink"),
            band("pal.raised", "Raised", theme.raised, "controls, hover · +${(theme.elevation.raised * 100).toInt()}%"),
            band("pal.overlay", "Overlay", theme.overlay, "menus, sheets · +${(theme.elevation.overlay * 100).toInt()}%"),
        ),
    )
    return listOf(
        ladder,
        swatch("pal.ink", "Ink", theme.ink, onGround(theme.ink)),
        swatch("pal.muted", "Muted", theme.muted, onGround(theme.muted)),
        swatch("pal.accent", "Accent", theme.accent, "${onGround(theme.accent)} · ${ratioOf(theme.onAccent, theme.accent)} for its ink"),
        swatch("pal.pass", "Pass", theme.pass, onGround(theme.pass)),
        swatch("pal.warn", "Warn", theme.warn, onGround(theme.warn)),
        swatch("pal.fail", "Fail", theme.fail, onGround(theme.fail)),
    )
}

private fun typeRow(id: String, variant: String, sample: String, token: String): JsonObject =
    node(id, "column", mapOf("gap" to 2), listOf(text("$id.sample", sample, variant), text("$id.token", token, "caption", "muted")))

private fun typeScale(theme: SpecTheme): List<JsonObject> {
    val type = theme.type
    fun describe(name: String, token: ai.factoredui.compose.renderer.TextToken) = "$name · ${token.size}/${token.line} · weight ${token.weight}"
    return listOf(
        typeRow("type.display", "display", "1,842 W", describe("display", type.display)),
        typeRow("type.heading", "heading", "Heat loss by the hour", describe("heading", type.heading)),
        typeRow("type.subheading", "subheading", "What the house is made of", describe("subheading", type.subheading)),
        typeRow("type.body", "body", "The north wall is a 6 in SIP with an R-value of 24.", describe("body", type.body)),
        typeRow("type.caption", "caption", "Source: published row 14, inferred for the loft", describe("caption", type.caption)),
        typeRow("type.mono", "mono", "0.30 kWh   1,842 W   68.4 °F", describe("mono (numbers)", type.mono)),
    )
}

private fun spaceRow(id: String, name: String, size: Int, bar: Color): JsonObject =
    node(
        id, "row", mapOf("gap" to 12),
        listOf(text("$id.name", "$name · $size", "caption", "muted", flex = 1), node("$id.bar", "card", mapOf("background" to hexOf(bar), "padding" to 0, "cornerRadius" to 4, "width" to size * 3), listOf(node("$id.bar.fill", "spacer", mapOf("size" to 12))))),
    )

private fun spacing(theme: SpecTheme): List<JsonObject> {
    val space = theme.space
    val steps = listOf("xs" to space.xs, "sm" to space.sm, "md" to space.md, "lg" to space.lg, "xl" to space.xl, "xxl" to space.xxl)
    val radii = listOf("sm" to theme.radius.sm, "md" to theme.radius.md, "lg" to theme.radius.lg, "xl" to theme.radius.xl)
    val radiusCards = radii.map { (name, size) -> node("radius.$name", "card", mapOf("background" to "raised", "padding" to 12, "cornerRadius" to size, "flex" to 1), listOf(text("radius.$name.t", "$name\n$size", "caption"))) }
    return steps.map { (name, size) -> spaceRow("space.$name", name, size, theme.accent) } +
        listOf(text("space.gutter", "Phone gutter ${space.gutter} · bars are drawn 3× so they can be seen", "caption", "muted"), node("radius.row", "row", mapOf("gap" to 8), radiusCards))
}

private fun stats(): List<JsonObject> = listOf(
    node(
        "stats.row", "row", mapOf("gap" to 8),
        listOf(
            node("stat.pass", "stat", mapOf("label" to "Heat loss", "value" to 1842, "unit" to "W", "status" to "pass", "flex" to 1)),
            node("stat.warn", "stat", mapOf("label" to "Daylight", "value" to 4.1, "unit" to "%", "status" to "warn", "flex" to 1)),
        ),
    ),
    node(
        "stats.row2", "row", mapOf("gap" to 8),
        listOf(
            node("stat.fail", "stat", mapOf("label" to "Post load", "value" to 91, "unit" to "%", "status" to "fail", "status_text" to "Over", "flex" to 1)),
            node("stat.none", "stat", mapOf("label" to "Water", "value" to null, "unit" to "lb", "flex" to 1)),
        ),
    ),
)

private fun buttons(): List<JsonObject> {
    fun button(id: String, label: String, size: String, variant: String? = null) = node(id, "button", listOfNotNull("label" to label, "size" to size, variant?.let { "variant" to it }).toMap())
    return listOf(
        node("btn.row", "row", mapOf("gap" to 8), listOf(button("btn.sm", "sm 36", "sm"), button("btn.md", "md 48", "md"), button("btn.lg", "lg 56", "lg"))),
        button("btn.xl", "xl 72 · one tap", "xl"),
        node("btn.row2", "row", mapOf("gap" to 8), listOf(button("btn.outline", "Outline", "md", "outline"), button("btn.ghost", "Ghost", "md", "ghost"), button("btn.destructive", "Delete", "md", "destructive"))),
    )
}

private fun chart(): JsonObject {
    val rows = (0 until 24).map { hour -> mapOf("t" to hour, "v" to (1..8).associate { index -> "s$index" to 40 + 26 * sin((hour + index * 2.4) / 3.8) + index * 6 }) }
    val series = (1..8).map { index -> mapOf("id" to "s$index", "label" to "Series $index", "path" to "v.s$index") }
    return node("chart", "chart", mapOf("rows" to rows, "x" to "t", "x_label" to "hour", "axes" to mapOf("left" to mapOf("unit" to "°F")), "series" to series))
}

private fun windowNote(): JsonObject = text("window.note", "Compact under 600 dp, medium from 600, expanded from 840: a spec shows or hides a subtree with visible on window.is_compact, window.is_medium and window.is_expanded.", "caption", "muted")

fun designSpecimenSpec(theme: SpecTheme, mode: String): String {
    val children = listOf(
        text("title", "factored-ui design system", "display"),
        text("subtitle", "The $mode theme. Tokens are in design/$mode.tokens.json (W3C Design Tokens); a file a user brings overrides them token by token.", "caption", "muted"),
        section("palette", "Palette", "Every text colour is at least 4.5:1 on the ground, the surface and the raised surface.", palette(theme)),
        section("type", "Type scale", "Numbers are mono so columns of figures line up.", typeScale(theme)),
        section("space", "Spacing, radius", "Steps of the 4 dp grid; a phone page keeps a 16 dp gutter.", spacing(theme)),
        section("stat", "Stat", "A figure, its unit and label, and a status said in words as well as colour.", stats()),
        section("section", "Section", "A titled card on the surface, with a note in muted ink.", listOf(text("section.body", "A section groups one question's answers. It has a heading, a note and its content; nothing else.", "body"))),
        section("chart", "Chart series", "Eight colours that stay apart for colour-blind readers and meet 3:1 on every ground.", listOf(chart())),
        section("button", "Buttons", "Four sizes. Every one has a 48 dp touch target.", buttons()),
        section("window", "Window size", "One spec lays out for a phone, a tablet and a desktop.", listOf(windowNote())),
    )
    val root = node("root", "column", mapOf("gap" to 16, "padding" to theme.space.gutter, "background" to "ground"), children)
    val spec = JsonObject(mapOf("spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive(mode), "root" to root))
    return Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), spec) + "\n"
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "../../design/specimen")
    out.mkdirs()
    mapOf("dark" to SpecTheme.DARK, "light" to SpecTheme.LIGHT).forEach { (mode, theme) ->
        val spec = designSpecimenSpec(theme, mode)
        File(out, "design-system-$mode.spec.json").writeText(spec)
        File(out, "design-system-$mode.png").writeBytes(renderSpecToPng(spec, width = SPECIMEN_WIDTH_DP, height = SPECIMEN_HEIGHT_DP, density = SPECIMEN_DENSITY, transparent = false, data = emptyMap(), theme = theme))
        println("wrote $mode specimen")
    }
}
