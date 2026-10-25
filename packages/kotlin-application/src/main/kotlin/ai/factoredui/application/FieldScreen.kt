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

/** How a view gets its answers from the engine: the system whose solution each layout picks and the solver function whose values are drawn. */
data class FieldSolve(val system: String, val function: String)

data class FieldScreen(val title: String, val world: String, val gridPath: String, val inletPath: String, val outletPath: String, val layouts: List<String>, val ramp: String, val answersFile: String? = null, val labels: Map<String, String> = emptyMap(), val axisLabels: Map<String, String> = emptyMap(), val temperatureUnit: String = "C", val solve: FieldSolve? = null)

private const val FRAME_PATH = "frame"
private const val MM_PER_FOOT = 304.8

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
        (root["answers"] as? JsonPrimitive)?.takeIf { it.isString }?.content, textMapIn(root, "labels"), textMapIn(root, "axis_labels"), (root["temperature_unit"] as? JsonPrimitive)?.content ?: "C", solveOf(root),
    )
}

private fun solveOf(root: JsonObject): FieldSolve? = (root["solve"] as? JsonObject)?.let { FieldSolve(textIn(it, "system"), textIn(it, "function")) }

private fun textMapIn(root: JsonObject, key: String): Map<String, String> =
    (root[key] as? JsonObject).orEmpty().mapValues { (name, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw FieldFormatException("the '$key' entry '$name' is not text") }

/** The file of answers a field view names, beside the view; null when the view names none. */
fun answersFileOf(viewFile: Path, screen: FieldScreen): Path? = screen.answersFile?.let { viewFile.toAbsolutePath().parent.resolve(it) }

private fun answersOf(viewFile: Path, screen: FieldScreen): Map<String, JsonObject> {
    val file = answersFileOf(viewFile, screen) ?: throw FieldFormatException("the field view names no 'answers' file, and the engine's solver is not wired to give one yet")
    val root = runCatching { Json.parseToJsonElement(Files.readString(file)) }.getOrNull() as? JsonObject ?: throw FieldFormatException("the answers file ${file.fileName} is not a JSON object")
    return root.mapValues { (layout, answer) -> answer as? JsonObject ?: throw FieldFormatException("the answer for '$layout' is not an object") }
}

private const val SOLVE_TIMEOUT_SECONDS = 900L
private const val HEX_BYTE = "%02x"

private fun solvedValues(engine: EngineRuntime, worldFile: Path, solve: FieldSolve, layout: String): JsonObject {
    val text = engine.answer(listOf("solve", worldFile.toAbsolutePath().toString(), "--use", "${solve.system}=$layout"), SOLVE_TIMEOUT_SECONDS)
    val root = Json.parseToJsonElement(text.substring(text.indexOf('{').coerceAtLeast(0))) as? JsonObject ?: throw FieldFormatException("the solve for '$layout' is not a JSON object")
    val solver = (root["solvers"] as? JsonArray).orEmpty().map { it as JsonObject }.firstOrNull { (it["function"] as? JsonPrimitive)?.content == solve.function }
        ?: throw FieldFormatException("the engine answered no '${solve.function}' for the layout '$layout': ${text.take(200)}")
    val status = (solver["status"] as? JsonPrimitive)?.content
    if (status != "known") throw FieldFormatException("the engine's ${solve.function} for the layout '$layout' is $status: ${(solver["detail"] as? JsonPrimitive)?.content.orEmpty()}")
    return solver["values"] as? JsonObject ?: throw FieldFormatException("the engine's ${solve.function} for the layout '$layout' has no values")
}

private fun configHash(worldFile: Path): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(worldFile)).joinToString("") { HEX_BYTE.format(it) }

/** The page a field view draws from what the engine solves, each layout asked by choosing its solution, nothing stored in the view. */
fun fieldSpecBySolving(engine: EngineRuntime, worldFile: Path, screen: FieldScreen): JsonObject {
    val solve = screen.solve ?: throw FieldFormatException("the field view names no 'solve'")
    val answers = screen.layouts.associateWith { solvedValues(engine, worldFile, solve, it) } + ("provenance" to JsonObject(mapOf("config_sha256" to JsonPrimitive(configHash(worldFile)))))
    return fieldScreenSpec(screen, answers)
}

