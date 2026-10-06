package ai.factoredui.worldengine.log

import ai.factoredui.worldengine.events.Refusal
import ai.factoredui.worldengine.events.RefusalException
import ai.factoredui.worldengine.events.applyEvent
import ai.factoredui.worldengine.events.footprintTiles
import ai.factoredui.worldengine.events.inverseGroundChange
import ai.factoredui.worldengine.ground.GROUND_VERBS
import ai.factoredui.worldengine.ground.tileCornerTouches
import ai.factoredui.worldengine.expression.Value
import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.asTextOrNull
import ai.factoredui.worldengine.json.isTruthy
import ai.factoredui.worldengine.json.missingKey
import ai.factoredui.worldengine.json.pythonEquals
import ai.factoredui.worldengine.json.pythonInt
import ai.factoredui.worldengine.json.required
import ai.factoredui.worldengine.json.requiredObject
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.schema.EVENTS_SCHEMA_JSON
import ai.factoredui.worldengine.schema.schemaErrors
import ai.factoredui.worldengine.state.Instance
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.state.Tile
import ai.factoredui.worldengine.text.pythonListRepr
import ai.factoredui.worldengine.text.pythonRepr
import ai.factoredui.worldengine.text.pythonStr
import ai.factoredui.worldengine.units.parseUnit
import ai.factoredui.worldengine.world.World
import ai.factoredui.worldengine.world.WorldLoadException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

val MERGEABLE_VERBS: List<String> = listOf("place", "remove", "tick", "enroll", "opt_in", "revert", "place_instance", "remove_instance") + GROUND_VERBS
val REVERTIBLE_VERBS: List<String> = listOf("place", "remove") + GROUND_VERBS

data class BranchMeta(val from: String?, val isProposal: Boolean)

sealed interface Plan<out T> {
    data class Ready<T>(val value: T) : Plan<T>
    data class Refused(val refusal: Refusal) : Plan<Nothing>
}

class EventLog(val world: World) {
    private val eventList: MutableList<LogEvent> = mutableListOf()
    private val byId: MutableMap<String, LogEvent> = mutableMapOf()
    private val headMap: MutableMap<String, String?> = linkedMapOf("main" to null)
    private val branchMetaMap: MutableMap<String, BranchMeta> = linkedMapOf("main" to BranchMeta(null, false))
    private val states: MutableMap<String?, State> = mutableMapOf(null to world.seedState())

    val events: List<LogEvent> get() = eventList
    val heads: Map<String, String?> get() = headMap
    val branchMeta: Map<String, BranchMeta> get() = branchMetaMap

    fun lookup(eventId: String): LogEvent = byId[eventId] ?: throw missingKey(eventId)

    fun chain(head: String?): List<LogEvent> {
        val ordered = mutableListOf<LogEvent>()
        var cursor = head
        while (cursor != null) {
            val event = lookup(cursor)
            ordered += event
            cursor = event.parent
        }
        return ordered.reversed()
    }

    fun fold(head: String?): State =
        chain(head).fold(world.seedState()) { state, event -> applyEvent(world, state, event.asApplied()) { byId[it]?.asApplied() } }

    fun stateOf(branch: String = "main"): State {
        if (branch !in headMap) throw missingKey(branch)
        return states.getValue(headMap[branch]).copy()
    }

    fun flatten(chain: List<LogEvent>): List<LogEvent> = chain.flatMap { event ->
        when {
            event.action == "merge" -> flatten(mergedIds(event).map { lookup(it) })
            event.action in MERGEABLE_VERBS -> listOf(event)
            else -> emptyList()
        }
    }

    private fun mergedIds(event: LogEvent): List<String> =
        (event.parameters.required("events") as? JsonArray ?: throw MalformedDataException("'events' is not a list")).map { pythonStr(it) }

    private fun draft(branch: String, actor: String, action: String, parameters: JsonObject, timestamp: String): LogEvent =
        LogEvent("e${eventList.size + 1}", headMap[branch], world.id, branch, actor, action, parameters, timestamp)

    private fun commit(event: LogEvent): LogResult {
        val prior = states[event.parent] ?: throw missingKey(event.parent ?: "None")
        val following = try {
            applyEvent(world, prior, event.asApplied()) { byId[it]?.asApplied() }
        } catch (refused: RefusalException) {
            return LogResult.Refused(refused.refusal)
        }
        val committed = event.copy(touches = touchesOf(event, prior), removed = removedRecord(event, prior), removedInstance = removedInstanceRecord(event, prior))
        append(committed, following)
        return LogResult.Committed(committed)
    }

