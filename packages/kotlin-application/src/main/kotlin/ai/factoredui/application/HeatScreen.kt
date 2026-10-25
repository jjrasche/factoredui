package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.nio.file.Files
import java.nio.file.Path

class HeatFormatException(message: String) : IllegalArgumentException(message)

data class HeatScreen(val title: String, val world: String, val site: String, val ramp: String, val unit: String)

private const val HOURS_IN_DAY = 24
private const val HOUR_PATH = "heat.hour"
private const val OUTSIDE_AIR = "outside_air_f"
private const val TENTH = 10.0

private fun textIn(root: JsonObject, key: String): String =
    (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw HeatFormatException("the heat view has no '$key' text")

/** The heat view a world gives: the site whose boxes it colours and the ramp that colours them. */
fun readHeatScreen(text: String): HeatScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw HeatFormatException("the heat view is not a JSON object")
    return HeatScreen((root["title"] as? JsonPrimitive)?.content.orEmpty(), textIn(root, "world"), textIn(root, "site"), (root["ramp"] as? JsonPrimitive)?.content ?: "cool_warm", (root["unit"] as? JsonPrimitive)?.content ?: "F")
}

private fun heatStepsOf(season: JsonObject): List<JsonObject> = (season["steps"] as? JsonArray).orEmpty().map { it as? JsonObject ?: throw HeatFormatException("a step is not an object") }

private fun heatNumberAt(element: JsonElement?): Double? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

private fun outsideAir(step: JsonObject): Double = heatNumberAt((step["series"] as? JsonObject)?.get(OUTSIDE_AIR)) ?: throw HeatFormatException("a step has no $OUTSIDE_AIR")

/** The whole day of the season with the lowest mean outside air, counted from zero. */
fun heatDayOf(season: JsonObject): Int {
    val steps = heatStepsOf(season)
    val days = steps.size / HOURS_IN_DAY
    if (days == 0) throw HeatFormatException("the season has no whole day of $HOURS_IN_DAY hours")
    return (0 until days).minBy { day -> steps.subList(day * HOURS_IN_DAY, (day + 1) * HOURS_IN_DAY).sumOf(::outsideAir) / HOURS_IN_DAY }
}

private fun stockHours(steps: List<JsonObject>, day: Int, stock: String): List<Double> = (0 until HOURS_IN_DAY).map { hour ->
    heatNumberAt(((steps[day * HOURS_IN_DAY + hour]["stocks"] as? JsonObject)?.get(stock))) ?: throw HeatFormatException("the season has no stock '$stock' at hour $hour of day ${day + 1}")
}

private fun readableStock(stock: String): String = stock.removeSuffix("_f").replace('_', ' ')

private fun colouredByStock(box: Map<String, Any?>, steps: List<JsonObject>, day: Int): Map<String, Any?> {
    val stock = box["stock"] as? String ?: return box - "stock"
    return (box - "stock") + ("values" to stockHours(steps, day, stock))
}

private fun chartNode(steps: List<JsonObject>, day: Int, stocks: List<String>): JsonObject {
    val rows = (0 until HOURS_IN_DAY).map { hour ->
        val values = stocks.associate { stock -> readableStock(stock) to stockHours(steps, day, stock)[hour] }
        jsonOf(mapOf("t" to hour, "outside" to outsideAir(steps[day * HOURS_IN_DAY + hour])) + values)
    }
    val series = listOf(mapOf("id" to "outside air", "path" to "outside", "axis" to "right")) + stocks.map { mapOf("id" to readableStock(it), "path" to readableStock(it), "axis" to "right") }
    return specNode("heat.chart", "chart", mapOf("x" to "t", "x_label" to "hour", "axes" to mapOf("right" to mapOf("unit" to "F")), "series" to series, "rows" to rows, "ink" to INK, "muted" to MUTED))
}

/** The site's boxes colouredByStock by their stock's temperature through the coldest day of the season, one slider through its hours, and the chart of the same series. */
fun heatSpec(screen: HeatScreen, siteBoxes: List<Map<String, Any?>>, season: JsonObject): JsonObject {
    val steps = heatStepsOf(season)
    val day = heatDayOf(season)
    val boxes = siteBoxes.map { colouredByStock(it, steps, day) }
    val stocks = siteBoxes.mapNotNull { it["stock"] as? String }.distinct()
    val everyValue = boxes.flatMap { (it["values"] as? List<*>).orEmpty().filterIsInstance<Double>() }
    val low = Math.floor((everyValue.minOrNull() ?: 0.0) * TENTH) / TENTH
    val high = Math.ceil((everyValue.maxOrNull() ?: 0.0) * TENTH) / TENTH
    val styles = boxes.map { it["style"] as String }.distinct().associateWith { mapOf("stroke" to "#8aa0b4") }
    val volume = specNode(
        "heat.volume", "volume",
        mapOf(
            "boxes" to boxes, "grade_mm" to GRADE_MM, "styles" to styles, "empty_text" to "The site has nothing to draw.",
            "frame" to "{$HOUR_PATH}", "value_ramp" to screen.ramp, "min" to low, "max" to high, "unit" to screen.unit, "height" to VOLUME_HEIGHT_DP,
        ),
    )
    val outside = (0 until HOURS_IN_DAY).map { outsideAir(steps[day * HOURS_IN_DAY + it]) }
    val title = textNode("heat.title", screen.title, variant = "subheading", bold = true, color = INK)
    val dayLine = textNode("heat.day", "Day ${day + 1} of the season, the coldest by outside air: ${tidyDegrees(outside.min())} to ${tidyDegrees(outside.max())} F", variant = "caption", color = MUTED)
    val slider = specNode("heat.slider", "slider", mapOf("value" to "{$HOUR_PATH}", "min" to 0, "max" to HOURS_IN_DAY - 1, "step" to 1))
    val hourLine = textNode("heat.hourlabel", "Hour of the day, 0 to ${HOURS_IN_DAY - 1}", variant = "caption", color = MUTED)
    val children = listOf(title, dayLine, volume, hourLine, slider, chartNode(steps, day, stocks))
    return JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"), "state" to jsonOf(mapOf(HOUR_PATH to 0)),
            "root" to specNode("root", "column", mapOf("gap" to 12, "padding" to 16, "background" to HUD), children),
        ),
    )
}

private const val VOLUME_HEIGHT_DP = 360

private fun tidyDegrees(value: Double): String = java.math.BigDecimal(value).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** The heat view file and the site view it colours, side by side in the world's views directory. */
fun heatSiteFileOf(viewFile: Path, screen: HeatScreen): Path = viewFile.toAbsolutePath().parent.resolve(screen.site + ".site.json")

internal fun heatOfView(engine: EngineRuntime, worlds: List<EngineWorld>, viewFile: Path, timings: Timings): JsonObject {
    val screen = readHeatScreen(Files.readString(viewFile))
    val world = worlds.firstOrNull { it.id == screen.world } ?: throw HeatFormatException("the engine serves no world '${screen.world}'")
    val site = readSiteScreen(Files.readString(heatSiteFileOf(viewFile, screen)))
    val boxes = siteBoxesOf(engine, worlds, site, timings)
    val text = timings.timed("simulate") { simulateSystem(engine, world.path) }
    val season = Json.parseToJsonElement(text.substring(text.indexOf('{').coerceAtLeast(0))) as? JsonObject ?: throw HeatFormatException("the simulation is not a JSON object")
    return timings.timed("spec") { heatSpec(screen, boxes, season) }
}
