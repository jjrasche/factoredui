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

data class AirZone(val label: String, val member: String, val systems: Set<String>, val stock: String, val layers: Int = 1, val stopAt: String? = null)

data class HeatScreen(val title: String, val world: String, val site: String, val ramp: String, val unit: String, val clearStyles: Set<String> = emptySet(), val clearBoxes: List<String> = emptyList(), val airZones: List<AirZone> = emptyList(), val rampStops: List<Pair<Double, String>>? = null, val weekStartDay: Int? = null)

private const val HOURS_IN_DAY = 24
private const val HOUR_PATH = "heat.hour"
private const val OUTSIDE_AIR = "outside_air_f"
private const val TENTH = 10.0

private fun textIn(root: JsonObject, key: String): String =
    (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw HeatFormatException("the heat view has no '$key' text")

/** The heat view a world gives: the site whose boxes it colours and the ramp that colours them. */
fun readHeatScreen(text: String): HeatScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw HeatFormatException("the heat view is not a JSON object")
    return HeatScreen((root["title"] as? JsonPrimitive)?.content.orEmpty(), textIn(root, "world"), textIn(root, "site"), (root["ramp"] as? JsonPrimitive)?.content ?: "cool_warm", (root["unit"] as? JsonPrimitive)?.content ?: "F",
        (root["clear_styles"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }.toSet(),
        (root["clear_boxes"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }, airZonesOf(root), rampStopsOf(root), (root["week_start_day"] as? JsonPrimitive)?.content?.toIntOrNull())
}

private fun airZonesOf(root: JsonObject): List<AirZone> = (root["air_zones"] as? JsonArray).orEmpty().map { entry ->
    val zone = entry as? JsonObject ?: throw HeatFormatException("an air zone is not an object")
    AirZone(textIn(zone, "label"), textIn(zone, "member"), (zone["systems"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }.toSet(), textIn(zone, "stock"),
        (zone["layers"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1, (zone["stop_at"] as? JsonPrimitive)?.content)
}

private fun rampStopsOf(root: JsonObject): List<Pair<Double, String>>? = (root["ramp_stops"] as? JsonArray)?.map { stop ->
    val pair = stop as? JsonArray ?: throw HeatFormatException("a ramp stop is not a [value, colour] pair")
    (pair[0] as JsonPrimitive).doubleOrNull!! to (pair[1] as JsonPrimitive).content
}

private const val HOURS_IN_WEEK = 168
private const val DAYS_BEFORE = 3
private const val FAN_FLOW = "greenhouse-hour"
private const val FAN_STOCK = "fan_kwh"

private fun fanKwhIn(step: JsonObject): Double =
    heatNumberAt((((step["flows"] as? JsonObject)?.get(FAN_FLOW) as? JsonObject)?.get("moves") as? JsonObject)?.get(FAN_STOCK)) ?: 0.0

/** The first step of the week shown: the view's own start day, else three days before the shown day, kept inside the season. */
private fun weekStartOf(screen: HeatScreen, day: Int, stepCount: Int): Int {
    val wanted = (screen.weekStartDay ?: (day - DAYS_BEFORE)) * HOURS_IN_DAY
    return wanted.coerceIn(0, maxOf(0, stepCount - HOURS_IN_WEEK))
}

/** The fan hour by hour over a week, from the engine's own hourly fan energy: on when it is above zero, with the count of on-hours and the week's kWh. */
private fun fanNodes(screen: HeatScreen, steps: List<JsonObject>, day: Int): List<JsonObject> {
    val from = weekStartOf(screen, day, steps.size)
    val hours = steps.subList(from, minOf(steps.size, from + HOURS_IN_WEEK)).map(::fanKwhIn)
    val onHours = hours.count { it > 0.0 }
    val total = hours.sum()
    val strip = specNode(
        "heat.fan.strip", "heatmap",
        mapOf(
            "x" to mapOf("origin" to 0, "step" to 1, "count" to hours.size, "label" to "hour of the week"), "y" to mapOf("origin" to 0, "step" to 1, "count" to 1, "label" to ""),
            "frames" to listOf(hours.map { if (it > 0.0) 1.0 else 0.0 }), "frame" to 0, "min" to 0, "max" to 1, "unit" to "on",
            "ramp" to mapOf("stops" to listOf(listOf(0, "#1b2433"), listOf(1, "#f2d43d"))), "height" to FAN_STRIP_HEIGHT_DP,
        ),
    )
    val label = textNode("heat.fan.label", "Fan: on ${onHours} of ${hours.size} hours, ${tidyDegrees(total)} kWh (days ${from / HOURS_IN_DAY + 1} to ${(from + hours.size - 1) / HOURS_IN_DAY + 1})", variant = "caption", bold = true, color = INK)
    return listOf(label, strip)
}

private const val FAN_STRIP_HEIGHT_DP = 70

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

private fun number(box: Map<String, Any?>, key: String): Double = (box[key] as Number).toDouble()

/** The air of a zone as translucent layers over the pieces of its systems, coloured by the zone's air stock; with a `stop_at` each layer ends where those pieces (the sloped glazing) reach its height, so no air sits outside. */
private fun airBoxes(zone: AirZone, siteBoxes: List<Map<String, Any?>>, steps: List<JsonObject>, day: Int): List<Map<String, Any?>> {
    val own = siteBoxes.filter { (it["id"] as String).startsWith(zone.member + "/") && it["system"] in zone.systems }
    if (own.isEmpty()) throw HeatFormatException("the air zone '${zone.label}' has no pieces of ${zone.systems} in '${zone.member}'")
    val west = own.minOf { number(it, "x_mm") - number(it, "width_mm") / 2 }
    val east = own.maxOf { number(it, "x_mm") + number(it, "width_mm") / 2 }
    val south = own.minOf { number(it, "y_mm") - number(it, "depth_mm") / 2 }
    val north = own.maxOf { number(it, "y_mm") + number(it, "depth_mm") / 2 }
    val floor = own.minOf { number(it, "z_mm") }
    val ridge = own.maxOf { number(it, "z_mm") + number(it, "height_mm") }
    val values = stockHours(steps, day, zone.stock)
    val lids = zone.stopAt?.let { part -> own.filter { (it["id"] as String).contains(part) } }.orEmpty()
    return (0 until zone.layers).mapNotNull { layer ->
        val bottom = floor + (ridge - floor) * layer / zone.layers
        val top = floor + (ridge - floor) * (layer + 1) / zone.layers
        val from = if (lids.isEmpty()) south else lids.filter { number(it, "z_mm") + number(it, "height_mm") >= top }.minOfOrNull { number(it, "y_mm") - number(it, "depth_mm") / 2 }
        if (from == null || from >= north) null
        else mapOf(
            "id" to "air:${zone.label}:$layer", "style" to AIR_PREFIX + zone.label, "x_mm" to (west + east) / 2, "y_mm" to (from + north) / 2, "z_mm" to bottom,
            "width_mm" to east - west, "depth_mm" to north - from, "height_mm" to top - bottom, "rotation_deg" to 0.0, "estimate" to false, "values" to values,
        )
    }
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
    val air = screen.airZones.flatMap { airBoxes(it, siteBoxes, steps, day) }
    val boxes = (siteBoxes.map { colouredByStock(it, steps, day) } + air).map { box -> if (screen.clearBoxes.any { part -> (box["id"] as String).contains(part) }) box + ("style" to CLEAR_PREFIX + (box["style"] as String)) else box }
    val stocks = siteBoxes.mapNotNull { it["stock"] as? String }.distinct()
    val everyValue = boxes.flatMap { (it["values"] as? List<*>).orEmpty().filterIsInstance<Double>() }
    val low = Math.floor((everyValue.minOrNull() ?: 0.0) * TENTH) / TENTH
    val high = Math.ceil((everyValue.maxOrNull() ?: 0.0) * TENTH) / TENTH
    val styles = boxes.map { it["style"] as String }.distinct().associateWith { name -> if (name.startsWith(AIR_PREFIX)) mapOf("stroke" to "#8aa0b4", "opacity" to AIR_OPACITY) else if (name in screen.clearStyles || name.startsWith(CLEAR_PREFIX)) mapOf("stroke" to "#8aa0b4", "opacity" to CLEAR_OPACITY) else mapOf("stroke" to "#8aa0b4") }
    val volume = specNode(
        "heat.volume", "volume",
        mapOf(
            "boxes" to boxes, "grade_mm" to GRADE_MM, "styles" to styles, "empty_text" to "The site has nothing to draw.",
            "frame" to "{$HOUR_PATH}", "value_ramp" to (screen.rampStops?.let { stops -> mapOf("stops" to stops.map { listOf(it.first, it.second) }) } ?: screen.ramp), "min" to low, "max" to high, "unit" to screen.unit, "height" to VOLUME_HEIGHT_DP,
        ),
    )
    val outside = (0 until HOURS_IN_DAY).map { outsideAir(steps[day * HOURS_IN_DAY + it]) }
    val coldestHour = outside.indexOf(outside.min())
    val title = textNode("heat.title", screen.title, variant = "subheading", bold = true, color = INK)
    val dayLine = textNode("heat.day", "Day ${day + 1} of the season, the coldest by outside air: ${tidyDegrees(outside.min())} to ${tidyDegrees(outside.max())} F", variant = "caption", color = MUTED)
    val slider = specNode("heat.slider", "slider", mapOf("value" to "{$HOUR_PATH}", "min" to 0, "max" to HOURS_IN_DAY - 1, "step" to 1))
    val stepButtons = specNode("heat.steps", "row", mapOf("gap" to 12), listOf(hourButton("heat.earlier", "Earlier", -1), playButton(), hourButton("heat.later", "Later", 1)))
    val hourLine = textNode("heat.hourlabel", "Hour of the day, 0 to ${HOURS_IN_DAY - 1}", variant = "caption", color = MUTED)
    val children = listOf(title, dayLine) + nowNodes(season, steps, day) + listOf(volume, hourLine, slider, stepButtons, chartNode(steps, day, stocks)) + fanNodes(screen, steps, day)
    return JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"), "state" to jsonOf(mapOf(HOUR_PATH to coldestHour, PLAYING_PATH to false)),
            "root" to specNode("root", "column", mapOf("gap" to 12, "padding" to 16, "background" to HUD), children),
        ),
    )
}

private const val PLAYING_PATH = "heat.playing"
private const val PLAY_INTERVAL_MILLIS = 400

/** One button that plays the hours round the day and, tapped again, stops where it is; it stays on screen while it plays, since the loop runs in its tap. */
private fun playButton(): JsonObject =
    JsonObject(
        specNode("heat.play", "button", mapOf("label" to "Play", "flex" to 1)) +
            ("action" to jsonOf(mapOf("action" to "animate_binding", "params" to mapOf("path" to HOUR_PATH, "from" to 0, "to" to HOURS_IN_DAY - 1, "step" to 1, "interval_ms" to PLAY_INTERVAL_MILLIS, "loop" to true, "playing" to PLAYING_PATH)))),
    )

/** One line per hour, shown only while the hour binding names it: the clock time and the outside air then, so the colour has a reason. */
private fun nowNodes(season: JsonObject, steps: List<JsonObject>, day: Int): List<JsonObject> {
    val clock = clockOf(season)
    return (0 until HOURS_IN_DAY).map { hour ->
        val step = day * HOURS_IN_DAY + hour
        val line = "${stepLabel(clock, step.toDouble())}: outside air ${tidyDegrees(outsideAir(steps[step]))} F"
        JsonObject(textNode("heat.now.$hour", line, variant = "subheading", bold = true, color = INK) + ("visible" to JsonPrimitive("{$HOUR_PATH}=$hour")))
    }
}

private fun hourButton(id: String, label: String, add: Int): JsonObject =
    JsonObject(specNode(id, "button", mapOf("label" to label, "variant" to "outlined", "flex" to 1)) + ("action" to jsonOf(mapOf("action" to "set_binding", "params" to mapOf("path" to HOUR_PATH, "add" to add, "min" to 0, "max" to HOURS_IN_DAY - 1)))))

private const val VOLUME_HEIGHT_DP = 360
private const val CLEAR_OPACITY = 0.1
private const val CLEAR_PREFIX = "clear: "
private const val AIR_PREFIX = "air: "
private const val AIR_OPACITY = 0.14

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
