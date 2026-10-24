package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.round

class CardsFormatException(message: String) : IllegalArgumentException(message)

data class CardsScreen(val title: String, val world: String, val answersFile: String?)

data class CardsPart(val id: String, val name: String, val xMm: Double, val yMm: Double, val zMm: Double, val dxMm: Double, val dyMm: Double, val dzMm: Double)

data class CardsStep(
    val title: String, val parts: List<String>, val screws: Int, val adult: String, val child46: String, val child710: String,
    val ask46: String, val ask710: String, val shows: String, val learns: String,
)

data class CardsPlan(val title: String, val fixture: String?, val parts: List<CardsPart>, val steps: List<CardsStep>, val endQuestions: List<String>, val loadLb: Double?) {
    val screwCount: Int get() = steps.sumOf { it.screws }
}

private const val MM_PER_INCH = 25.4
private const val SIXTEENTHS = 16
private const val CARD_PATH = "card"
private const val SHEET_GROUND = "#ffffff"
private const val SHEET_INK = "#111111"
private const val DIMENSION_STYLE = "dimension"
private const val PIECE_STYLE = "piece"
private const val END_CARD = "end"
private const val VIEW_HEIGHT_DP = 240
private const val STOOL_HEIGHT_DP = 260

private fun gcdOf(a: Int, b: Int): Int = if (b == 0) a else gcdOf(b, a % b)

/** A length in millimetres as inches to the nearest sixteenth: 12, 9 1/4, 3/4. */
internal fun inchesText(millimetres: Double): String {
    val sixteenths = round(millimetres / MM_PER_INCH * SIXTEENTHS).toInt()
    val whole = sixteenths / SIXTEENTHS
    val remainder = sixteenths % SIXTEENTHS
    if (remainder == 0) return "$whole"
    val fraction = "${remainder / gcdOf(remainder, SIXTEENTHS)}/${SIXTEENTHS / gcdOf(remainder, SIXTEENTHS)}"
    return if (whole == 0) fraction else "$whole $fraction"
}

private fun textIn(root: JsonObject, key: String, where: String): String =
    (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() } ?: throw CardsFormatException("$where has no '$key' text")

/** The build view a world gives: the world whose plan it draws and the file of answers (a fixture until the engine gives them). */
fun readCardsScreen(text: String): CardsScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw CardsFormatException("the build view is not a JSON object")
    return CardsScreen((root["title"] as? JsonPrimitive)?.content.orEmpty(), textIn(root, "world", "the build view"), (root["answers"] as? JsonPrimitive)?.takeIf { it.isString }?.content)
}

