package ai.factoredui.worldengine.script

import ai.factoredui.worldengine.json.isTruthy
import ai.factoredui.worldengine.json.optionalText
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.log.LogResult
import ai.factoredui.worldengine.outputs.WorldOutputs
import ai.factoredui.worldengine.text.formatGeneral
import ai.factoredui.worldengine.text.pythonJsonDumps
import ai.factoredui.worldengine.world.World
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

data class ScriptRun(val log: EventLog, val lines: List<String>, val results: List<LogResult>)

fun scriptTimestamp(index: Int): String = "2026-10-05T00:${(index / 60).toString().padStart(2, '0')}:${(index % 60).toString().padStart(2, '0')}Z"

fun runScript(world: World, steps: JsonArray, log: EventLog = EventLog(world)): ScriptRun {
    val lines = mutableListOf<String>()
    val results = mutableListOf<LogResult>()
    steps.forEachIndexed { index, element ->
        val step = element as JsonObject
        val result = runStep(log, step, step.optionalText("timestamp") ?: scriptTimestamp(index))
        results += result
        lines += describeStep(step, result)
    }
    return ScriptRun(log, lines, results)
}

fun runStep(log: EventLog, step: JsonObject, timestamp: String): LogResult {
    val actor = step.optionalText("actor") ?: "jim"
    return when (val verb = step.requiredText("do")) {
        "branch" -> log.branch(step.requiredText("name"), branchSource(step), actor, timestamp, isTruthy(step["proposal"]))
        "merge" -> log.merge(step.requiredText("branch"), step.optionalText("into") ?: "main", actor, timestamp)
        "revert" -> log.revert(step.requiredText("event"), step.optionalText("on") ?: "main", actor, timestamp)
        else -> log.attempt(step.optionalText("on") ?: "main", actor, verb, stepParameters(step), timestamp)
    }
}

private fun branchSource(step: JsonObject): String? = if (step["from"] is JsonNull) null else step.optionalText("from") ?: "main"

private fun stepParameters(step: JsonObject): JsonObject = step["parameters"] as? JsonObject ?: JsonObject(emptyMap())

fun describeStep(step: JsonObject, result: LogResult): String {
    val verb = step.requiredText("do")
    return when (result) {
        is LogResult.Refused -> "refused $verb ${pythonJsonDumps(stepParameters(step), sortKeys = true)} by ${result.refusal.rule}: ${result.refusal.message}"
        is LogResult.Committed -> "applied ${result.event.id} $verb on ${result.event.branch}"
    }
}

fun formatOutputs(world: World, outputs: WorldOutputs): List<String> {
    val lines = mutableListOf<String>()
    outputs.counts.forEach { (typeId, count) -> lines += "count $typeId $count tiles ${formatGeneral(outputs.areas.getValue(typeId), 6)} sq_ft" }
    outputs.equations.forEach { (id, value) -> lines += "equation $id ${formatGeneral(value, 6)} ${world.equations.getValue(id).unit}" }
    outputs.stocks.forEach { (id, value) -> lines += "stock $id ${formatGeneral(value, 6)} ${world.stocks.getValue(id).unit}" }
    outputs.scoring.forEach { (id, value) -> lines += "score $id ${formatGeneral(value, 6)} ${world.scoring.getValue(id).unit}" }
    lines += "ticks ${outputs.ticks}"
    return lines
}
