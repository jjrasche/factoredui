package ai.factoredui.worldengine

import ai.factoredui.worldengine.events.Refusal
import ai.factoredui.worldengine.events.RefusalException
import ai.factoredui.worldengine.expression.Evaluation
import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.expression.Scope
import ai.factoredui.worldengine.expression.ScopeSite
import ai.factoredui.worldengine.expression.Value
import ai.factoredui.worldengine.expression.ValueType
import ai.factoredui.worldengine.expression.checkExpression
import ai.factoredui.worldengine.expression.parseExpression
import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.isTruthy
import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.log.LogResult
import ai.factoredui.worldengine.outputs.TapDecision
import ai.factoredui.worldengine.outputs.WorldOutputs
import ai.factoredui.worldengine.outputs.actionForTap
import ai.factoredui.worldengine.outputs.renderProps
import ai.factoredui.worldengine.outputs.reportOutputs
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.script.scriptTimestamp
import ai.factoredui.worldengine.text.pythonStr
import ai.factoredui.worldengine.units.BaseDimension
import ai.factoredui.worldengine.validate.WorldValidator
import ai.factoredui.worldengine.validate.applyDocumentEdit
import ai.factoredui.worldengine.validate.applyMutation
import ai.factoredui.worldengine.world.MapWorldLibrary
import ai.factoredui.worldengine.world.World
import ai.factoredui.worldengine.world.WorldLoadException
import ai.factoredui.worldengine.world.WorldLoader
import ai.factoredui.worldengine.world.findCycle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.math.abs
import kotlin.math.pow

const val FROZEN_SUFFIX = ".frozen.world.json"
private const val RELATIVE_TOLERANCE = 1e-9
private const val ABSOLUTE_TOLERANCE = 1e-12
private val CASE_FIELDS = listOf("id", "kind", "source", "world", "input", "expect", "notes")
private val KINDS = listOf("expression", "world_load", "action_sequence", "replay", "branching", "clock", "agents", "report")
private val STATED_SECTIONS = listOf("render", "outputs", "expressions")
private val SOURCES = listOf("hand", "generated")
private val OUTPUT_SECTIONS = listOf("counts", "areas", "equations", "stocks", "scoring")
private val UNIT_PART = Regex("([*/]?)\\s*([a-z_]+)(?:\\^(\\d+))?")

private typealias Observation = Map<String, Any?>

class ConformanceRunner(private val locations: ReferenceLocations = ReferenceLocations) {
    private val unitTable: JsonObject by lazy { Json.parseToJsonElement(Files.readString(locations.unitTable)).jsonObject }

    fun readCases(): List<Pair<String, JsonObject>> {
        val directory = locations.conformanceCasesDir
        if (!Files.isDirectory(directory)) return emptyList()
        return directory.listDirectoryEntries("*.json").sortedBy { it.name }.map { it.name to Json.parseToJsonElement(Files.readString(it)).jsonObject }
    }

    fun findFrozenWorldChanges(): List<String> {
        val sumsFile = locations.frozenWorldsDir.resolve("SHA256SUMS")
        if (!Files.exists(sumsFile)) return listOf("$sumsFile is missing, so nothing proves the frozen worlds are the ones the cases were written against")
        val sums = readFrozenSums()
        val present = locations.frozenWorldsDir.listDirectoryEntries("*$FROZEN_SUFFIX").map { it.name }.toSet()
        val unlisted = (present - sums.keys).sorted().map { "$it is not listed in SHA256SUMS" }
        val missing = (sums.keys - present).sorted().map { "$it is listed in SHA256SUMS but missing" }
        val changed = sums.entries.sortedBy { it.key }.filter { it.key in present && hashFrozenWorld(locations.frozenWorldsDir.resolve(it.key)) != it.value }
            .map { "${it.key} changed: sha256 ${hashFrozenWorld(locations.frozenWorldsDir.resolve(it.key))}, frozen as ${it.value}" }
        return unlisted + missing + changed
    }

    private fun readFrozenSums(): Map<String, String> =
        locations.frozenWorldsDir.listDirectoryEntries("SHA256SUMS*").sortedBy { it.name }.flatMap { Files.readAllLines(it) }.filter { it.isNotBlank() }
            .associate { line -> line.trim().split(Regex("\\s+"), 2).let { it[1].trim() to it[0] } }

