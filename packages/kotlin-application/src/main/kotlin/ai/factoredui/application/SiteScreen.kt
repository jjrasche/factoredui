package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

class SiteFormatException(message: String) : IllegalArgumentException(message)

data class SiteMember(val label: String, val world: String, val atMm: List<Double>, val repeatCount: Int, val repeatStepMm: Double)

data class FocusGroup(val label: String, val systems: Set<String>, val member: String?)

data class SiteScreen(val title: String, val world: String, val members: List<SiteMember>, val styles: Map<String, JsonObject>, val assumed: List<String>, val groups: List<FocusGroup> = emptyList())

private const val MM_PER_INCH = 25.4
private const val INCHES_PER_FOOT = 12

private fun textIn(root: JsonObject, key: String, where: String): String =
    (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw SiteFormatException("$where has no '$key' text")

private fun numberIn(root: JsonObject, key: String, where: String): Double? =
    (root[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()?.also { if (!it.isFinite()) throw SiteFormatException("$where has a '$key' that is not finite") }

private fun offsetOf(member: JsonObject, where: String): List<Double> {
    val listed = member["at_mm"] as? JsonArray ?: return listOf(0.0, 0.0, 0.0)
    val numbers = listed.map { (it as? JsonPrimitive)?.takeIf { value -> !value.isString }?.content?.toDoubleOrNull() }
    if (numbers.size != 3 || numbers.any { it == null }) throw SiteFormatException("$where has an 'at_mm' that is not three numbers")
    return numbers.map { it!! }
}

private fun memberOf(entry: JsonObject, index: Int): SiteMember {
    val where = "member $index"
    val repeat = entry["repeat"] as? JsonObject
    val count = repeat?.let { numberIn(it, "count", "$where's repeat")?.toInt() } ?: 1
    val step = repeat?.let { numberIn(it, "step_mm", "$where's repeat") } ?: 0.0
    if (count < 1) throw SiteFormatException("$where repeats fewer than once")
    if (count > 1 && step <= 0.0) throw SiteFormatException("$where repeats $count times with no 'step_mm' above zero")
    return SiteMember(textIn(entry, "label", where), textIn(entry, "world", where), offsetOf(entry, where), count, step)
}

private fun stylesOf(root: JsonObject): Map<String, JsonObject> =
    (root["styles"] as? JsonObject).orEmpty().mapValues { (name, style) -> style as? JsonObject ?: throw SiteFormatException("the style '$name' is not an object") }

private fun groupsOf(root: JsonObject): List<FocusGroup> = (root["focus_groups"] as? JsonArray).orEmpty().map { entry ->
    val group = entry as? JsonObject ?: throw SiteFormatException("a focus group is not an object")
    FocusGroup(textIn(group, "label", "a focus group"), (group["systems"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }.toSet(), (group["member"] as? JsonPrimitive)?.content)
}

private fun assumedOf(root: JsonObject): List<String> =
    (root["assumed"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.takeIf { line -> line.isString }?.content ?: throw SiteFormatException("an 'assumed' line is not text") }

/** The site a world gives: which worlds stand together in one drawing, where each sits, and how each material looks. */
fun readSiteScreen(text: String): SiteScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw SiteFormatException("the site is not a JSON object")
    val members = (root["members"] as? JsonArray ?: throw SiteFormatException("the site has no 'members' list")).mapIndexed { index, entry ->
        memberOf(entry as? JsonObject ?: throw SiteFormatException("member $index is not an object"), index)
    }
    if (members.isEmpty()) throw SiteFormatException("the site has an empty 'members' list")
    return SiteScreen((root["title"] as? JsonPrimitive)?.content ?: "", textIn(root, "world", "the site"), members, stylesOf(root), assumedOf(root), groupsOf(root))
}

internal fun feetAndInches(mm: Double): String {
    val totalInches = (mm / MM_PER_INCH).roundToInt()
    val feet = totalInches / INCHES_PER_FOOT
    val inches = totalInches % INCHES_PER_FOOT
    return when {
        feet == 0 -> "$inches in"
        inches == 0 -> "$feet ft"
        else -> "$feet ft $inches in"
    }
}

private fun number(box: Map<String, Any?>, key: String) = (box[key] as Number).toDouble()

private fun shifted(box: Map<String, Any?>, id: String, along: Double, offset: List<Double>): Map<String, Any?> =
    box + mapOf("id" to id, "x_mm" to number(box, "x_mm") + offset[0] + along, "y_mm" to number(box, "y_mm") + offset[1], "z_mm" to number(box, "z_mm") + offset[2])

private fun idInSite(member: SiteMember, boxId: String, copy: Int): String =
    if (member.repeatCount > 1) "${member.label}/$boxId~${copy + 1}" else "${member.label}/$boxId"

private fun systemOf(geometry: Geometry, id: String): String = geometry.pieces.firstOrNull { it.id == id }?.system.orEmpty()

private fun stockOf(geometry: Geometry, id: String): String? = geometry.pieces.firstOrNull { it.id == id }?.stock

private fun boxesOfMember(member: SiteMember, geometry: Geometry): List<Map<String, Any?>> {
    val whole = withCutsOpened(measuredBoxes(geometry), geometry)
    return (0 until member.repeatCount).flatMap { copy ->
        whole.map { box -> shifted(box, idInSite(member, box["id"] as String, copy), copy * member.repeatStepMm, member.atMm) + mapOf("system" to systemOf(geometry, box["id"] as String), "stock" to stockOf(geometry, box["id"] as String)) }
    }
}

private fun sizeLine(box: Map<String, Any?>): String =
    listOf("width_mm", "depth_mm", "height_mm").joinToString(" × ") { feetAndInches(number(box, it)) }

private fun detailOf(member: SiteMember, box: Map<String, Any?>): Map<String, Any?> = mapOf(
    "title" to "${member.label}: ${(box["id"] as String).substringAfter('/').substringBefore('@')}",
    "sections" to listOf(mapOf("heading" to "", "rows" to listOf(mapOf("label" to "Material", "value" to box["style"]), mapOf("label" to "Size", "value" to sizeLine(box))))),
)

private fun styleOf(site: SiteScreen, palette: List<String>, name: String): Map<String, Any?> =
    mapOf("stroke" to colourOfSystem(palette, name)) + (site.styles[name]?.let { override -> override.mapValues { it.value } } ?: emptyMap())

private fun siteVolume(site: SiteScreen, drawn: List<Pair<SiteMember, List<Map<String, Any?>>>>): JsonObject {
    val boxes = drawn.flatMap { it.second }
    val names = boxes.map { it["style"] as String }.distinct()
    val details = drawn.flatMap { (member, memberBoxes) -> memberBoxes.map { (it["id"] as String) to detailOf(member, it) } }.toMap()
    return specNode(
        "volume", "volume",
        mapOf(
            "boxes" to boxes, "grade_mm" to GRADE_MM, "styles" to names.associateWith { styleOf(site, names, it) },
            "legend" to names.map { mapOf("label" to it, "style" to it) },
            "empty_text" to "The site has nothing to draw.", "selected" to SELECTION_PATH, "details" to details, "focus" to "{$FOCUS_PATH}",
        ),
    )
}

private const val FOCUS_PATH = "site.focus"
private const val SELECTION_CARD_PATH = "selection.card"
private const val SITE_VOLUME_HEIGHT_DP = 440
private const val SITE_GROUND = "#2b3c50"

/** The site's volume as a block in the page's column: sunlit, on its ground, with the picked box's card under it. */
private fun siteWorldNode(volume: JsonObject): JsonObject {
    val props = (volume["props"] as JsonObject).toMutableMap()
    props["legend"] = JsonArray(emptyList())
    props["ground"] = JsonPrimitive(SITE_GROUND)
    props["lighting"] = JsonPrimitive("sun")
    props["detail_text"] = JsonPrimitive("{$SELECTION_CARD_PATH}")
    props["height"] = JsonPrimitive(SITE_VOLUME_HEIGHT_DP)
    return JsonObject(volume + ("props" to JsonObject(props)))
}

private fun idsOfGroup(group: FocusGroup, drawn: List<Pair<SiteMember, List<Map<String, Any?>>>>): List<String> =
    drawn.filter { (member, _) -> group.member == null || member.label == group.member }
        .flatMap { (_, boxes) -> boxes.filter { group.systems.isEmpty() || it["system"] in group.systems }.map { it["id"] as String } }

private fun focusButton(label: String, ids: List<String>): JsonObject =
    JsonObject(specNode("site.focus.$label", "button", mapOf("label" to label, "variant" to "outlined")) + ("action" to jsonOf(mapOf("action" to "set_binding", "params" to mapOf("path" to FOCUS_PATH, "value" to ids)))))

private fun focusNodes(site: SiteScreen, drawn: List<Pair<SiteMember, List<Map<String, Any?>>>>): List<JsonObject> {
    if (site.groups.isEmpty()) return emptyList()
    val everything = drawn.flatMap { (_, boxes) -> boxes.map { it["id"] as String } }
    val buttons = listOf(focusButton("Everything", everything)) + site.groups.map { focusButton(it.label, idsOfGroup(it, drawn)) }
    return listOf(specNode("site.focus", "scrollview", mapOf("horizontal" to true), listOf(specNode("site.focus.row", "row", mapOf("gap" to 8), buttons))))
}

private val SETTING_PLACEHOLDER = Regex("""\{([a-z0-9_]+)\.([a-z0-9_]+)\}""")

private fun settingMm(world: JsonObject?, system: String, setting: String): Double? =
    (((world?.get("systems") as? JsonObject)?.get(system) as? JsonObject)?.get("settings") as? JsonObject)?.get(setting)
        ?.let { (it as? JsonPrimitive)?.takeIf { value -> !value.isString }?.content?.toDoubleOrNull() }

/** A note's {system.setting} words are the world's own value of that setting, in feet and inches, so a changed setting cannot leave old words behind. */
private fun resolvedNote(line: String, world: JsonObject?): String =
    SETTING_PLACEHOLDER.replace(line) { match ->
        val millimetres = settingMm(world, match.groupValues[1], match.groupValues[2])
            ?: throw SiteFormatException("the note '$line' names ${match.groupValues[1]}.${match.groupValues[2]}, which the site's world does not set")
        feetAndInches(millimetres)
    }

private fun siteWorldFile(site: SiteScreen, worlds: List<EngineWorld>): JsonObject? =
    worlds.firstOrNull { it.id == site.world }?.takeIf { site.assumed.any(SETTING_PLACEHOLDER::containsMatchIn) }
        ?.let { runCatching { Json.parseToJsonElement(java.nio.file.Files.readString(it.path)) as? JsonObject }.getOrNull() }

private fun assumedNodes(lines: List<String>): List<JsonObject> =
    if (lines.isEmpty()) emptyList()
    else listOf(textNode("site.assumed.title", "Assumed, not set by Jim", variant = "subheading", bold = true)) +
        lines.mapIndexed { index, line -> textNode("site.assumed.$index", line, variant = "caption", color = MUTED) }

private fun memberCountNodes(drawn: List<Pair<SiteMember, List<Map<String, Any?>>>>): List<JsonObject> =
    drawn.map { (member, boxes) -> textNode("site.member.${member.label}", "${member.label}: ${boxes.size} ${if (boxes.size == 1) "piece" else "pieces"}", variant = "caption", color = MUTED) }

/** One solid drawing of every member of [site], each member's geometry moved to its place and repeated along east-west as the site says, with what the site assumes written under it. */
internal fun siteDrawn(engine: EngineRuntime, worlds: List<EngineWorld>, site: SiteScreen, timings: Timings): List<Pair<SiteMember, List<Map<String, Any?>>>> = site.members.map { member ->
    val world = worlds.firstOrNull { it.id == member.world } ?: throw SiteFormatException("the engine serves no world '${member.world}' for '${member.label}'")
    val geometry = timings.timed("geometry") { readGeometry(geometrySystem(engine, world.path)) }
    member to boxesOfMember(member, geometry)
}

/** Every box of the site as the volume draws it, with its system and the stock that gives its temperature. */
internal fun siteBoxesOf(engine: EngineRuntime, worlds: List<EngineWorld>, site: SiteScreen, timings: Timings): List<Map<String, Any?>> = siteDrawn(engine, worlds, site, timings).flatMap { it.second }

internal fun siteSpec(engine: EngineRuntime, worlds: List<EngineWorld>, site: SiteScreen, timings: Timings): JsonObject {
    val drawn = siteDrawn(engine, worlds, site, timings)
    val title = textNode("site.title", site.title, variant = "subheading", bold = true)
    val world = siteWorldFile(site, worlds)
    val everything = drawn.flatMap { (_, boxes) -> boxes.map { it["id"] as String } }
    val world3d = siteWorldNode(siteVolume(site, drawn))
    val tapped = JsonObject(textNode("site.tapped", "{$SELECTION_CARD_PATH}", color = INK) + ("visible" to JsonPrimitive("{$SELECTION_CARD_PATH}")))
    val children = listOf(title) + focusNodes(site, drawn) + listOf(world3d, tapped) + memberCountNodes(drawn) + assumedNodes(site.assumed.map { resolvedNote(it, world) })
    return JsonObject(
        mapOf(
            "spec_version" to JsonPrimitive(1), "renderer_min" to JsonPrimitive(1), "theme" to JsonPrimitive("dark"), "state" to jsonOf(mapOf(FOCUS_PATH to everything)),
            "root" to specNode("root", "column", mapOf("gap" to 12, "padding" to 16, "background" to HUD), children),
        ),
    )
}
