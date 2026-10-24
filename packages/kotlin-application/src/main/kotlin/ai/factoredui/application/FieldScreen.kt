package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.nio.file.Files
import java.nio.file.Path

class FieldFormatException(message: String) : IllegalArgumentException(message)

data class FieldScreen(val title: String, val world: String, val gridPath: String, val inletPath: String, val outletPath: String, val layouts: List<String>, val ramp: String, val answersFile: String? = null)

private const val FRAME_PATH = "frame"

private fun textIn(root: JsonObject, key: String): String =
    (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw FieldFormatException("the field view has no '$key' text")

/** The field view a world gives: which answer holds the grid and the pipe series, and the layouts to try. */
fun readFieldScreen(text: String): FieldScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw FieldFormatException("the field view is not a JSON object")
    val layouts = (root["layouts"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.takeIf { id -> id.isString }?.content ?: throw FieldFormatException("a layout is not text") }
    if (layouts.isEmpty()) throw FieldFormatException("the field view names no layouts")
    return FieldScreen(
        (root["title"] as? JsonPrimitive)?.content.orEmpty(), textIn(root, "world"), textIn(root, "grid"),
        textIn(root, "inlet"), textIn(root, "outlet"), layouts, (root["ramp"] as? JsonPrimitive)?.content ?: "heat",
        (root["answers"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
    )
}

/** The file of answers a field view names, beside the view; null when the view names none. */
fun answersFileOf(viewFile: Path, screen: FieldScreen): Path? = screen.answersFile?.let { viewFile.toAbsolutePath().parent.resolve(it) }

private fun answersOf(viewFile: Path, screen: FieldScreen): Map<String, JsonObject> {
    val file = answersFileOf(viewFile, screen) ?: throw FieldFormatException("the field view names no 'answers' file, and the engine's solver is not wired to give one yet")
    val root = runCatching { Json.parseToJsonElement(Files.readString(file)) }.getOrNull() as? JsonObject ?: throw FieldFormatException("the answers file ${file.fileName} is not a JSON object")
    return root.mapValues { (layout, answer) -> answer as? JsonObject ?: throw FieldFormatException("the answer for '$layout' is not an object") }
}

/** The page a field view file draws, from the answers file it names. */
fun fieldSpecOfFile(viewFile: Path): JsonObject {
    val screen = readFieldScreen(Files.readString(viewFile))
    return fieldScreenSpec(screen, answersOf(viewFile, screen))
}

private fun elementAt(answer: JsonObject, path: String): JsonElement? =
    path.split('.').fold(answer as JsonElement?) { current, key -> (current as? JsonObject)?.get(key) }

private fun objectAt(answer: JsonObject, path: String, layout: String): JsonObject =
    elementAt(answer, path) as? JsonObject ?: throw FieldFormatException("the answer for '$layout' has no object at '$path'")

private fun numbers(list: JsonElement?): List<Double> = (list as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }

private fun numberOf(axis: JsonObject, key: String): Double = (axis[key] as? JsonPrimitive)?.doubleOrNull ?: throw FieldFormatException("a grid axis has no number '$key'")

private fun edgesOf(axis: JsonObject): List<Double>? = (axis["edges_mm"] as? JsonArray)?.let(::numbers)

private fun cellCountOf(axis: JsonObject): Int = edgesOf(axis)?.let { it.size - 1 } ?: numberOf(axis, "count").toInt()

private fun axisNode(axis: JsonObject): Map<String, Any?> {
    val shared = mapOf("count" to cellCountOf(axis), "label" to (axis["name"] as? JsonPrimitive)?.content, "unit" to (axis["unit"] as? JsonPrimitive)?.content)
    val edges = edgesOf(axis)
    return if (edges != null) shared + ("edges" to edges) else shared + mapOf("origin" to numberOf(axis, "origin_mm"), "step" to numberOf(axis, "step_mm"))
}

private fun framesOf(grid: JsonObject, layout: String): List<List<Double>> {
    val cells = (grid["axes"] as JsonArray).fold(1) { total, axis -> total * cellCountOf(axis as JsonObject) }
    val values = numbers(grid["values"])
    if (cells == 0 || values.size % cells != 0) throw FieldFormatException("the grid for '$layout' holds ${values.size} values, not a whole number of $cells-cell frames")
    return values.chunked(cells)
}

private data class LayoutField(val layout: String, val grid: JsonObject, val frames: List<List<Double>>)

private fun fieldOf(screen: FieldScreen, layout: String, answers: Map<String, JsonObject>): LayoutField {
    val answer = answers[layout] ?: throw FieldFormatException("the engine gave no answer for the layout '$layout'")
    val grid = objectAt(answer, screen.gridPath, layout)
    return LayoutField(layout, grid, framesOf(grid, layout))
}

private fun pipeSeries(answer: JsonObject, path: String): Map<String, JsonObject> =
    (elementAt(answer, path) as? JsonObject).orEmpty().mapValues { (pipe, series) -> series as? JsonObject ?: throw FieldFormatException("the series for pipe '$pipe' is not an object") }

private fun chartRows(inlets: Map<String, JsonObject>, outlets: Map<String, JsonObject>): List<JsonObject> {
    val hours = numbers(inlets.values.firstOrNull()?.get("t_h"))
    return hours.indices.map { index ->
        val cells = inlets.map { (pipe, series) -> "inlet.$pipe" to numbers(series["v"]).getOrNull(index) } + outlets.map { (pipe, series) -> "outlet.$pipe" to numbers(series["v"]).getOrNull(index) }
        val nested = cells.groupBy({ it.first.substringBefore('.') }, { it.first.substringAfter('.') to it.second })
        jsonOf(mapOf("t" to hours[index]) + nested.mapValues { (_, pipes) -> pipes.toMap() }) as JsonObject
    }
}

private fun chartNode(id: String, answer: JsonObject, screen: FieldScreen): JsonObject {
    val inlets = pipeSeries(answer, screen.inletPath)
    val outlets = pipeSeries(answer, screen.outletPath)
    val series = inlets.keys.map { mapOf("id" to "$it inlet", "path" to "inlet.$it", "axis" to "right") } + outlets.keys.map { mapOf("id" to "$it outlet", "path" to "outlet.$it", "axis" to "right") }
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id), "type" to JsonPrimitive("chart"), "children" to JsonArray(emptyList()),
            "props" to JsonObject(
                mapOf(
                    "x" to JsonPrimitive("t"), "x_label" to JsonPrimitive("hour"), "axes" to jsonOf(mapOf("right" to mapOf("unit" to "C"))),
                    "series" to jsonOf(series), "rows" to JsonArray(chartRows(inlets, outlets)), "ink" to JsonPrimitive(INK), "muted" to JsonPrimitive(MUTED),
                ),
            ),
        ),
    )
}

