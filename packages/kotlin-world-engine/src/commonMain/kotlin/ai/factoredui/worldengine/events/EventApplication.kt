package ai.factoredui.worldengine.events

import ai.factoredui.worldengine.expression.Evaluation
import ai.factoredui.worldengine.expression.Value
import ai.factoredui.worldengine.expression.storedProperty
import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.isTruthy
import ai.factoredui.worldengine.json.pythonFloat
import ai.factoredui.worldengine.json.pythonInt
import ai.factoredui.worldengine.json.required
import ai.factoredui.worldengine.json.requiredObject
import ai.factoredui.worldengine.text.pythonListRepr
import ai.factoredui.worldengine.text.pythonStr
import ai.factoredui.worldengine.text.pythonTupleRepr
import ai.factoredui.worldengine.json.asTextOrNull
import ai.factoredui.worldengine.state.AgentRecord
import ai.factoredui.worldengine.state.Endorsement
import ai.factoredui.worldengine.state.Instance
import ai.factoredui.worldengine.state.InstanceRecord
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.state.Tile
import ai.factoredui.worldengine.state.TileFootprint
import ai.factoredui.worldengine.units.parseUnit
import ai.factoredui.worldengine.world.RuleSpec
import ai.factoredui.worldengine.world.World
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class Refusal(val rule: String, val message: String) {
    override fun toString(): String = "$rule: $message"
}

class RefusalException(val refusal: Refusal) : Exception(refusal.toString())

data class AppliedEvent(val id: String, val actor: String, val action: String, val parameters: JsonObject)

typealias EventLookup = (String) -> AppliedEvent?

val VOTE_CLASSES: List<String> = listOf("on_site", "nearby", "supporting")
const val MAX_TICKS_PER_EVENT = 100_000

fun refuse(rule: String, message: String): Nothing = throw RefusalException(Refusal(rule, message))

fun applyEvent(world: World, state: State, event: AppliedEvent, lookup: EventLookup): State = when (event.action) {
    "place" -> applyPlace(world, state, event)
    "remove" -> applyRemove(world, state, event)
    "place_instance" -> applyPlaceInstance(world, state, event)
    "remove_instance" -> applyRemoveInstance(world, state, event)
    "dig", "raise" -> applyGroundChange(world, state, event)
    "tick" -> applyTick(world, state, event)
    "enroll" -> applyEnroll(world, state, event)
    "opt_in" -> applyOptIn(world, state, event)
    "endorse" -> applyEndorse(state, event)
    "revert" -> applyRevert(world, state, event, lookup)
    "merge" -> applyMerge(world, state, event, lookup)
    "branch" -> state
    else -> refuse("unknown-action", "no action '${event.action}'")
}

fun footprintTiles(world: World, typeId: String, col: Int, row: Int): List<Tile> {
    val (width, height) = world.footprintOf(typeId)
    return (0 until height).flatMap { dy -> (0 until width).map { dx -> Tile(col + dx, row + dy) } }
}

fun isOnGrid(world: World, tile: Tile): Boolean = tile.col in 0 until world.cols && tile.row in 0 until world.rows

fun isFootprintOnGrid(world: World, typeId: String, col: Int, row: Int): Boolean {
    val (width, height) = world.footprintOf(typeId)
    return isOnGrid(world, Tile(col, row)) && isOnGrid(world, Tile(col + width - 1, row + height - 1))
}

sealed interface RuleSubject {
    val type: String?

    data class TileObject(val footprint: TileFootprint) : RuleSubject {
        override val type: String? get() = footprint.type
    }

    data class PointObject(val record: InstanceRecord) : RuleSubject {
        override val type: String get() = record.type
    }
}

private fun ruleTargets(world: World, rule: RuleSpec, typeId: String?): Boolean {
    if (typeId == null) return "applies_to" !in rule.raw && "applies_to_tag" !in rule.raw
    rule.appliesTo?.let { return typeId in it }
    if ("applies_to_tag" in rule.raw) return rule.appliesToTag in (world.types[typeId]?.tags ?: emptyList())
    return true
}

private fun ruleApplies(world: World, rule: RuleSpec, verb: String, typeId: String?): Boolean =
    (rule.on ?: "place") == verb && ruleTargets(world, rule, typeId)

fun checkRules(world: World, state: State, verb: String, acted: RuleSubject) {
    world.rules.filter { it.hasRequire }.forEach { rule ->
        val subjects = ruleSubjects(world, state, rule, verb, acted)
        val tree = world.ast(rule.require)
        subjects.forEach { subject ->
            if (bindingFor(world, state, subject).valueOf(tree) == Value.Bool(false)) {
                refuse(rule.id, rule.message?.takeIf { it.isNotEmpty() } ?: "refused")
            }
        }
    }
}