private fun numberIn(root: JsonObject, key: String, where: String): Double =
    (root[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: throw CardsFormatException("$where has no number '$key'")

private fun partOf(entry: JsonObject): CardsPart {
    val id = textIn(entry, "id", "a part")
    val where = "the part '$id'"
    return CardsPart(id, textIn(entry, "name", where), numberIn(entry, "x", where), numberIn(entry, "y", where), numberIn(entry, "z", where), numberIn(entry, "dx", where), numberIn(entry, "dy", where), numberIn(entry, "dz", where))
}

private fun stepOf(entry: JsonObject, index: Int): CardsStep {
    val where = "step ${index + 1}"
    val parts = (entry["parts"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content ?: throw CardsFormatException("$where lists a part that is not text") }
    return CardsStep(
        textIn(entry, "title", where), parts, numberIn(entry, "screws", where).toInt(), textIn(entry, "adult", where), textIn(entry, "child_4_6", where), textIn(entry, "child_7_10", where),
        (entry["ask_4_6"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: throw CardsFormatException("$where has no question for ages 4 to 6"),
        (entry["ask_7_10"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: throw CardsFormatException("$where has no question for ages 7 to 10"),
        textIn(entry, "shows", where), textIn(entry, "learns", where),
    )
}

private fun checkEveryPartUsedOnce(parts: List<CardsPart>, steps: List<CardsStep>) {
    val used = steps.flatMap { it.parts }
    val twice = used.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    if (twice.isNotEmpty()) throw CardsFormatException("the part '${twice.first()}' is used in more than one step")
    val unknown = used.firstOrNull { id -> parts.none { it.id == id } }
    if (unknown != null) throw CardsFormatException("a step uses the part '$unknown', which the plan does not list")
    val unused = parts.firstOrNull { it.id !in used }
    if (unused != null) throw CardsFormatException("the part '${unused.id}' is used in no step")
}

/** The plan an answer holds: the parts in the engine's placement order, one step per card, and every part used exactly once. */
fun readCardsPlan(answer: JsonObject): CardsPlan {
    val parts = (answer["parts"] as? JsonArray).orEmpty().map { partOf(it as? JsonObject ?: throw CardsFormatException("a part is not an object")) }
    val steps = (answer["steps"] as? JsonArray).orEmpty().mapIndexed { index, entry -> stepOf(entry as? JsonObject ?: throw CardsFormatException("step ${index + 1} is not an object"), index) }
    if (parts.isEmpty()) throw CardsFormatException("the plan lists no parts")
    if (steps.isEmpty()) throw CardsFormatException("the plan lists no steps")
    checkEveryPartUsedOnce(parts, steps)
    val endQuestions = (answer["end_questions"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content.orEmpty() }
    if (endQuestions.size < 2) throw CardsFormatException("the plan has fewer than two end questions")
    return CardsPlan(
        (answer["title"] as? JsonPrimitive)?.content ?: "Build", (answer["fixture"] as? JsonPrimitive)?.content, parts, steps, endQuestions,
        (answer["load_lb"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull,
    )
}

private fun sizeText(part: CardsPart): String = listOf(part.dxMm, part.dyMm, part.dzMm).sortedDescending().joinToString(" x ") { inchesText(it) } + " in"

private fun partLine(part: CardsPart): String = "${part.name}: ${sizeText(part)}"

private fun plainText(id: String, value: String, variant: String? = null, bold: Boolean = false, color: String? = null): JsonObject =
    textNode(id, value, variant = variant, bold = bold, color = color)

private fun navigation(index: Int, last: Int): JsonObject = specNode(
    "card.$index.nav", "row", mapOf("gap" to 12),
    listOf(
        specNode("card.$index.back", "button", mapOf("label" to "Back", "variant" to "outlined", "flex" to 1), emptyList<JsonObject>()).let { withAction(it, mapOf("path" to CARD_PATH, "add" to -1, "min" to 0, "max" to last)) },
        specNode("card.$index.next", "button", mapOf("label" to if (index == last - 1) "Finish" else "Next", "flex" to 1), emptyList<JsonObject>()).let { withAction(it, mapOf("path" to CARD_PATH, "add" to 1, "min" to 0, "max" to last)) },
    ),
)

private fun withAction(node: JsonObject, params: Map<String, Any?>): JsonObject =
    JsonObject(node + ("action" to jsonOf(mapOf("action" to "set_binding", "params" to params))))

private val PART_STROKES = listOf("#5aa9ff", "#ffb347", "#7bd88f", "#f78fb3", "#c3a6ff")

private fun boxOf(part: CardsPart): Map<String, Any?> = mapOf(
    "id" to part.id, "style" to part.name, "x_mm" to part.xMm + part.dxMm / 2, "y_mm" to part.yMm + part.dyMm / 2, "z_mm" to part.zMm,
    "width_mm" to part.dxMm, "depth_mm" to part.dyMm, "height_mm" to part.dzMm, "rotation_deg" to 0.0, "estimate" to false,
)

/** The whole stool in 3D, this step's boards drawn as normal and the rest ghosted; a step that adds no board shows the stool as it stands. */
private fun stoolPicture(prefix: String, step: CardsStep, parts: List<CardsPart>): JsonObject {
    val names = parts.map { it.name }.distinct()
    return specNode(
        "$prefix.stool", "volume",
        mapOf(
            "height" to STOOL_HEIGHT_DP, "boxes" to parts.map(::boxOf), "grade_mm" to 0, "styles" to names.mapIndexed { n, name -> name to mapOf("stroke" to PART_STROKES[n % PART_STROKES.size]) }.toMap(),
            "legend" to names.mapIndexed { n, name -> mapOf("label" to name, "style" to name) }, "highlight" to step.parts, "empty_text" to "No boards to draw.",
        ),
    )
}

private fun stepCard(index: Int, count: Int, step: CardsStep, parts: List<CardsPart>): JsonObject {
    val prefix = "card.$index"
    val own = step.parts.map { id -> parts.first { it.id == id } }
    val partNodes = if (own.isEmpty()) listOf(plainText("$prefix.noparts", "No new boards in this step.", variant = "caption", color = MUTED)) else own.mapIndexed { n, part -> plainText("$prefix.part.$n", partLine(part)) }
    val screws = plainText("$prefix.screws", if (step.screws == 0) "No screws in this step." else "Screws: ${step.screws}", bold = true)
    val body = listOf(
        plainText("$prefix.title", "Step ${index + 1} of $count: ${step.title}", variant = "subheading", bold = true),
        stoolPicture(prefix, step, parts),
        specNode("$prefix.parts", "column", mapOf("gap" to 4), partNodes), screws,
        plainText("$prefix.adult", "Adult: ${step.adult}", variant = "caption", color = MUTED),
        plainText("$prefix.ask46", "Ask, ages 4 to 6: ${step.ask46}", variant = "subheading"),
        plainText("$prefix.ask710", "Ask, ages 7 to 10: ${step.ask710}", variant = "subheading"),
        specNode("$prefix.reveal", "toggle", mapOf("label" to "Show what answers it", "value" to "{reveal.$index}")),
        specNode("$prefix.answer", "column", mapOf("gap" to 4), listOf(plainText("$prefix.shows", step.shows), plainText("$prefix.learns", "They learn: ${step.learns}", variant = "caption", color = MUTED)), visible = "{reveal.$index}"),
        navigation(index, count),
    )
    return specNode(prefix, "column", mapOf("gap" to 12), body, visible = "{$CARD_PATH}=$index")
}

private fun certificateNodes(plan: CardsPlan): List<JsonObject> {
    val made = "A step stool made of ${plan.parts.size} boards and ${plan.screwCount} screws" + (plan.loadLb?.let { ", tested to hold ${formatFigure(it)} lb" } ?: "") + "."
    fun row(id: String, label: String, binding: String) = specNode(id, "row", mapOf("gap" to 8), listOf(plainText("$id.label", label, variant = "caption", color = MUTED), textNode("$id.value", binding, bold = true, flex = 1)))
    return listOf(
        plainText("certificate.title", "I built this, and now I know this.", variant = "subheading", bold = true),
        row("certificate.name", "Built by", "{child.name}"), row("certificate.first", "Now I know:", "{answers.first}"), row("certificate.second", "Next time I would:", "{answers.second}"),
        plainText("certificate.made", made), plainText("certificate.plum", "Built with Plum.", variant = "caption", color = MUTED),
    )
}

private fun endCard(count: Int, plan: CardsPlan): JsonObject {
    val prefix = "card.$END_CARD"
    val body = listOf(
        plainText("$prefix.title", "All built. Two last questions.", variant = "subheading", bold = true),
        plainText("$prefix.name.ask", "Who built it?", variant = "caption", color = MUTED), specNode("$prefix.name", "textinput", mapOf("value" to "{child.name}", "placeholder" to "Name")),
        plainText("$prefix.first.ask", plan.endQuestions[0], variant = "subheading"), specNode("$prefix.first", "textinput", mapOf("value" to "{answers.first}", "placeholder" to "In their own words")),
        plainText("$prefix.second.ask", plan.endQuestions[1], variant = "subheading"), specNode("$prefix.second", "textinput", mapOf("value" to "{answers.second}", "placeholder" to "In their own words")),
        specNode("$prefix.certificate", "card", mapOf("padding" to 16), listOf(specNode("$prefix.certificate.body", "column", mapOf("gap" to 8), certificateNodes(plan)))),
        specNode("$prefix.back", "button", mapOf("label" to "Back")).let { withAction(it, mapOf("path" to CARD_PATH, "add" to -1, "min" to 0, "max" to count)) },
    )
    return specNode(prefix, "column", mapOf("gap" to 12), body, visible = "{$CARD_PATH}=$count")
}

private fun page(children: List<JsonObject>, theme: String, state: Map<String, Any?>, background: String): JsonObject =
    JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive(theme), "state" to jsonOf(state),
            "root" to specNode("root", "column", mapOf("gap" to 12, "padding" to 16, "background" to background), children),
        ),
    )

/** One card per build step, then the ending: the stool's own parts, screws and a question for each age, and at the end the child's words as a keepsake. */
fun cardsSpec(screen: CardsScreen, plan: CardsPlan): JsonObject {
    val notice = plan.fixture?.let { plainText("fixture.notice", "FIXTURE: $it", variant = "caption", bold = true, color = MUTED) }
    val cards = plan.steps.mapIndexed { index, step -> stepCard(index, plan.steps.size, step, plan.parts) } + endCard(plan.steps.size, plan)
    val state = mapOf(CARD_PATH to 0, "child.name" to "", "answers.first" to "", "answers.second" to "") + plan.steps.indices.associate { "reveal.$it" to false }
    return page(listOfNotNull(plainText("screen.title", screen.title.ifBlank { plan.title }, variant = "subheading", bold = true, color = INK), notice) + cards, "dark", state, HUD)
}

private class Projection(val id: String, val title: String, val horizontal: (CardsPart) -> Pair<Double, Double>, val vertical: (CardsPart) -> Pair<Double, Double>)

private val PROJECTIONS = listOf(
    Projection("front", "Front", { it.xMm to it.xMm + it.dxMm }, { it.zMm to it.zMm + it.dzMm }),
    Projection("side", "Side", { it.yMm to it.yMm + it.dyMm }, { it.zMm to it.zMm + it.dzMm }),
    Projection("top", "Top", { it.xMm to it.xMm + it.dxMm }, { it.yMm to it.yMm + it.dyMm }),
)

private const val OFFSET_MM = 30.0
private const val TICK_FRACTION = 0.4

private fun dimensionShapes(prefix: String, horizontalLow: Double, horizontalHigh: Double, verticalLow: Double, verticalHigh: Double): List<Map<String, Any?>> {
    val below = verticalLow - OFFSET_MM
    val left = horizontalLow - OFFSET_MM
    fun label(id: String, x: Double, y: Double, text: String, align: String, dx: Int, dy: Int) = mapOf("id" to id, "kind" to "label", "style" to DIMENSION_STYLE, "x_mm" to x, "y_mm" to y, "text" to text, "align" to align, "dx_dp" to dx, "dy_dp" to dy)
    return listOf(
        lineShape("$prefix.width", DIMENSION_STYLE, horizontalLow, below, horizontalHigh, below),
        lineShape("$prefix.width.start", DIMENSION_STYLE, horizontalLow, below - OFFSET_MM * TICK_FRACTION, horizontalLow, below + OFFSET_MM * TICK_FRACTION),
        lineShape("$prefix.width.end", DIMENSION_STYLE, horizontalHigh, below - OFFSET_MM * TICK_FRACTION, horizontalHigh, below + OFFSET_MM * TICK_FRACTION),
        label("$prefix.width.text", (horizontalLow + horizontalHigh) / 2, below, "${inchesText(horizontalHigh - horizontalLow)} in", "center", 0, 14),
        lineShape("$prefix.height", DIMENSION_STYLE, left, verticalLow, left, verticalHigh),
        lineShape("$prefix.height.start", DIMENSION_STYLE, left - OFFSET_MM * TICK_FRACTION, verticalLow, left + OFFSET_MM * TICK_FRACTION, verticalLow),
        lineShape("$prefix.height.end", DIMENSION_STYLE, left - OFFSET_MM * TICK_FRACTION, verticalHigh, left + OFFSET_MM * TICK_FRACTION, verticalHigh),
        label("$prefix.height.text", left, (verticalLow + verticalHigh) / 2, "${inchesText(verticalHigh - verticalLow)} in", "end", -10, 0),
    )
}

private fun viewNode(projection: Projection, parts: List<CardsPart>): JsonObject {
    val bodies = parts.map { part ->
        val (hLow, hHigh) = projection.horizontal(part)
        val (vLow, vHigh) = projection.vertical(part)
        rectShape("blueprint.${projection.id}.${part.id}", PIECE_STYLE, hLow, vLow, hHigh - hLow, vHigh - vLow)
    }
    val names = parts.map { part ->
        val (hLow, hHigh) = projection.horizontal(part)
        val (vLow, vHigh) = projection.vertical(part)
        mapOf("id" to "name.${projection.id}.${part.id}", "kind" to "label", "style" to DIMENSION_STYLE, "x_mm" to (hLow + hHigh) / 2, "y_mm" to (vLow + vHigh) / 2, "text" to part.name, "align" to "center", "dx_dp" to 0, "dy_dp" to 0)
    }
    val hLow = parts.minOf { projection.horizontal(it).first }
    val hHigh = parts.maxOf { projection.horizontal(it).second }
    val vLow = parts.minOf { projection.vertical(it).first }
    val vHigh = parts.maxOf { projection.vertical(it).second }
    val styles = mapOf(PIECE_STYLE to mapOf("stroke" to SHEET_INK, "weight" to 1.6), DIMENSION_STYLE to mapOf("stroke" to "#444444", "weight" to 1.0))
    val plan = specNode("blueprint.${projection.id}", "plan", mapOf("flex" to 1, "ground" to SHEET_GROUND, "shapes" to bodies + names + dimensionShapes("dim.${projection.id}", hLow, hHigh, vLow, vHigh), "styles" to styles, "legend" to emptyList<Any>(), "empty_text" to "Nothing to draw."))
    return specNode("blueprint.${projection.id}.view", "column", mapOf("gap" to 4, "flex" to 1), listOf(plainText("blueprint.${projection.id}.title", projection.title, variant = "caption", bold = true, color = SHEET_INK), specNode("blueprint.${projection.id}.box", "column", mapOf("height" to VIEW_HEIGHT_DP), listOf(plan))))
}

/** One light page for black-and-white letter paper: the stool's front, side and top in inches, the parts list and the steps. */
fun cardsBlueprintSpec(screen: CardsScreen, plan: CardsPlan): JsonObject {
    val parts = plan.parts.mapIndexed { n, part -> plainText("blueprint.part.$n", partLine(part), color = SHEET_INK) }
    val steps = plan.steps.mapIndexed { n, step -> plainText("blueprint.step.$n", "${n + 1}. ${step.title}" + (if (step.parts.isEmpty()) "" else ": " + step.parts.joinToString(", ") { id -> plan.parts.first { it.id == id }.name }) + (if (step.screws > 0) " (${step.screws} screws)" else ""), variant = "caption", color = SHEET_INK) }
    val views = specNode("blueprint.views", "row", mapOf("gap" to 8), PROJECTIONS.map { viewNode(it, plan.parts) })
    val lists = specNode(
        "blueprint.lists", "column", mapOf("gap" to 8),
        listOf(
            specNode("blueprint.parts", "column", mapOf("gap" to 2), listOf(plainText("blueprint.parts.title", "Parts", variant = "subheading", bold = true, color = SHEET_INK)) + parts),
            specNode("blueprint.steps", "column", mapOf("gap" to 2), listOf(plainText("blueprint.steps.title", "Steps", variant = "subheading", bold = true, color = SHEET_INK)) + steps),
        ),
    )
    val children = listOf(plainText("blueprint.title", "${screen.title.ifBlank { plan.title }}: blueprint", variant = "subheading", bold = true, color = SHEET_INK), lists, views)
    return page(children, "light", emptyMap(), SHEET_GROUND)
}

private fun answersOf(viewFile: Path, screen: CardsScreen): JsonObject {
    val name = screen.answersFile ?: throw CardsFormatException("the build view names no 'answers' file, and the engine's plan is not wired to give one yet")
    val file = viewFile.toAbsolutePath().parent.resolve(name)
    return runCatching { Json.parseToJsonElement(Files.readString(file)) }.getOrNull() as? JsonObject ?: throw CardsFormatException("the answers file ${file.fileName} is not a JSON object")
}

/** The cards or the blueprint a build view file draws, from the answers file it names. */
fun cardsSpecOfFile(viewFile: Path, blueprint: Boolean): JsonObject {
    val screen = readCardsScreen(Files.readString(viewFile))
    val plan = readCardsPlan(answersOf(viewFile, screen))
    return if (blueprint) cardsBlueprintSpec(screen, plan) else cardsSpec(screen, plan)
}

/** The answers file a build view names, beside the view; null when it names none. */
fun cardsAnswersFileOf(viewFile: Path, screen: CardsScreen): Path? = screen.answersFile?.let { viewFile.toAbsolutePath().parent.resolve(it) }
