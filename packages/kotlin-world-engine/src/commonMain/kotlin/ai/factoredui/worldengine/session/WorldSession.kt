package ai.factoredui.worldengine.session

import ai.factoredui.worldengine.events.Refusal
import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.log.LogEvent
import ai.factoredui.worldengine.log.LogResult
import ai.factoredui.worldengine.outputs.TapDecision
import ai.factoredui.worldengine.outputs.countUses
import ai.factoredui.worldengine.outputs.renderProps
import ai.factoredui.worldengine.outputs.reportOutputs
import ai.factoredui.worldengine.world.World
import ai.factoredui.worldengine.world.WorldLibrary
import ai.factoredui.worldengine.world.WorldLoader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface WorldAction {
    data class Place(val use: String, val col: Int, val row: Int) : WorldAction
    data class Remove(val col: Int, val row: Int) : WorldAction
    data class Tick(val steps: Int) : WorldAction
    data class Verb(val verb: String, val parameters: JsonObject) : WorldAction
}

sealed interface DispatchResult {
    data class Accepted(val event: LogEvent) : DispatchResult
    data class Refused(val rule: String, val message: String) : DispatchResult
    data class Failed(val kind: String, val message: String) : DispatchResult
}

data class PlacedObject(val id: String, val type: String, val col: Int, val row: Int, val width: Int, val height: Int)

data class ScoreView(val id: String, val label: String?, val value: Double?, val unit: String, val isBinding: Boolean)

@OptIn(ExperimentalTime::class)
fun utcTimestamp(): String = Clock.System.now().toString()