private fun bindingFor(world: World, state: State, subject: RuleSubject): Evaluation = when (subject) {
    is RuleSubject.TileObject -> Evaluation(world, state, tileInstance = subject.footprint)
    is RuleSubject.PointObject -> Evaluation(world, state, actedInstance = subject.record)
}

private fun ruleSubjects(world: World, state: State, rule: RuleSpec, verb: String, acted: RuleSubject): List<RuleSubject> = when {
    rule.on == "always" -> state.instances.entries.sortedBy { it.key }.map { it.value }.filter { ruleTargets(world, rule, it.type) }.map { RuleSubject.TileObject(it) }
    ruleApplies(world, rule, verb, acted.type) -> listOf(acted)
    else -> emptyList()
}

private fun applyEffects(world: World, state: State, verb: String, instance: Instance) {
    world.rules.filter { it.hasEffect && ruleApplies(world, it, verb, instance.type) }.forEach { rule ->
        val effect = rule.effect ?: throw MalformedDataException("an effect is not an object")
        val value = Evaluation(world, state, tileInstance = instance).valueOf(world.ast(effect.to))
        val target = state.instances[instance.id] ?: throw ai.factoredui.worldengine.json.missingKey(instance.id)
        target.props[effect.set ?: throw ai.factoredui.worldengine.json.missingKey("set")] = value
    }
}

internal fun requireWorldVerb(world: World, verb: String) {
    if (verb !in world.actions) refuse("unknown-action", "world ${world.id} declares no action '$verb'")
}

private fun applyPlace(world: World, state: State, event: AppliedEvent): State {
    requireWorldVerb(world, "place")
    val parameters = event.parameters
    val typeElement = parameters.required("type")
    val typeId = typeElement.asTextOrNull()?.takeIf { it in world.types } ?: refuse("unknown-type", "no object type '${pythonStr(typeElement)}'")
    val col = pythonInt(parameters.required("col")).toInt()
    val row = pythonInt(parameters.required("row")).toInt()
    if (!isFootprintOnGrid(world, typeId, col, row)) {
        refuse("off-grid", "$typeId at ${pythonStr(parameters.getValue("col"))},${pythonStr(parameters.getValue("row"))} leaves the grid")
    }
    val tiles = footprintTiles(world, typeId, col, row)
    val occupied = tiles.firstOrNull { it in state.cells }
    if (occupied != null) {
        val holder = state.instanceAt(occupied.col, occupied.row)?.type
        refuse("occupied", "tile ${occupied.col},${occupied.row} already holds $holder; remove it first")
    }
    val instance = Instance(event.id, typeId, col, row, tiles, placedProperties(world, typeId, parameters))
    val candidate = state.copy()
    candidate.addInstance(instance)
    checkRules(world, candidate, "place", RuleSubject.TileObject(instance))
    applyEffects(world, candidate, "place", instance)
    return candidate
}

private fun placedProperties(world: World, typeId: String, parameters: JsonObject): MutableMap<String, ai.factoredui.worldengine.expression.Value> {
    val properties = world.defaultProperties(typeId)
    val given = parameters["properties"]?.let { it as? JsonObject ?: throw MalformedDataException("'properties' is not an object") } ?: return properties
    given.forEach { (name, value) ->
        val spec = world.propertySpec(typeId, name) ?: refuse("unknown-property", "$typeId has no property '$name'")
        properties[name] = storedProperty(spec, value)
    }
    return properties
}

private fun applyRemove(world: World, state: State, event: AppliedEvent): State {
    requireWorldVerb(world, "remove")
    val parameters = event.parameters
    val col = pythonInt(parameters.required("col")).toInt()
    val row = pythonInt(parameters.required("row")).toInt()
    val instance = state.instanceAt(col, row)
        ?: refuse("empty-tile", "nothing to remove at ${pythonStr(parameters.getValue("col"))},${pythonStr(parameters.getValue("row"))}")
    val candidate = state.copy()
    val removed = candidate.dropInstance(instance.id)
    checkRules(world, candidate, "remove", RuleSubject.TileObject(removed))
    return candidate
}

private fun applyTick(world: World, state: State, event: AppliedEvent): State {
    requireWorldVerb(world, "tick")
    val steps = event.parameters["n"]?.let { pythonInt(it) } ?: 1L
    if (steps < 1 || steps > MAX_TICKS_PER_EVENT) refuse("tick-bound", "a tick event advances 1 to $MAX_TICKS_PER_EVENT ticks")
    val candidate = state.copy()
    repeat(steps.toInt()) {
        val evaluation = Evaluation(world, candidate)
        val following = world.stocks.values.associate { stock -> stock.id to evaluation.numberOf(world.ast(stock.next)) }
        candidate.stocks.putAll(following)
        candidate.ticks += 1
    }
    return candidate
}