    private fun hashFrozenWorld(path: Path): String {
        val text = String(Files.readAllBytes(path), Charsets.ISO_8859_1).replace("\r\n", "\n")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.ISO_8859_1)).joinToString("") { "%02x".format(it) }
    }

    fun judge(fileName: String, case: JsonObject): List<String> {
        val shapeProblems = findCaseShapeProblems(fileName, case)
        if (shapeProblems.isNotEmpty()) return shapeProblems
        return compareCase(case, observeCase(case))
    }

    private fun findCaseShapeProblems(fileName: String, case: JsonObject): List<String> {
        val problems = CASE_FIELDS.filter { it !in case }.map { "missing field $it" }.toMutableList()
        val kind = case["kind"]?.let { pythonStr(it) }
        if (kind !in KINDS) problems += "kind $kind is not one of $KINDS"
        if (case["source"]?.let { pythonStr(it) } !in SOURCES) problems += "source ${case["source"]} is not one of $SOURCES"
        if (case["id"]?.let { pythonStr(it) } != fileName.removeSuffix(".json")) problems += "id ${case["id"]} does not name its file $fileName"
        return problems
    }

    private fun materializeWorlds(worldSpec: JsonObject): Map<String, String> {
        val files = LinkedHashMap<String, String>()
        locations.liveWorldsDir.listDirectoryEntries().filter { Files.isRegularFile(it) }.forEach { files[it.name] = Files.readString(it) }
        locations.frozenWorldsDir.listDirectoryEntries("*$FROZEN_SUFFIX").forEach { files[it.name] = Files.readString(it) }
        val edits = worldSpec["edits"] as? JsonArray ?: return files
        if (edits.isEmpty()) return files
        val target = worldSpec["edits_in"] ?: worldSpec["file"] ?: JsonNull
        return applyMutation(files, JsonObject(mapOf("world" to target, "edits" to edits)))
    }

    private fun observeCase(case: JsonObject): Observation = try {
        val worlds = materializeWorlds(case.getValue("world").jsonObject)
        when (pythonStr(case.getValue("kind"))) {
            "expression" -> observeExpression(worlds, case)
            "world_load" -> observeWorldLoad(worlds, case)
            "replay" -> observeReplay(worlds, case)
            "report" -> observeReport(worlds, case)
            else -> observeScript(worlds, case)
        }
    } catch (problem: Exception) {
        mapOf("crash" to "${problem::class.simpleName}: ${problem.message}")
    }

    private fun worldFile(case: JsonObject): String = pythonStr(case.getValue("world").jsonObject.getValue("file"))

    private fun observeExpression(worlds: Map<String, String>, case: JsonObject): Observation {
        val input = case.getValue("input").jsonObject
        val world = World.open(worldFile(case), MapWorldLibrary(worlds))
        val cycle = findCycle(world)
        if (cycle.isNotEmpty()) return mapOf("error" to "cycle", "detail" to cycle.joinToString(" -> "))
        val log = EventLog(world)
        val setup = runSteps(log, input["setup"] as? JsonArray ?: JsonArray(emptyList()))
        val refusedSetup = setup.filter { "refused" in it }
        if (refusedSetup.isNotEmpty()) return mapOf("setup_refused" to refusedSetup)
        val state = log.stateOf("main")
        val bindings = input["bindings"] as? JsonObject ?: JsonObject(emptyMap())
        val tileInstance = (bindings["tile"] as? JsonArray)?.let { state.instanceAt(it[0].jsonPrimitive.int, it[1].jsonPrimitive.int) }
        val agent = bindings["self"]?.let { state.agents[pythonStr(it)] }
        return try {
            val tree = parseExpression(expandText(input.getValue("text")))
            val site = ScopeSite.valueOf((input["site"]?.let { pythonStr(it) } ?: "equation").uppercase())
            val produced = checkExpression(tree, Scope(world, site, agent?.type))
            val value = Evaluation(world, state, tileInstance = tileInstance, agent = agent).valueOf(tree)
            describeExpressionValue(produced, value)
        } catch (problem: ExpressionException) {
            mapOf("error" to problem.kind, "detail" to problem.message)
        }
    }

    private fun describeExpressionValue(produced: ValueType, value: Value): Observation = when (produced) {
        is ValueType.Num -> mapOf("type" to "num", "dims" to BaseDimension.entries.filter { produced.dimension.exponentOf(it) != 0 }.associate { it.label to produced.dimension.exponentOf(it) }, "value" to plainValue(value))
        ValueType.Bool -> mapOf("type" to "bool", "value" to plainValue(value))
        ValueType.Str -> mapOf("type" to "str", "value" to plainValue(value))
        ValueType.TileType -> mapOf("type" to "tile", "value" to plainValue(value))
        ValueType.InstanceType -> mapOf("type" to "instance", "value" to plainValue(value))
    }

    private fun plainValue(value: Value): Any? = when (value) {
        is Value.Num -> value.value
        is Value.Bool -> value.value
        is Value.Text -> value.value
        is Value.TileRef -> value.instance?.id
        is Value.InstanceRef -> value.record?.id
        Value.Null -> null
    }

    private fun expandText(text: JsonElement): String {
        if (text is JsonPrimitive) return text.content
        return text.jsonArray.joinToString("") { segment -> segment.jsonArray[0].jsonPrimitive.content.repeat(segment.jsonArray[1].jsonPrimitive.int) }
    }

    private fun observeWorldLoad(worlds: Map<String, String>, case: JsonObject): Observation {
        val report = WorldValidator.validate(worlds)
        val target = case.getValue("world").jsonObject["file"]?.takeIf { it !is JsonNull }?.let { pythonStr(it) }
        val fired = report.fired.filter { it.world == target }.map { it.rule }.distinct()
        return mapOf("valid" to (target in report.valid), "fired" to fired, "blind" to report.blind.map { it.rule })
    }

    private fun observeScript(worlds: Map<String, String>, case: JsonObject): Observation {
        val world = WorldLoader.load(worldFile(case), MapWorldLibrary(worlds))
        val log = EventLog(world)
        val steps = runSteps(log, case.getValue("input").jsonObject.getValue("steps").jsonArray)
        return mapOf("steps" to steps, "final" to reportBranches(log))
    }

    private fun observeReplay(worlds: Map<String, String>, case: JsonObject): Observation {
        val library = MapWorldLibrary(worlds)
        val live = EventLog(WorldLoader.load(worldFile(case), library))
        val input = case.getValue("input").jsonObject
        val steps = runSteps(live, input.getValue("steps").jsonArray)
        val dumped = live.dump()
        val tampered = (input["tamper"] as? JsonArray ?: JsonArray(emptyList())).fold(dumped as JsonElement) { document, edit -> applyDocumentEdit(document, edit.jsonObject) }
        val observed = mapOf("steps" to steps, "log" to canonicalJson(dumped), "live" to snapshotBranches(live))
        val replayed = try {
            EventLog.load(WorldLoader.load(worldFile(case), library), tampered)
        } catch (problem: Exception) {
            return observed + mapOf("loads" to false, "reason" to describeReplayRefusal(problem))
        }
        return observed + mapOf("loads" to true, "replayed" to snapshotBranches(replayed), "replayed_log" to canonicalJson(replayed.dump()))
    }

    private fun observeReport(worlds: Map<String, String>, case: JsonObject): Observation {
        val world = WorldLoader.load(worldFile(case), MapWorldLibrary(worlds))
        val log = EventLog(world)
        val input = case.getValue("input").jsonObject
        val steps = runSteps(log, input["steps"] as? JsonArray ?: JsonArray(emptyList()))
        val state = log.stateOf(input["branch"]?.let { pythonStr(it) } ?: "main")
        val values = try {
            (input["expressions"] as? JsonArray ?: JsonArray(emptyList())).map { evaluateStated(world, state, it.jsonObject) }
        } catch (problem: ExpressionException) {
            return mapOf("steps" to steps, "crash" to "expression refused: ${problem.kind}: ${problem.message}")
        }
        return mapOf(
            "steps" to steps,
            "render" to jsonOfObserved(renderProps(world, state)),
            "outputs" to jsonOfOutputs(reportOutputs(world, state)),
            "expressions" to JsonArray(values),
        )
    }

    private fun evaluateStated(world: World, state: State, entry: JsonObject): JsonElement {
        val tree = parseExpression(pythonStr(entry.getValue("text")))
        val site = ScopeSite.valueOf((entry["site"]?.let { pythonStr(it) } ?: "equation").uppercase())
        checkExpression(tree, Scope(world, site))
        return when (val value = Evaluation(world, state).valueOf(tree)) {
            Value.Null -> JsonNull
            is Value.Bool -> JsonPrimitive(value.value)
            else -> JsonPrimitive((value as Value.Num).value / parseExpectedUnit(entry["unit"]?.let { pythonStr(it) }).first)
        }
    }

    private fun jsonOfOutputs(outputs: WorldOutputs): JsonElement = jsonOfObserved(
        mapOf("counts" to outputs.counts, "areas" to outputs.areas, "equations" to outputs.equations, "stocks" to outputs.stocks, "scoring" to outputs.scoring, "ticks" to outputs.ticks),
    )

    private fun jsonOfObserved(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to jsonOfObserved(item) })
        is List<*> -> JsonArray(value.map { jsonOfObserved(it) })
        else -> error("an observation holds a ${value::class.simpleName}")
    }

    private fun describeReplayRefusal(problem: Exception): String = when (problem) {
        is WorldLoadException -> problem.message
        is RefusalException -> problem.refusal.toString()
        is ExpressionException -> problem.message
        is MalformedDataException -> problem.message
        else -> throw problem
    }

    private fun snapshotBranches(log: EventLog): Map<String, String> = log.heads.keys.sorted().associateWith { canonicalJson(log.stateOf(it).snapshot()) }

    private fun reportBranches(log: EventLog): Map<String, WorldOutputs> = log.heads.keys.sorted().associateWith { reportOutputs(log.world, log.stateOf(it)) }

    private fun runSteps(log: EventLog, steps: JsonArray): List<Map<String, String>> =
        steps.mapIndexed { index, step -> describeStepResult(takeStep(log, step.jsonObject, index)) }

    private fun takeStep(log: EventLog, step: JsonObject, index: Int): Any {
        val timestamp = step["timestamp"]?.let { pythonStr(it) } ?: scriptTimestamp(index)
        val actor = step["actor"]?.let { pythonStr(it) } ?: "jim"
        val verb = pythonStr(step.getValue("do"))
        val branch = step["on"]?.let { pythonStr(it) } ?: "main"
        val parameters = step["parameters"] as? JsonObject ?: JsonObject(emptyMap())
        if (isTruthy(step["raw"])) return log.attempt(branch, actor, verb, parameters, timestamp)
        return when (verb) {
            "tap" -> takeTap(log, step, branch, actor, timestamp)
            "branch" -> log.branch(pythonStr(step.getValue("name")), branchSource(step), actor, timestamp, isTruthy(step["proposal"]))
            "merge" -> log.merge(pythonStr(step.getValue("branch")), step["into"]?.let { pythonStr(it) } ?: "main", actor, timestamp)
            "revert" -> log.revert(pythonStr(step.getValue("event")), branch, actor, timestamp)
            else -> log.attempt(branch, actor, verb, parameters, timestamp)
        }
    }

    private fun branchSource(step: JsonObject): String? = when (val from = step["from"]) {
        null -> "main"
        is JsonNull -> null
        else -> pythonStr(from)
    }

    private fun takeTap(log: EventLog, step: JsonObject, branch: String, actor: String, timestamp: String): Any {
        val brush = step["brush"]?.takeIf { it !is JsonNull }?.let { pythonStr(it) }
        return when (val chosen = actionForTap(log.stateOf(branch), step.getValue("col").jsonPrimitive.int, step.getValue("row").jsonPrimitive.int, brush)) {
            is TapDecision.Refused -> chosen.refusal
            is TapDecision.Place -> log.attempt(branch, actor, "place", placeParameters(chosen.type, chosen.col, chosen.row), timestamp)
            is TapDecision.Remove -> log.attempt(branch, actor, "remove", tileParameters(chosen.col, chosen.row), timestamp)
        }
    }

    private fun describeStepResult(result: Any): Map<String, String> = when (result) {
        is Refusal -> mapOf("refused" to result.rule, "message" to result.message)
        is LogResult.Refused -> mapOf("refused" to result.refusal.rule, "message" to result.refusal.message)
        is LogResult.Committed -> mapOf("applied" to result.event.id)
        else -> error("a step produced $result")
    }

    private fun compareCase(case: JsonObject, observed: Observation): List<String> {
        observed["crash"]?.let { return listOf("crashed: $it") }
        observed["setup_refused"]?.let { return listOf("setup was refused: $it") }
        val expect = case.getValue("expect").jsonObject
        return when (pythonStr(case.getValue("kind"))) {
            "expression" -> compareExpression(expect, observed)
            "world_load" -> compareWorldLoad(expect, observed)
            "replay" -> compareReplay(expect, observed)
            "report" -> compareReport(expect, observed)
            else -> compareScript(expect, observed)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compareReport(expect: JsonObject, observed: Observation): List<String> {
        val problems = mutableListOf<String>()
        expect["steps"]?.let { problems += compareSteps(it.jsonArray, observed.getValue("steps") as List<Map<String, String>>) }
        STATED_SECTIONS.forEach { section -> expect[section]?.let { problems += compareStated(section, it, observed.getValue(section) as JsonElement) } }
        val render = observed.getValue("render") as JsonObject
        (expect["render_absent"] as? JsonArray)?.map { pythonStr(it) }?.filter { it in render }?.forEach { problems += "render.$it: expected absent, observed ${render[it]}" }
        return problems
    }

    private fun compareStated(where: String, expected: JsonElement, observed: JsonElement): List<String> = when {
        expected is JsonObject -> compareStatedObject(where, expected, observed)
        expected is JsonArray -> compareStatedList(where, expected, observed)
        isLiteral(expected) -> if (observed == expected) emptyList() else listOf("$where: expected $expected, observed $observed")
        isNumber(observed) && isClose(observed.jsonPrimitive.content.toDouble(), expected.jsonPrimitive) -> emptyList()
        else -> listOf("$where: expected $expected, observed $observed")
    }

    private fun isLiteral(element: JsonElement): Boolean = element is JsonNull || element.jsonPrimitive.isString || element.jsonPrimitive.booleanOrNull != null

    private fun isNumber(element: JsonElement): Boolean = element is JsonPrimitive && element !is JsonNull && !isLiteral(element)

    private fun compareStatedObject(where: String, expected: JsonObject, observed: JsonElement): List<String> {
        if (observed !is JsonObject) return listOf("$where: expected an object, observed $observed")
        return expected.flatMap { (key, value) -> observed[key]?.let { compareStated("$where.$key", value, it) } ?: listOf("$where.$key: missing") }
    }

    private fun compareStatedList(where: String, expected: JsonArray, observed: JsonElement): List<String> {
        if (observed !is JsonArray || observed.size != expected.size) return listOf("$where: expected ${expected.size} items, observed $observed".take(400))
        return expected.indices.flatMap { compareStated("$where[$it]", expected[it], observed[it]) }
    }

    private fun compareExpression(expect: JsonObject, observed: Observation): List<String> {
        expect["error"]?.let { expected ->
            return if (observed["error"] == pythonStr(expected)) emptyList() else listOf("expected error ${pythonStr(expected)}, observed $observed")
        }
        if ("error" in observed || "value" !in observed) return listOf("expected a value, observed $observed")
        val expectedValue = expect.getValue("value").jsonPrimitive
        val isLiteralExpectation = expectedValue.isString && expectedValue.content != "inf" || expectedValue.booleanOrNull != null && !expectedValue.isString
        if (isLiteralExpectation) return compareLiteral(expectedValue, observed["value"])
        val (factor, dims) = parseExpectedUnit(expect["unit"]?.let { pythonStr(it) })
        val problems = mutableListOf<String>()
        if (observed["type"] != "num" || observed["dims"] != dims) problems += "expected dimensions ${dims.ifEmpty { "dimensionless" }}, observed ${observed["type"]} ${observed["dims"]}"
        val stated = (observed["value"] as? Double)?.let { it / factor }
        if (!isClose(stated, expectedValue)) problems += "expected ${expectedValue.content} ${expect["unit"] ?: "1"}, observed $stated"
        return problems
    }

    private fun compareLiteral(expected: JsonPrimitive, observed: Any?): List<String> {
        val matches = if (expected.isString) observed == expected.content else observed is Boolean && observed == expected.booleanOrNull
        return if (matches) emptyList() else listOf("expected ${expected}, observed $observed")
    }

    private fun parseExpectedUnit(text: String?): Pair<Double, Map<String, Int>> {
        val cleaned = (text ?: "1").trim()
        if (cleaned == "1") return 1.0 to emptyMap()
        val units = unitTable.getValue("units").jsonObject
        var factor = 1.0
        val dims = LinkedHashMap<String, Int>()
        UNIT_PART.findAll(cleaned).forEach { match ->
            val (operator, name, power) = match.destructured
            val unit = units[name]?.jsonObject ?: error("units.json has no unit '$name'")
            val exponent = (if (operator == "/") -1 else 1) * (power.toIntOrNull() ?: 1)
            val ratio = unit.getValue("ratio").jsonArray.map { it.jsonPrimitive.doubleOrNull!! }
            factor *= (ratio[0] / ratio[1]).pow(exponent)
            unit.getValue("dims").jsonObject.forEach { (base, baseExponent) -> dims[base] = (dims[base] ?: 0) + exponent * baseExponent.jsonPrimitive.int }
        }
        return factor to dims.filterValues { it != 0 }
    }

    private fun isClose(observed: Any?, expected: JsonPrimitive): Boolean {
        if (expected.isString && expected.content == "inf") return observed == Double.POSITIVE_INFINITY
        val number = when (observed) {
            is Double -> observed
            is Int -> observed.toDouble()
            is Long -> observed.toDouble()
            else -> return false
        }
        val target = expected.doubleOrNull ?: return false
        return abs(number - target) <= maxOf(RELATIVE_TOLERANCE * abs(target), ABSOLUTE_TOLERANCE)
    }

    private fun compareWorldLoad(expect: JsonObject, observed: Observation): List<String> {
        val problems = mutableListOf<String>()
        val fired = observed.getValue("fired") as List<*>
        val blind = observed.getValue("blind") as List<*>
        expect["blind"]?.let { if (pythonStr(it) !in blind) problems += "expected BLIND ${pythonStr(it)}, observed blind $blind" }
        expect["valid"]?.let { if (observed["valid"] != it.jsonPrimitive.booleanOrNull) problems += "expected valid=$it, observed valid=${observed["valid"]} firing $fired" }
        expect["first"]?.let { if (fired.take(1) != listOf(pythonStr(it))) problems += "expected first refusal ${pythonStr(it)}, observed $fired" }
        expect["fired"]?.let { expected -> if (fired != expected.jsonArray.map { pythonStr(it) }) problems += "expected rules $expected, observed $fired" }
        return problems
    }

    @Suppress("UNCHECKED_CAST")
    private fun compareSteps(expected: JsonArray, observed: List<Map<String, String>>): List<String> {
        if (expected.size != observed.size) return listOf("expected ${expected.size} step results, observed ${observed.size}")
        return expected.indices.mapNotNull { index ->
            val wanted = expected[index].jsonObject
            listOf("applied", "refused", "message").firstOrNull { field -> field in wanted && observed[index][field] != pythonStr(wanted.getValue(field)) }
                ?.let { "step $index: expected $it ${wanted[it]}, observed ${observed[index]}" }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compareScript(expect: JsonObject, observed: Observation): List<String> {
        val problems = mutableListOf<String>()
        expect["steps"]?.let { problems += compareSteps(it.jsonArray, observed.getValue("steps") as List<Map<String, String>>) }
        val finals = observed.getValue("final") as Map<String, WorldOutputs>
        (expect["final"] as? JsonObject ?: JsonObject(emptyMap())).forEach { (branch, expected) ->
            val outputs = finals[branch]
            if (outputs == null) problems += "branch $branch does not exist" else problems += compareOutputs(branch, expected.jsonObject, outputs)
        }
        return problems
    }

    private fun compareOutputs(branch: String, expected: JsonObject, observed: WorldOutputs): List<String> {
        val problems = mutableListOf<String>()
        expected["ticks"]?.let { if (observed.ticks.toDouble() != it.jsonPrimitive.doubleOrNull) problems += "$branch ticks: expected $it, observed ${observed.ticks}" }
        val sections: Map<String, Map<String, Any?>> = mapOf(
            "counts" to observed.counts,
            "areas" to observed.areas,
            "equations" to observed.equations,
            "stocks" to observed.stocks,
            "scoring" to observed.scoring,
        )
        OUTPUT_SECTIONS.forEach { section ->
            (expected[section] as? JsonObject ?: JsonObject(emptyMap())).forEach { (key, value) ->
                val observedSection = sections.getValue(section)
                val actual = observedSection[key]
                when {
                    key !in observedSection -> problems += "$branch $section.$key: missing"
                    !isClose(actual, value.jsonPrimitive) -> problems += "$branch $section.$key: expected $value, observed $actual"
                }
            }
        }
        return problems
    }

    @Suppress("UNCHECKED_CAST")
    private fun compareReplay(expect: JsonObject, observed: Observation): List<String> {
        val problems = mutableListOf<String>()
        expect["steps"]?.let { problems += compareSteps(it.jsonArray, observed.getValue("steps") as List<Map<String, String>>) }
        val expectedLoads = expect.getValue("loads").jsonPrimitive.booleanOrNull
        if (observed["loads"] != expectedLoads) return problems + "expected loads=$expectedLoads, observed loads=${observed["loads"]} ${observed["reason"] ?: ""}"
        if (expectedLoads == false) {
            val wanted = expect["reason_contains"]?.let { pythonStr(it) } ?: ""
            if (wanted !in (observed["reason"] as String)) problems += "expected the refusal to say '$wanted', observed '${observed["reason"]}'"
            return problems
        }
        val live = observed.getValue("live") as Map<String, String>
        val replayed = observed.getValue("replayed") as Map<String, String>
        live.forEach { (branch, state) -> if (replayed[branch] != state) problems += "branch $branch: replayed state differs from the live run" }
        if (observed["replayed_log"] != observed["log"]) problems += "the replayed log differs from the live log"
        expect["log"]?.let { if (canonicalJson(it) != observed["log"]) problems += "log differs: expected ${canonicalJson(it).take(400)} observed ${(observed["log"] as String).take(400)}" }
        (expect["state"] as? JsonObject)?.forEach { (branch, state) ->
            if (canonicalJson(state) != replayed[branch]) problems += "branch $branch canonical state: expected ${canonicalJson(state)} observed ${replayed[branch]}"
        }
        return problems
    }
}

fun canonicalJson(element: JsonElement): String = when (element) {
    is JsonNull -> "null"
    is JsonObject -> element.keys.sorted().joinToString(",", "{", "}") { "${canonicalString(it)}:${canonicalJson(element.getValue(it))}" }
    is JsonArray -> element.joinToString(",", "[", "]") { canonicalJson(it) }
    is JsonPrimitive -> canonicalPrimitive(element)
}

private fun canonicalPrimitive(primitive: JsonPrimitive): String {
    if (primitive.isString) return canonicalString(primitive.content)
    primitive.booleanOrNull?.let { return if (it) "true" else "false" }
    return formatCanonicalNumber(primitive.content)
}

private fun formatCanonicalNumber(literal: String): String {
    val isInteger = literal.removePrefix("-").all { it.isDigit() }
    if (isInteger) return BigDecimal(literal).toPlainString().let { if (it == "-0") "0" else it }
    val value = literal.toDouble()
    require(value.isFinite()) { "$value has no canonical form" }
    val text = BigDecimal(value).setScale(9, RoundingMode.HALF_EVEN).toPlainString().trimEnd('0').trimEnd('.')
    return if (text == "" || text == "-0") "0" else text
}

private fun canonicalString(text: String): String = buildString {
    append('"')
    text.forEach { character ->
        append(
            when {
                character == '"' -> "\\\""
                character == '\\' -> "\\\\"
                character == '\n' -> "\\n"
                character == '\r' -> "\\r"
                character == '\t' -> "\\t"
                character == '\b' -> "\\b"
                character == '\u000C' -> "\\f"
                character < ' ' -> "\\u%04x".format(character.code)
                else -> character.toString()
            },
        )
    }
    append('"')
}
