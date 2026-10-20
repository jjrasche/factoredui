package ai.factoredui.application

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()

private fun JsonElement?.words(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/** A figure as the engine gave it: a number, text, or {"unknown": why}. */
private fun figureText(element: JsonElement?): String = when {
    element == null || element is JsonNull -> "not given"
    element is JsonObject && element["unknown"] != null -> "unknown (${element["unknown"].words()})"
    element is JsonArray -> element.joinToString(", ") { figureText(it) }
    element is JsonPrimitive -> element.doubleOrNull?.takeIf { !element.isString }?.let { formatFigure(it) } ?: element.content
    else -> element.toString()
}

private fun settingLine(system: String, setting: JsonObject): String {
    val unit = setting["unit"].words()?.let { " $it" }.orEmpty()
    return "  $system.${setting["id"].words()} = ${figureText(setting["value"])}$unit; ${setting["reason"].words()?.let { "reason: $it" } ?: "no reason given"}"
}

private fun systemsGroup(describe: JsonObject): NoteGroup = NoteGroup(
    "SYSTEMS, EACH WITH ITS CHOSEN SOLUTION AND SETTINGS",
    describe["systems"].list().flatMap { entry ->
        val system = entry.obj()
        val id = system["id"].words().orEmpty()
        listOf("$id: ${system["solution_title"].words() ?: system["solution"].words()} (solution ${system["solution"].words()})") + system["settings"].list().map { settingLine(id, it.obj()) }
    },
)

private fun sourcesLine(sources: List<JsonElement>): String? {
    val distinct = sources.map { it.obj() }.mapNotNull { source -> source["source"].words()?.let { "$it (${source["evidence"].words() ?: "no evidence given"})" } }.distinct()
    return distinct.takeIf { it.isNotEmpty() }?.let { "  sources: ${it.joinToString("; ")}" }
}

private fun solversGroup(solving: JsonObject): NoteGroup = NoteGroup(
    "SOLVERS, EACH ANSWER WITH WHAT IT RESTS ON",
    solving["solvers"].list().flatMap { entry ->
        val solver = entry.obj()
        val head = "${solver["function"].words()} (${solver["domain"].words()}): ${solver["status"].words()}"
        listOfNotNull(head, solver["detail"].words()?.takeIf { it.isNotEmpty() }?.let { "  ${it}" }) +
            solver["values"].obj().map { (name, value) -> "  $name = ${figureText(value)}" } + listOfNotNull(sourcesLine(solver["sources"].list()))
    },
)

private fun rulesGroup(evaluation: JsonObject): NoteGroup = NoteGroup(
    "RULES, EACH WITH ITS STATUS",
    evaluation["rules"].list().map { entry ->
        val rule = entry.obj()
        "${rule["id"].words()} [${rule["system"].words()}]: ${rule["status"].words()?.uppercase()}: ${rule["detail"].words()}; source: ${rule["source"].words()}"
    },
)

private fun solverNamed(solving: JsonObject, function: String): JsonObject? = solving["solvers"].list().map { it.obj() }.firstOrNull { it["function"].words() == function }

private fun gravityGroup(solving: JsonObject): NoteGroup {
    val solver = solverNamed(solving, "gravity-load-path") ?: return NoteGroup("GRAVITY LOAD PATH, PER POST", listOf("the solver did not run"))
    val posts = solver["objects"].obj().filterKeys { it.startsWith("post@") }
    val lines = listOf("status: ${solver["status"].words()}; ${solver["detail"].words().orEmpty()}") +
        posts.map { (id, figures) -> "  $id: ground reaction ${figureText(figures.obj()["reaction_n"])}${if (figures.obj()["reaction_n"] is JsonObject) "" else " N"}" }
    return NoteGroup("GRAVITY LOAD PATH, PER POST", lines)
}

private fun headersGroup(solving: JsonObject): NoteGroup {
    val solver = solverNamed(solving, "header-check") ?: return NoteGroup("HEADER UTILISATIONS", listOf("the solver did not run"))
    val headers = solver["objects"].obj().map { (id, figures) ->
        val f = figures.obj()
        "  $id: utilisation ${figureText(f["utilisation"])}; bending ${figureText(f["bending_stress_mpa"])} MPa, shear ${figureText(f["shear_stress_mpa"])} MPa, deflection ${figureText(f["deflection_mm"])} mm; load ${figureText(f["load_n_per_mm"])} N/mm against capacity ${figureText(f["capacity_n_per_mm"])} N/mm"
    }
    return NoteGroup("HEADER UTILISATIONS", listOf("status: ${solver["status"].words()}; largest ${figureText(solver["values"].obj()["utilisation_max"])}") + headers)
}

private fun simulationGroup(simulation: JsonObject?): NoteGroup {
    if (simulation == null) return NoteGroup("BUILD SIMULATION TOTALS", listOf("no simulation: the config has no clock"))
    val clock = simulation["clock"].obj()
    val flows = simulation["totals"].obj().map { (name, totals) ->
        "  $name: ran ${figureText(totals.obj()["runs"])} times; moved ${totals.obj()["moved"].obj().entries.joinToString(", ") { (stock, value) -> "$stock ${figureText(value)}" }}"
    }
    return NoteGroup(
        "BUILD SIMULATION TOTALS",
        listOf("ended ${simulation["ended"].words()}; clock step ${figureText(clock["step"])} ${clock["unit"].words()} until ${figureText(clock["until"])}") +
            simulation["objective_total"].obj().map { (name, value) -> "  $name = ${figureText(value)}" } +
            listOf("final stocks:") + simulation["final_stocks"].obj().map { (name, value) -> "  $name = ${figureText(value)}" } + listOf("flows:") + flows,
    )
}

private fun assumptionsGroup(describe: JsonObject): NoteGroup = NoteGroup(
    "ASSUMPTIONS, EVERY SETTING WHOSE REASON SAYS IT IS ASSUMED",
    describe["systems"].list().flatMap { entry ->
        val system = entry.obj()
        system["settings"].list().map { it.obj() }.filter { it["reason"].words()?.contains("assum", ignoreCase = true) == true }.map { settingLine(system["id"].words().orEmpty(), it) }
    }.ifEmpty { listOf("none") },
)

private fun unmeasuredGroup(geometry: Geometry): NoteGroup =
    NoteGroup("UNMEASURED PIECES", geometry.unmeasured.map { "${it.id}: ${it.field} is unknown (${it.why})" }.ifEmpty { listOf("none") })

/** The groups of the design summary, read from one load. */
internal fun summaryGroups(loaded: Loaded, geometry: Geometry): List<NoteGroup> {
    val describe = loaded.objectOf("describe")
    val solving = loaded.objectOf("solving")
    val simulation = if (loaded.isSkipped("simulation")) null else loaded.objectOf("simulation")
    return listOf(
        systemsGroup(describe), solversGroup(solving), rulesGroup(loaded.objectOf("evaluation")), gravityGroup(solving), headersGroup(solving), simulationGroup(simulation),
        assumptionsGroup(describe), unmeasuredGroup(geometry),
    )
}