class WorldSession(
    val world: World,
    private val timestamps: () -> String = ::utcTimestamp,
    private val defaultActor: String = "host",
) {
    val log: EventLog = EventLog(world)
    private val parentBranches: MutableMap<String, String?> = linkedMapOf("main" to null)

    var currentBranch: String = "main"
        private set

    val branches: List<String> get() = log.heads.keys.toList()

    fun isProposal(branch: String): Boolean = log.branchMeta[branch]?.isProposal == true

    fun parentOf(branch: String): String? = parentBranches[branch]

    fun actionForTap(col: Int, row: Int, use: String?, branch: String = currentBranch): TapDecision =
        ai.factoredui.worldengine.outputs.actionForTap(log.stateOf(branch), col, row, use)

    fun tap(col: Int, row: Int, use: String?, branch: String = currentBranch, actor: String = defaultActor): DispatchResult =
        dispatch(actionForTap(col, row, use, branch), branch, actor)

    fun dispatch(decision: TapDecision, branch: String = currentBranch, actor: String = defaultActor): DispatchResult = when (decision) {
        is TapDecision.Place -> dispatch(WorldAction.Place(decision.type, decision.col, decision.row), branch, actor)
        is TapDecision.Remove -> dispatch(WorldAction.Remove(decision.col, decision.row), branch, actor)
        is TapDecision.Refused -> refusedResult(decision.refusal)
    }

    fun dispatch(action: WorldAction, branch: String = currentBranch, actor: String = defaultActor): DispatchResult {
        val (verb, parameters) = verbAndParameters(action)
        return guarded { log.attempt(branch, actor, verb, parameters, timestamps()) }
    }

    fun renderProps(branch: String = currentBranch): Map<String, Any?> {
        val state = log.stateOf(branch)
        val scores = scores(branch).map { mapOf("id" to it.id, "label" to it.label, "value" to it.value, "unit" to it.unit, "binding" to it.isBinding) }
        return renderProps(world, state) + ("scores" to scores)
    }

    fun scores(branch: String = currentBranch): List<ScoreView> {
        val values = reportOutputs(world, log.stateOf(branch)).scoring
        return world.scoring.values.map { ScoreView(it.id, it.label, values.getValue(it.id), it.requiredUnit(), it.isBinding) }
    }

    fun counts(branch: String = currentBranch): Map<String, Int> = countUses(world, log.stateOf(branch))

    fun placedObjects(branch: String = currentBranch): List<PlacedObject> =
        log.stateOf(branch).instances.values
            .map { PlacedObject(it.id, it.type, it.col, it.row, it.tiles.maxOf { tile -> tile.col } - it.col + 1, it.tiles.maxOf { tile -> tile.row } - it.row + 1) }
            .sortedWith(compareBy({ it.row }, { it.col }))

    fun countsDiff(branch: String, base: String): Map<String, Int> {
        val mine = counts(branch)
        val theirs = counts(base)
        return mine.mapValues { (use, count) -> count - (theirs[use] ?: 0) }
    }

    fun createBranch(name: String, from: String = currentBranch, isProposal: Boolean = false, actor: String = defaultActor): DispatchResult {
        val result = guarded { log.branch(name, from, actor, timestamps(), isProposal) }
        if (result is DispatchResult.Accepted) parentBranches[name] = from.takeIf { it in log.heads }
        return result
    }

    fun createProposal(name: String, from: String = currentBranch, actor: String = defaultActor): DispatchResult =
        createBranch(name, from, isProposal = true, actor = actor)

    fun switchBranch(name: String): Boolean {
        if (name !in log.heads) return false
        currentBranch = name
        return true
    }

    fun merge(source: String = currentBranch, into: String = parentOf(source) ?: "main", actor: String = defaultActor): DispatchResult =
        guarded { log.merge(source, into, actor, timestamps()) }

    fun revert(eventId: String, branch: String = currentBranch, actor: String = defaultActor): DispatchResult =
        guarded { log.revert(eventId, branch, actor, timestamps()) }

    fun undoLast(branch: String = currentBranch, actor: String = defaultActor): DispatchResult {
        if (branch !in log.heads) return DispatchResult.Refused("unknown-branch", "no branch '$branch'")
        val undoable = lastUndoableEvent(branch) ?: return DispatchResult.Refused("nothing-to-undo", "no place or remove on $branch is left to undo")
        return revert(undoable.id, branch, actor)
    }

    private fun lastUndoableEvent(branch: String): LogEvent? {
        val applied = log.flatten(log.chain(log.heads[branch]))
        val reverted = applied.filter { it.action == "revert" }.mapNotNull { (it.parameters["event"] as? JsonPrimitive)?.content }.toSet()
        return applied.lastOrNull { (it.action == "place" || it.action == "remove") && it.id !in reverted }
    }

    fun endorse(weightClass: String, actor: String = defaultActor, branch: String = currentBranch): DispatchResult =
        dispatch(WorldAction.Verb("endorse", JsonObject(mapOf("weight_class" to JsonPrimitive(weightClass)))), branch, actor)

    private fun verbAndParameters(action: WorldAction): Pair<String, JsonObject> = when (action) {
        is WorldAction.Place -> "place" to JsonObject(linkedMapOf("type" to JsonPrimitive(action.use), "col" to JsonPrimitive(action.col), "row" to JsonPrimitive(action.row)))
        is WorldAction.Remove -> "remove" to JsonObject(linkedMapOf("col" to JsonPrimitive(action.col), "row" to JsonPrimitive(action.row)))
        is WorldAction.Tick -> "tick" to JsonObject(mapOf("n" to JsonPrimitive(action.steps)))
        is WorldAction.Verb -> action.verb to action.parameters
    }

    private fun guarded(attempt: () -> LogResult): DispatchResult = try {
        when (val result = attempt()) {
            is LogResult.Committed -> DispatchResult.Accepted(result.event)
            is LogResult.Refused -> refusedResult(result.refusal)
        }
    } catch (problem: ExpressionException) {
        DispatchResult.Failed(problem.kind, problem.message)
    } catch (problem: MalformedDataException) {
        DispatchResult.Failed("malformed", problem.message)
    }

    private fun refusedResult(refusal: Refusal): DispatchResult = DispatchResult.Refused(refusal.rule, refusal.message)

    companion object {
        fun fromJson(worldJson: String, path: String = "world.world.json", library: WorldLibrary = WorldLibrary { null }): WorldSession =
            WorldSession(WorldLoader.loadFromJson(worldJson, path, library))
    }
}
