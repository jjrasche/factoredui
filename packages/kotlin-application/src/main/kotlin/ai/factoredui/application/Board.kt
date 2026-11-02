package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

class BoardFormatException(message: String) : IllegalArgumentException(message)

data class FlowCard(
    val name: String,
    val runs: Int,
    val firstRunAt: Double?,
    val lastRunAt: Double?,
    val people: List<String>,
    val mentors: List<String>,
    val learners: List<String>,
    val places: List<String>,
    val blockedBy: String?,
    val needs: Map<String, Double>,
)

data class Board(val unit: String, val endedAs: String, val cards: List<FlowCard>)

data class Boarded(val board: Board?, val failure: String? = null)

internal fun JsonElement?.objectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonElement?.listOrEmpty(): List<JsonElement> = (this as? JsonArray).orEmpty()

internal fun JsonElement?.textsOrEmpty(): List<String> = listOrEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { text -> text.isString }?.content }

internal fun JsonElement?.numberOrNull(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull

internal class Step(val t: Double, val assignments: List<JsonObject>, val flows: JsonObject)

internal fun stepsOf(root: JsonObject): List<Step> = root["steps"].listOrEmpty().mapNotNull { element ->
    val step = element as? JsonObject ?: return@mapNotNull null
    val t = step["t"].numberOrNull() ?: return@mapNotNull null
    Step(t, step["assignments"].listOrEmpty().mapNotNull { it as? JsonObject }, step["flows"].objectOrEmpty())
}

private fun ranAt(steps: List<Step>, flow: String): List<Double> = steps.filter { it.flows[flow].objectOrEmpty()["ran"] == JsonPrimitive(true) }.map { it.t }

private fun assignedTo(steps: List<Step>, flow: String, key: String): List<String> =
    steps.flatMap { step -> step.assignments.filter { (it["flow"] as? JsonPrimitive)?.content == flow }.flatMap { it[key].textsOrEmpty() } }.distinct()

private fun placedBy(steps: List<Step>, flow: String): List<String> = steps.flatMap { it.flows[flow].objectOrEmpty()["places"].textsOrEmpty() }.distinct()

private fun lastBlocker(steps: List<Step>, flow: String): String? =
    steps.lastOrNull { (it.flows[flow].objectOrEmpty()["blocked_by"] as? JsonPrimitive)?.takeIf { blocker -> blocker !is JsonNull } != null }
        ?.let { (it.flows[flow].objectOrEmpty()["blocked_by"] as JsonPrimitive).content }

private fun needsOf(root: JsonObject, flow: String): Map<String, Double> =
    root["needs"].objectOrEmpty()[flow].objectOrEmpty().mapNotNull { (key, value) -> value.numberOrNull()?.let { key to it } }.toMap()

private fun flowNames(root: JsonObject, steps: List<Step>): List<String> =
    (steps.flatMap { it.flows.keys } + root["totals"].objectOrEmpty().keys).distinct()

private fun cardOf(root: JsonObject, steps: List<Step>, flow: String): FlowCard {
    val ran = ranAt(steps, flow)
    return FlowCard(
        flow, ran.size, ran.firstOrNull(), ran.lastOrNull(), assignedTo(steps, flow, "people"), assignedTo(steps, flow, "mentors"),
        assignedTo(steps, flow, "learners"), placedBy(steps, flow), lastBlocker(steps, flow), needsOf(root, flow),
    )
}

fun readBoard(text: String): Board {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw BoardFormatException("the simulation is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw BoardFormatException((it["message"] as? JsonPrimitive)?.content ?: "the simulation was refused") }
    val steps = stepsOf(root)
    if (steps.isEmpty() && root["steps"] == null) throw BoardFormatException("the simulation has no steps")
    val cards = flowNames(root, steps).map { cardOf(root, steps, it) }
    val unit = (root["clock"].objectOrEmpty()["unit"] as? JsonPrimitive)?.content ?: "step"
    return Board(unit, (root["ended"] as? JsonPrimitive)?.content ?: "unknown", cards)
}
