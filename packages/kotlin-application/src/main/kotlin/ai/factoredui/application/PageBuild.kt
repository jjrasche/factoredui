package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

internal const val STEP_PATH = "ui.step"

private fun signed(value: Double) = if (value > 0) "+${formatFigure(value)}" else formatFigure(value)

private fun listLine(label: String, values: Map<String, Double>, format: (Double) -> String): String? =
    if (values.isEmpty()) null else "$label: " + values.entries.joinToString(", ") { "${humanize(it.key)} ${format(it.value)}" }

private fun actLines(act: Act): List<String> = listOfNotNull(
    "${humanize(act.flow.replace('-', '_'))}:",
    act.people.takeIf { it.isNotEmpty() }?.let { people -> "  people: ${people.joinToString(", ")}" + if (act.learners.isEmpty()) "" else "; learning: ${act.learners.joinToString(", ")}" },
    act.places.takeIf { it.isNotEmpty() }?.let { "  placed: ${it.joinToString(", ")}" },
    listLine("  drew from stocks", act.moves, ::signed),
)

private fun momentLines(unit: String, index: Int, count: Int, keyframe: Keyframe): List<String> {
    val head = "Step ${index + 1} of $count, $unit ${formatFigure(keyframe.t)}"
    val acts = if (keyframe.acts.isEmpty()) listOf("Nothing has run yet.") else keyframe.acts.flatMap(::actLines)
    return listOfNotNull(head) + acts + listOfNotNull(listLine("stocks now", keyframe.stocks, ::formatFigure), listLine("totals so far", keyframe.objective, ::formatFigure))
}

private fun momentCard(unit: String, index: Int, count: Int, keyframe: Keyframe): JsonObject =
    specNode(
        "build.step.$index", "card", mapOf("padding" to 10, "gap" to 3),
        momentLines(unit, index, count, keyframe).mapIndexed { line, text -> textNode("build.step.$index.line.$line", text, bold = line == 0, variant = if (line == 0) null else "caption") },
        visible = "{$STEP_PATH}=$index",
    )

private fun stepper(count: Int): JsonObject {
    fun move(id: String, label: String, by: Int) =
        JsonObject(specNode(id, "button", mapOf("label" to label, "variant" to "secondary", "size" to "sm", "flex" to 1)) + ("action" to jsonOf(mapOf("action" to "set_binding", "params" to mapOf("path" to STEP_PATH, "add" to by, "min" to 0, "max" to count - 1)))))
    return specNode("build.stepper", "row", mapOf("gap" to 8), listOf(move("build.previous", "Previous", -1), move("build.next", "Next", 1)))
}

/** The build one key moment at a time: a slider and previous and next over the moments, and under them the card of the one chosen. */
internal fun buildNode(built: Built): JsonObject {
    val title = textNode("build.title", "Build, step by step", variant = "subheading", bold = true)
    val build = built.build ?: return specNode("build", "column", mapOf("gap" to 8), listOf(title, textNode("build.failure", "The build could not be stepped: ${built.failure}", variant = "caption", color = MUTED_INK)))
    val count = build.keyframes.size
    val slider = specNode("build.slider", "slider", mapOf("value" to "{$STEP_PATH}", "min" to 0, "max" to (count - 1).coerceAtLeast(1), "step" to 1))
    return specNode("build", "column", mapOf("gap" to 8), listOf(title, slider, stepper(count)) + build.keyframes.mapIndexed { index, keyframe -> momentCard(build.unit, index, count, keyframe) })
}
