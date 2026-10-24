package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

class SiteFormatException(message: String) : IllegalArgumentException(message)

data class SiteMember(val label: String, val world: String, val atMm: List<Double>, val repeatCount: Int, val repeatStepMm: Double)

data class SiteScreen(val title: String, val world: String, val members: List<SiteMember>, val styles: Map<String, JsonObject>, val assumed: List<String>)

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

private fun assumedOf(root: JsonObject): List<String> =
    (root["assumed"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.takeIf { line -> line.isString }?.content ?: throw SiteFormatException("an 'assumed' line is not text") }

/** The site a world gives: which worlds stand together in one drawing, where each sits, and how each material looks. */
fun readSiteScreen(text: String): SiteScreen {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw SiteFormatException("the site is not a JSON object")
    val members = (root["members"] as? JsonArray ?: throw SiteFormatException("the site has no 'members' list")).mapIndexed { index, entry ->
        memberOf(entry as? JsonObject ?: throw SiteFormatException("member $index is not an object"), index)
    }
    if (members.isEmpty()) throw SiteFormatException("the site has an empty 'members' list")
    return SiteScreen((root["title"] as? JsonPrimitive)?.content ?: "", textIn(root, "world", "the site"), members, stylesOf(root), assumedOf(root))
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

private fun boxesOfMember(member: SiteMember, geometry: Geometry): List<Map<String, Any?>> {
    val whole = withCutsOpened(measuredBoxes(geometry), geometry)
    return (0 until member.repeatCount).flatMap { copy ->
        whole.map { box -> shifted(box, idInSite(member, box["id"] as String, copy), copy * member.repeatStepMm, member.atMm) }
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
            "empty_text" to "The site has nothing to draw.", "selected" to SELECTION_PATH, "details" to details,
        ),
    )
}

private fun assumedNodes(site: SiteScreen): List<JsonObject> =
    if (site.assumed.isEmpty()) emptyList()
    else listOf(textNode("site.assumed.title", "Assumed, not set by Jim", variant = "subheading", bold = true)) +
        site.assumed.mapIndexed { index, line -> textNode("site.assumed.$index", line, variant = "caption", color = MUTED) }

/** One solid drawing of every member of [site], each member's geometry moved to its place and repeated along east-west as the site says, with what the site assumes written under it. */
internal fun siteSpec(engine: EngineRuntime, worlds: List<EngineWorld>, site: SiteScreen, timings: Timings): JsonObject {
    val drawn = site.members.map { member ->
        val world = worlds.firstOrNull { it.id == member.world } ?: throw SiteFormatException("the engine serves no world '${member.world}' for '${member.label}'")
        val geometry = timings.timed("geometry") { readGeometry(geometrySystem(engine, world.path)) }
        member to boxesOfMember(member, geometry)
    }
    val title = textNode("site.title", site.title, variant = "subheading", bold = true)
    return dockedPage(listOf(shadedWorld(siteVolume(site, drawn))), false, listOf(title) + assumedNodes(site))
}