    private fun removedRecord(event: LogEvent, prior: State): JsonObject? {
        if (event.action != "remove") return null
        val removed = instanceAtParameters(event, prior)
        return JsonObject(
            linkedMapOf(
                "type" to JsonPrimitive(removed.type),
                "col" to JsonPrimitive(removed.col),
                "row" to JsonPrimitive(removed.row),
                "properties" to declaredProperties(removed),
            ),
        )
    }

    private fun removedInstanceRecord(event: LogEvent, prior: State): JsonObject? {
        if (event.action != "remove_instance") return null
        val instanceId = pythonStr(event.parameters.required("id"))
        return (prior.instanceLayer[instanceId] ?: throw missingKey(instanceId)).toJson()
    }

    private fun instanceAtParameters(event: LogEvent, prior: State): Instance {
        val col = pythonInt(event.parameters.required("col")).toInt()
        val row = pythonInt(event.parameters.required("row")).toInt()
        return prior.instanceAt(col, row) ?: throw MalformedDataException("no instance at $col,$row")
    }

    private fun declaredProperties(instance: Instance): JsonObject {
        val specs = world.types[instance.type]?.properties ?: emptyList()
        return JsonObject(specs.associateTo(LinkedHashMap()) { spec -> spec.name to declaredValue(instance.props[spec.name] ?: Value.Null, spec.isText, spec.unit) })
    }

    private fun declaredValue(value: Value, isText: Boolean, unit: String?): JsonElement {
        if (isText) return jsonOfValue(value)
        val number = (value as? Value.Num)?.value ?: throw MalformedDataException("unsupported operand type(s) for /")
        return JsonPrimitive(number / parseUnit(unit).factor)
    }

    private fun append(event: LogEvent, state: State) {
        eventList += event
        byId[event.id] = event
        headMap[event.branch] = event.id
        states[event.id] = state
    }

    private fun touchesOf(event: LogEvent, prior: State): List<String> {
        val parameters = event.parameters
        return when (event.action) {
            "place" -> footprintTiles(world, requiredTypeText(parameters), pythonInt(parameters.required("col")).toInt(), pythonInt(parameters.required("row")).toInt()).map { it.touchKey() }
            "remove" -> instanceAtParameters(event, prior).tiles.map { it.touchKey() }
            "place_instance" -> listOf("instance:${event.id}")
            "remove_instance" -> listOf("instance:${pythonStr(parameters.required("id"))}")
            in GROUND_VERBS -> tileCornerTouches(Tile(pythonInt(parameters.required("col")).toInt(), pythonInt(parameters.required("row")).toInt()))
            "tick" -> listOf("clock")
            "enroll", "opt_in" -> listOf("agent:${pythonStr(parameters.required("agent_id"))}")
            "revert" -> lookup(pythonStr(parameters.required("event"))).touches
            "merge" -> mergedIds(event).flatMap { lookup(it).touches }.toSet().sorted()
            else -> emptyList()
        }
    }

    private fun requiredTypeText(parameters: JsonObject): String = pythonStr(parameters.required("type"))

    fun attempt(branch: String, actor: String, action: String, parameters: JsonObject, timestamp: String): LogResult {
        if (branch !in headMap) return LogResult.Refused(Refusal("unknown-branch", "no branch '$branch'"))
        if (action in listOf("branch", "merge", "revert")) return LogResult.Refused(Refusal("governance-verb", "use Log.$action() for '$action'"))
        if (action == "endorse" && branchMetaMap[branch]?.isProposal != true) {
            return LogResult.Refused(Refusal("not-a-proposal", "branch $branch is not a proposal"))
        }
        return commit(draft(branch, actor, action, parameters, timestamp))
    }

    fun branch(name: String, source: String?, actor: String, timestamp: String, proposal: Boolean = false): LogResult {
        if (name in headMap) return LogResult.Refused(Refusal("branch-exists", "branch $name already exists"))
        val start = if (source != null && source in headMap) headMap[source] else source
        if (start != null && start !in byId) return LogResult.Refused(Refusal("unknown-branch", "no branch or event '$source'"))
        val parameters = JsonObject(linkedMapOf("name" to JsonPrimitive(name), "from" to (start?.let { JsonPrimitive(it) } ?: JsonNull), "proposal" to JsonPrimitive(proposal)))
        val event = LogEvent("e${eventList.size + 1}", start, world.id, name, actor, "branch", parameters, timestamp)
        return LogResult.Committed(openBranch(event))
    }

