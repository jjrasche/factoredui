package ai.factoredui.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class BuildFormatException(message: String) : IllegalArgumentException(message)

data class Act(val flow: String, val people: List<String>, val learners: List<String>, val places: List<String>, val moves: Map<String, Double>)

data class Keyframe(val t: Double, val acts: List<Act>, val stocks: Map<String, Double>, val objective: Map<String, Double>)

data class Build(val unit: String, val keyframes: List<Keyframe>, val stageOf: Map<String, Int>)

data class Built(val build: Build?, val failure: String? = null)

private fun numbersOf(element: kotlinx.serialization.json.JsonElement?): Map<String, Double> =
    element.objectOrEmpty().mapNotNull { (key, value) -> value.numberOrNull()?.let { key to it } }.toMap()

private fun crewOf(steps: List<Step>, flow: String, upTo: Double, key: String): List<String> =
    steps.filter { it.t <= upTo }.flatMap { step -> step.assignments.filter { (it["flow"] as? JsonPrimitive)?.content == flow } }.lastOrNull()?.get(key).textsOrEmpty()

private fun actsOf(steps: List<Step>, step: Step): List<Act> =
    step.flows.filter { (_, flow) -> flow.objectOrEmpty()["ran"] == JsonPrimitive(true) }.map { (name, flow) ->
        val fields = flow.objectOrEmpty()
        Act(name, crewOf(steps, name, step.t, "people"), crewOf(steps, name, step.t, "learners"), fields["places"].textsOrEmpty(), numbersOf(fields["moves"]))
    }

private fun keyframeOf(steps: List<Step>, step: Step, source: JsonObject?): Keyframe =
    Keyframe(step.t, actsOf(steps, step), numbersOf(source?.get("stocks")), numbersOf(source?.get("objective")))

private fun stepSources(root: JsonObject): Map<Double, JsonObject> =
    root["steps"].listOrEmpty().mapNotNull { it as? JsonObject }.mapNotNull { source -> source["t"].numberOrNull()?.let { it to source } }.toMap()

private fun stagesOf(root: JsonObject, keyframes: List<Keyframe>): Map<String, Int> =
    numbersOf(root["placed_at"]).mapNotNull { (piece, t) -> keyframes.indexOfFirst { it.t >= t }.takeIf { it >= 0 }?.let { piece to it } }.toMap()

/** The simulation as key moments: its first step, then each step at which a flow ran, each with who acted, what was placed, and the stocks and totals then. */
fun readBuild(text: String): Build {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw BuildFormatException("the simulation is not a JSON object")
    (root["refused"] as? JsonObject)?.let { throw BuildFormatException((it["message"] as? JsonPrimitive)?.content ?: "the simulation was refused") }
    val steps = stepsOf(root)
    if (steps.isEmpty()) throw BuildFormatException("the simulation has no steps")
    val sources = stepSources(root)
    val moments = (listOf(steps.first()) + steps.filter { step -> step.flows.values.any { it.objectOrEmpty()["ran"] == JsonPrimitive(true) } }).distinctBy { it.t }
    val keyframes = moments.map { keyframeOf(steps, it, sources[it.t]) }
    return Build((root["clock"].objectOrEmpty()["unit"] as? JsonPrimitive)?.content ?: "step", keyframes, stagesOf(root, keyframes))
}