/** The page a field view draws: solved by the engine when the view says how, else from the answers file it names. */
internal fun fieldOfView(engine: EngineRuntime, worlds: List<EngineWorld>, viewFile: Path): JsonObject {
    val screen = readFieldScreen(Files.readString(viewFile))
    if (screen.solve == null) return fieldSpecOfFile(viewFile)
    val world = worlds.firstOrNull { it.id == screen.world } ?: throw FieldFormatException("the engine serves no world '${screen.world}'")
    return fieldSpecBySolving(engine, world.path, screen)
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

private fun feet(millimetres: Double): Double = Math.round(millimetres / MM_PER_FOOT * 100.0) / 100.0

/** A heatmap axis runs up from its origin; an axis that runs down (z below the floor) is drawn as its depth, so the first row sits at the top of the screen. Lengths in mm are drawn in feet. */
private fun axisNode(axis: JsonObject, screen: FieldScreen): Map<String, Any?> {
    val name = (axis["name"] as? JsonPrimitive)?.content.orEmpty()
    val shared = mapOf("count" to cellCountOf(axis), "label" to "${screen.axisLabels[name] ?: name} (ft)", "unit" to "ft")
    val edges = edgesOf(axis)
    if (edges != null) return shared + ("edges" to edges.map(::feet))
    val origin = feet(numberOf(axis, "origin_mm"))
    val step = feet(numberOf(axis, "step_mm"))
    return if (step < 0) shared + mapOf("origin" to (if (origin == 0.0) 0.0 else -origin), "step" to -step) else shared + mapOf("origin" to origin, "step" to step)
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
    return LayoutField(layout, grid, framesOf(grid, layout).map { frame -> frame.map { inUnit(it, screen) } })
}

private const val SINGLE_TUBE = "tube"

/** The pipe series of an answer: one series per pipe id, or a single series when the answer gives the one tube's own. */
private fun pipeSeries(answer: JsonObject, path: String): Map<String, JsonObject> {
    val found = elementAt(answer, path) as? JsonObject ?: return emptyMap()
    if (found.containsKey("v")) return mapOf(SINGLE_TUBE to found)
    return found.mapValues { (pipe, series) -> series as? JsonObject ?: throw FieldFormatException("the series for pipe '$pipe' is not an object") }
}

private fun seriesIn(answer: JsonObject, key: String): JsonObject? = (answer[key] as? JsonObject)?.takeIf { it.containsKey("v") }

private fun chartRows(inlets: Map<String, JsonObject>, outlets: Map<String, JsonObject>, house: JsonObject?, screen: FieldScreen): List<JsonObject> {
    val hours = numbers(inlets.values.firstOrNull()?.get("t_h"))
    fun at(series: JsonObject, index: Int): Double? = numbers(series["v"]).getOrNull(index)?.let { inUnit(it, screen) }
    return hours.indices.map { index ->
        val cells = inlets.map { (pipe, series) -> "inlet.$pipe" to at(series, index) } + outlets.map { (pipe, series) -> "outlet.$pipe" to at(series, index) }
        val nested = cells.groupBy({ it.first.substringBefore('.') }, { it.first.substringAfter('.') to it.second })
        val houseCell = house?.let { mapOf("house" to at(it, index)) } ?: emptyMap()
        jsonOf(mapOf("t" to hours[index]) + nested.mapValues { (_, pipes) -> pipes.toMap() } + houseCell) as JsonObject
    }
}

private fun chartNode(id: String, answer: JsonObject, screen: FieldScreen): JsonObject {
    val inlets = pipeSeries(answer, screen.inletPath)
    val outlets = pipeSeries(answer, screen.outletPath)
    val house = seriesIn(answer, "house_air_c")
    val series = inlets.keys.map { mapOf("id" to "$it inlet", "path" to "inlet.$it", "axis" to "right") } + outlets.keys.map { mapOf("id" to "$it outlet", "path" to "outlet.$it", "axis" to "right") } +
        listOfNotNull(house?.let { mapOf("id" to "house air", "path" to "house", "axis" to "right") })
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id), "type" to JsonPrimitive("chart"), "children" to JsonArray(emptyList()),
            "props" to JsonObject(
                mapOf(
                    "x" to JsonPrimitive("t"), "x_label" to JsonPrimitive("hour"), "axes" to jsonOf(mapOf("right" to mapOf("unit" to screen.temperatureUnit))),
                    "series" to jsonOf(series), "rows" to JsonArray(chartRows(inlets, outlets, house, screen)), "ink" to JsonPrimitive(INK), "muted" to JsonPrimitive(MUTED),
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
            "x" to axisNode(axes[0] as JsonObject, screen), "y" to axisNode(axes[1] as JsonObject, screen),
            "frames" to field.frames, "frame" to "{$FRAME_PATH}", "min" to low, "max" to high, "ramp" to screen.ramp, "unit" to screen.temperatureUnit,
        ),
    )
}

private const val HASH_SHOWN = 8
private const val TENTH = 10.0
private const val FAHRENHEIT_PER_CELSIUS = 1.8
private const val FAHRENHEIT_OFFSET = 32.0

private fun inUnit(celsius: Double, screen: FieldScreen): Double = if (screen.temperatureUnit == "F") celsius * FAHRENHEIT_PER_CELSIUS + FAHRENHEIT_OFFSET else celsius