private fun heatmapNode(id: String, field: LayoutField, screen: FieldScreen, low: Double, high: Double): JsonObject {
    val axes = field.grid["axes"] as JsonArray
    return specNode(
        id, "heatmap",
        mapOf(
            "axes" to mapOf("x" to axisNode(axes[0] as JsonObject), "y" to axisNode(axes[1] as JsonObject)),
            "frames" to field.frames, "frame" to "{$FRAME_PATH}", "min" to low, "max" to high, "ramp" to screen.ramp, "unit" to "C",
        ),
    )
}

private fun resolutionNotice(layout: String, answer: JsonObject): JsonObject? {
    val resolution = answer["resolution"] as? JsonObject ?: return null
    if ((resolution["underresolved"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val cell = (resolution["near_pipe_cell_m"] as? JsonPrimitive)?.content
    val penetration = (resolution["daily_penetration_m"] as? JsonPrimitive)?.content
    return textNode("$layout.resolution", "near-pipe soil coarse: daily storage underresolved (near-pipe cell $cell m, daily penetration $penetration m)", variant = "caption", bold = true, color = INK)
}

private fun layoutTab(field: LayoutField, answer: JsonObject, screen: FieldScreen, low: Double, high: Double): JsonObject =
    specNode(
        field.layout, "column", mapOf("gap" to 12),
        listOfNotNull(resolutionNotice(field.layout, answer), heatmapNode("${field.layout}.heatmap", field, screen, low, high), chartNode("${field.layout}.chart", answer, screen)),
    )

private fun frameControls(frameCount: Int, hours: List<Double>): List<JsonObject> = listOf(
    textNode("frame.caption", "Hour of the day, ${hours.firstOrNull()?.let { "%.0f".format(it) } ?: "0"} to ${hours.lastOrNull()?.let { "%.0f".format(it) } ?: "0"}", variant = "caption", color = MUTED),
    specNode("frame.slider", "slider", mapOf("value" to "{$FRAME_PATH}", "min" to 0, "max" to maxOf(0, frameCount - 1), "step" to 1)),
)

/** One tab per layout, each a colour section through the ground and the pipes' inlet and outlet air, with the one slider that moves every layout's frame. */
fun fieldScreenSpec(screen: FieldScreen, answers: Map<String, JsonObject>): JsonObject {
    val fields = screen.layouts.map { fieldOf(screen, it, answers) }
    val everyValue = fields.flatMap { it.frames.flatten() }
    val low = everyValue.min()
    val high = everyValue.max()
    val first = fields.first()
    val hours = numbers(first.grid["times_h"])
    val notice = (answers.getValue(first.layout)["fixture"] as? JsonPrimitive)?.content?.let { textNode("fixture.notice", "FIXTURE: $it", variant = "caption", bold = true, color = MUTED) }
    val tabs = specNode("layouts", "tabs", mapOf("items" to screen.layouts, "selectedIndex" to "{layout}"), fields.map { layoutTab(it, answers.getValue(it.layout), screen, low, high) })
    val children = listOfNotNull(textNode("screen.title", screen.title, variant = "subheading", bold = true, color = INK), notice, tabs) + frameControls(first.frames.size, hours)
    val root = specNode("root", "column", mapOf("gap" to 12, "padding" to 16, "background" to HUD), children)
    return JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"),
            "state" to jsonOf(mapOf(FRAME_PATH to 0, "layout" to 0)), "root" to root,
        ),
    )
}