private fun storedAttributes(world: World, agentType: String, attributes: Map<String, JsonElement>): MutableMap<String, Double> {
    val specs = world.agents.getValue(agentType).attributes.associateBy { it.name }
    val unknown = (attributes.keys - specs.keys).sorted()
    if (unknown.isNotEmpty()) refuse("unknown-attribute", "agent type $agentType has no attribute ${pythonListRepr(unknown)}")
    return attributes.entries.associateTo(LinkedHashMap()) { (name, value) -> name to pythonFloat(value) * parseUnit(specs.getValue(name).unit).factor }
}

private fun attributesParameter(parameters: JsonObject): JsonObject =
    parameters["attributes"]?.let { it as? JsonObject ?: throw MalformedDataException("'attributes' is not an object") } ?: JsonObject(emptyMap())

private fun applyEnroll(world: World, state: State, event: AppliedEvent): State {
    requireWorldVerb(world, "enroll")
    val parameters = event.parameters
    val agentTypeElement = parameters.required("agent_type")
    val agentIdElement = parameters.required("agent_id")
    val agentType = agentTypeElement.asTextOrNull()?.takeIf { it in world.agents } ?: refuse("unknown-agent-type", "no agent type '${pythonStr(agentTypeElement)}'")
    val agentId = pythonStr(agentIdElement)
    if (agentId in state.agents) refuse("agent-exists", "agent $agentId is already enrolled")
    val defaults = world.agents.getValue(agentType).attributes.associate { it.name to (it.default ?: JsonPrimitive(0)) }
    val candidate = state.copy()
    candidate.agents[agentId] = AgentRecord(
        id = agentId,
        type = agentType,
        isSynthetic = parameters["synthetic"]?.let { isTruthy(it) } ?: true,
        attributes = storedAttributes(world, agentType, LinkedHashMap(defaults) + attributesParameter(parameters)),
    )
    return candidate
}

private fun applyOptIn(world: World, state: State, event: AppliedEvent): State {
    requireWorldVerb(world, "opt_in")
    val agentIdElement = event.parameters.required("agent_id")
    val agent = state.agents[agentIdElement.asTextOrNull()] ?: refuse("unknown-agent", "no agent ${pythonStr(agentIdElement)} to replace")
    val candidate = state.copy()
    val replaced = candidate.agents.getValue(agent.id)
    replaced.isSynthetic = false
    replaced.attributes.putAll(storedAttributes(world, agent.type, attributesParameter(event.parameters)))
    return candidate
}

private fun applyEndorse(state: State, event: AppliedEvent): State {
    val weightClass = event.parameters["weight_class"]
    val agent = state.agents[event.actor]
    if (agent != null && agent.isSynthetic) {
        refuse("projection-not-binding", "${event.actor} is a synthetic agent; its vote is a projection and cannot endorse")
    }
    val weightClassText = weightClass.asTextOrNull()?.takeIf { it in VOTE_CLASSES }
        ?: refuse("unknown-weight-class", "weight class must be one of ${pythonTupleRepr(VOTE_CLASSES)}")
    val candidate = state.copy()
    candidate.endorsements += Endorsement(event.actor, weightClassText)
    return candidate
}

private fun applyRevert(world: World, state: State, event: AppliedEvent, lookup: EventLookup): State {
    val undo = event.parameters.requiredObject("undo")
    val inner = AppliedEvent(
        id = event.id,
        actor = event.actor,
        action = undo.required("action").asTextOrNull() ?: pythonStr(undo.getValue("action")),
        parameters = undo.requiredObject("parameters"),
    )
    return applyEvent(world, state, inner, lookup)
}

private fun applyMerge(world: World, state: State, event: AppliedEvent, lookup: EventLookup): State {
    val mergedIds = event.parameters.required("events") as? JsonArray ?: throw MalformedDataException("'events' is not a list")
    return mergedIds.fold(state) { merged, idElement ->
        val eventId = pythonStr(idElement)
        val source = lookup(eventId) ?: throw ai.factoredui.worldengine.json.missingKey(eventId)
        try {
            applyEvent(world, merged, source, lookup)
        } catch (refused: RefusalException) {
            refuse(refused.refusal.rule, "merging $eventId: ${refused.refusal.message}")
        }
    }
}