    private fun openBranch(event: LogEvent): LogEvent {
        val name = pythonStr(event.parameters.required("name"))
        if (name in headMap) throw WorldLoadException("branch $name is opened twice")
        val from = event.parameters.required("from").asTextOrNull()
        val opened = event.copy(touches = emptyList(), removed = null, removedInstance = null)
        branchMetaMap[name] = BranchMeta(from, isTruthy(event.parameters.required("proposal")))
        eventList += opened
        byId[opened.id] = opened
        headMap[name] = opened.id
        states[opened.id] = states[from] ?: throw missingKey(from ?: "None")
        return opened
    }

    fun endorsementsOn(branch: String): List<LogEvent> =
        chain(headMap[branch]).filter { it.action == "endorse" && it.branch == branch }

    fun planMerge(source: String, into: String): Plan<List<String>> {
        if (source !in headMap || into !in headMap) return Plan.Refused(Refusal("unknown-branch", "merge needs two branches, got $source and $into"))
        if (branchMetaMap.getValue(source).isProposal && endorsementsOn(source).isEmpty()) {
            return Plan.Refused(Refusal("proposal-unendorsed", "proposal $source carries no endorsement from a real person"))
        }
        val targetChain = chain(headMap[into])
        val sourceChain = chain(headMap[source])
        val targetFlat = flatten(targetChain)
        val sourceFlat = flatten(sourceChain)
        val targetApplied = (targetChain + targetFlat).map { it.id }.toSet()
        val sourceApplied = (sourceChain + sourceFlat).map { it.id }.toSet()
        val incoming = sourceFlat.filter { it.id !in targetApplied }
        if (incoming.isEmpty()) return Plan.Refused(Refusal("nothing-to-merge", "$into already holds every event of $source"))
        val diverged = targetFlat.filter { it.id !in sourceApplied }
        val clash = (incoming.flatMap { it.touches }.toSet() intersect diverged.flatMap { it.touches }.toSet()).sorted()
        if (clash.isNotEmpty()) return Plan.Refused(Refusal("merge-conflict", "both branches changed ${clash.joinToString(", ")} since they split"))
        return Plan.Ready(incoming.map { it.id })
    }

    fun merge(source: String, into: String, actor: String, timestamp: String): LogResult = when (val plan = planMerge(source, into)) {
        is Plan.Refused -> LogResult.Refused(plan.refusal)
        is Plan.Ready -> commit(draft(into, actor, "merge", mergeParameters(source, plan.value), timestamp))
    }

    private fun mergeParameters(source: String, eventIds: List<String>): JsonObject =
        JsonObject(linkedMapOf("branch" to JsonPrimitive(source), "events" to JsonArray(eventIds.map { JsonPrimitive(it) })))

    fun planRevert(eventId: String, branch: String): Plan<JsonObject> {
        val applied = flatten(chain(headMap[branch]))
        val ids = applied.map { it.id }
        if (eventId !in ids) return Plan.Refused(Refusal("revert-unknown", "$eventId is not applied on $branch"))
        val target = lookup(eventId)
        if (target.action !in REVERTIBLE_VERBS) {
            return Plan.Refused(Refusal("revert-unsupported", "only place and remove revert; branch from before $eventId instead"))
        }
        if (applied.any { it.action == "revert" && pythonStr(it.parameters.required("event")) == eventId }) {
            return Plan.Refused(Refusal("already-reverted", "$eventId is already reverted"))
        }
        val later = applied.drop(ids.indexOf(eventId) + 1)
        val clash = (target.touches.toSet() intersect later.flatMap { it.touches }.toSet()).sorted()
        if (clash.isNotEmpty()) return Plan.Refused(Refusal("revert-conflict", "${clash.joinToString(", ")} changed after $eventId"))
        return Plan.Ready(inverseOf(target))
    }

    private fun inverseOf(target: LogEvent): JsonObject {
        if (target.action == "place") {
            val position = JsonObject(linkedMapOf("col" to target.parameters.required("col"), "row" to target.parameters.required("row")))
            return JsonObject(linkedMapOf("action" to JsonPrimitive("remove"), "parameters" to position))
        }
        if (target.action in GROUND_VERBS) return inverseGroundChange(target.action, target.parameters)
        val removed = target.removed ?: throw missingKey("removed")
        return JsonObject(linkedMapOf("action" to JsonPrimitive("place"), "parameters" to removed))
    }