/** A number to two decimals with no trailing zeros: 0.2915 reads 0.29 and 0.5 reads 0.5. */
private fun tidy(value: Double): String = java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Where an answer came from, so a reader can tell it from a stale run: the engine, the domains and the config's hash. */
private fun provenanceNote(provenance: JsonObject?): JsonObject? {
    if (provenance == null) return null
    fun part(key: String) = (provenance[key] as? JsonPrimitive)?.content
    val config = part("config_sha256")?.take(HASH_SHOWN)
    val line = listOfNotNull(part("engine")?.let { "engine $it" }, part("domains")?.let { "domains $it" }, config?.let { "config $it" }).joinToString(", ")
    return if (line.isEmpty()) null else textNode("provenance.note", "From the engine: $line", variant = "caption", color = MUTED)
}

private const val SWITCH_ON_NOTE = "below the fan's switch-on temperature"

/** Says why the soil picture is flat when the fan never ran: the house air stayed under the temperature that starts it. */
private fun fanNote(layout: String, answer: JsonObject, screen: FieldScreen): JsonObject? {
    val fan = seriesIn(answer, "fan_on") ?: return null
    if (numbers(fan["v"]).any { it != 0.0 }) return null
    val peak = seriesIn(answer, "house_air_c")?.let { numbers(it["v"]).maxOrNull() }?.let { inUnit(it, screen) }
    val peakText = peak?.let { ", the house air peaked at ${tidy(it)} ${screen.temperatureUnit}," } ?: ""
    return textNode("$layout.fan", "Fan off all day$peakText $SWITCH_ON_NOTE.", variant = "caption", color = MUTED)
}

private fun resolutionNotice(layout: String, answer: JsonObject): JsonObject? {
    val resolution = answer["resolution"] as? JsonObject ?: return null
    if ((resolution["underresolved"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val cell = (resolution["near_pipe_cell_m"] as? JsonPrimitive)?.doubleOrNull?.let(::tidy)
    val penetration = (resolution["daily_penetration_m"] as? JsonPrimitive)?.doubleOrNull?.let(::tidy)
    return textNode("$layout.resolution", "near-pipe soil coarse: daily storage underresolved (near-pipe cell $cell m, daily penetration $penetration m)", variant = "caption", bold = true, color = INK)
}

private fun layoutTab(field: LayoutField, answer: JsonObject, screen: FieldScreen, low: Double, high: Double): JsonObject =
    specNode(
        field.layout, "column", mapOf("gap" to 12),
        listOfNotNull(resolutionNotice(field.layout, answer), fanNote(field.layout, answer, screen), heatmapNode("${field.layout}.heatmap", field, screen, low, high), chartNode("${field.layout}.chart", answer, screen)),
    )

private fun frameControls(frameCount: Int, hours: List<Double>): List<JsonObject> = listOf(
    textNode("frame.caption", "Hour of the day, ${hours.firstOrNull()?.let { "%.0f".format(it) } ?: "0"} to ${hours.lastOrNull()?.let { "%.0f".format(it) } ?: "0"}", variant = "caption", color = MUTED),
    specNode("frame.slider", "slider", mapOf("value" to "{$FRAME_PATH}", "min" to 0, "max" to maxOf(0, frameCount - 1), "step" to 1)),
)

/** One tab per layout, each a colour section through the ground and the pipes' inlet and outlet air, with the one slider that moves every layout's frame. */
fun fieldScreenSpec(screen: FieldScreen, answers: Map<String, JsonObject>): JsonObject {
    val fields = screen.layouts.map { fieldOf(screen, it, answers) }
    val everyValue = fields.flatMap { it.frames.flatten() }
    val low = Math.floor(everyValue.min() * TENTH) / TENTH
    val high = Math.ceil(everyValue.max() * TENTH) / TENTH
    val first = fields.first()
    val hours = numbers(first.grid["times_h"])
    val notice = (answers.getValue(first.layout)["fixture"] as? JsonPrimitive)?.content?.let { textNode("fixture.notice", "FIXTURE: $it", variant = "caption", bold = true, color = MUTED) }
    val provenance = provenanceNote(answers["provenance"])
    val tabs = specNode("layouts", "tabs", mapOf("items" to screen.layouts.map { screen.labels[it] ?: it }, "selectedIndex" to "{layout}"), fields.map { layoutTab(it, answers.getValue(it.layout), screen, low, high) })
    val children = listOfNotNull(textNode("screen.title", screen.title, variant = "subheading", bold = true, color = INK), notice, provenance) + frameControls(first.frames.size, hours) + tabs
    val root = scrollingRoot(children, HUD)
    return JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"),
            "state" to jsonOf(mapOf(FRAME_PATH to 0, "layout" to 0)), "root" to root,
        ),
    )
}