    fun revert(eventId: String, branch: String, actor: String, timestamp: String): LogResult {
        if (branch !in headMap) return LogResult.Refused(Refusal("unknown-branch", "no branch '$branch'"))
        return when (val plan = planRevert(eventId, branch)) {
            is Plan.Refused -> LogResult.Refused(plan.refusal)
            is Plan.Ready -> commit(draft(branch, actor, "revert", revertParameters(eventId, plan.value), timestamp))
        }
    }

    private fun revertParameters(eventId: String, undo: JsonObject): JsonObject =
        JsonObject(linkedMapOf("event" to JsonPrimitive(eventId), "undo" to undo))

    private fun recheckGovernance(event: LogEvent): String? = when (event.action) {
        "endorse" -> if (branchMetaMap[event.branch]?.isProposal == true) null else "endorse on ${event.branch}, which is not a proposal"
        "merge" -> recheckMerge(event)
        "revert" -> recheckRevert(event)
        else -> null
    }

    private fun recheckMerge(event: LogEvent): String? {
        val source = pythonStr(event.parameters.required("branch"))
        val plan = planMerge(source, event.branch)
        val declared = event.parameters.required("events")
        if (plan is Plan.Ready && pythonEquals(JsonArray(plan.value.map { JsonPrimitive(it) }), declared)) return null
        return "merge of $source is not the merge the conflict rule allows (${describePlan(plan) { pythonListRepr(it) }})"
    }

    private fun recheckRevert(event: LogEvent): String? {
        val eventId = pythonStr(event.parameters.required("event"))
        val plan = planRevert(eventId, event.branch)
        val declared = event.parameters.required("undo")
        if (plan is Plan.Ready && pythonEquals(plan.value, declared)) return null
        return "revert of $eventId is not the revert the rules allow (${describePlan(plan) { pythonRepr(it) }})"
    }

    private fun <T> describePlan(plan: Plan<T>, describe: (T) -> String): String = when (plan) {
        is Plan.Ready -> describe(plan.value)
        is Plan.Refused -> plan.refusal.toString()
    }

    private fun replay(raw: JsonObject) {
        val event = LogEvent.fromJson(raw)
        if (event.world != world.id) throw WorldLoadException("event ${event.id} belongs to world ${event.world}")
        if (event.action == "branch") {
            replayBranch(event)
            return
        }
        if (event.id in byId) throw WorldLoadException("event id ${event.id} appears twice")
        if (!headMap.containsKey(event.branch) || headMap[event.branch] != event.parent) {
            throw WorldLoadException("event ${event.id} does not extend the head of branch ${event.branch}")
        }
        recheckGovernance(event)?.let { throw WorldLoadException("event ${event.id} does not replay: $it") }
        val result = commit(event)
        if (result is LogResult.Refused) throw WorldLoadException("event ${event.id} does not replay: ${result.refusal}")
    }

    private fun replayBranch(event: LogEvent) {
        val start = event.parameters.required("from").asTextOrNull()
        if (event.parent != start || (start != null && start !in byId)) throw WorldLoadException("branch event ${event.id} does not start where it says")
        openBranch(event)
    }

    fun dump(): JsonObject = JsonObject(linkedMapOf("world" to JsonPrimitive(world.id), "events" to JsonArray(eventList.map { it.toJson() })))

    companion object {
        fun load(world: World, document: JsonElement): EventLog {
            val problems = schemaErrors(document, Json.parseToJsonElement(EVENTS_SCHEMA_JSON))
            if (problems.isNotEmpty()) throw WorldLoadException("event log does not match events.schema.json: ${pythonListRepr(problems.take(3))}")
            val log = EventLog(world)
            (document as JsonObject).requiredObjectList("events").forEach { log.replay(it) }
            return log
        }
    }
}

private fun JsonObject.requiredObjectList(key: String): List<JsonObject> =
    (required(key) as? JsonArray ?: throw MalformedDataException("'$key' is not a list")).map { it as? JsonObject ?: throw MalformedDataException("an event is not an object") }

private fun jsonOfValue(value: Value): JsonElement = when (value) {
    is Value.Num -> JsonPrimitive(value.value)
    is Value.Bool -> JsonPrimitive(value.value)
    is Value.Text -> JsonPrimitive(value.value)
    is Value.TileRef -> JsonPrimitive(value.instance?.id)
    is Value.InstanceRef -> JsonPrimitive(value.record?.id)
    Value.Null -> JsonNull
}
