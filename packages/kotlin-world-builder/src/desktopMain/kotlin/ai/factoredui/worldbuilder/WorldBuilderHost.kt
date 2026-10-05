package ai.factoredui.worldbuilder

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.worldengine.session.DispatchResult
import ai.factoredui.worldengine.session.WorldSession
import kotlinx.serialization.json.JsonElement

data class UsePresentation(val height: Double? = null, val critter: String? = null, val image: String? = null)

private const val ERASE_BRUSH = "erase"
private const val ROOT_BRANCH = "main"
private const val PROPOSAL_PREFIX = "proposal-"

class WorldBuilderHost(
    val session: WorldSession,
    private val presentation: Map<String, UsePresentation> = emptyMap(),
    initialPlanNames: Map<String, String> = emptyMap(),
    private val writePlan: (fileName: String, text: String) -> String = ::writePlanToDocuments,
) {
    private var message: String = ""
    private var selectedInstanceId: String? = null
    private var selectedScoreId: String? = null
    private val plans = PlanNames(ROOT_BRANCH, PROPOSAL_PREFIX, initialPlanNames)

    fun bindings(): Map<String, Any?> {
        val props = session.renderProps()
        val uses = usesOf(props)
        val counts = session.counts()
        val areas = areasOf(props)
        val labels = uses.associate { it["id"] as String to it["label"] as String }
        val card = instanceCard(labels)
        val comparison = comparison()
        val scores = session.scores()
        val scoreCard = scoreCard(scores)
        return linkedMapOf(
            "parcel" to mapOf("cols" to props["cols"], "rows" to props["rows"], "tile_area" to props["tile_area"], "view" to props["view"]),
            "uses" to uses,
            "cells" to emptyList<Any?>(),
            "footprints" to footprintsOf(),
            "instances" to instancesOf(props),
            "counts" to counts,
            "areas" to areas.mapValues { wholeWhenIntegral(it.value) },
            "usage_text" to usageLines(uses, counts, areas, instanceTallies()),
            "instance_title" to card.first,
            "instance_text" to card.second,
            "scores_text" to scoreLines(scores),
            "score_groups" to scoreGroupList(scores, labels.values.toList()),
            "score_title" to scoreCard.first,
            "score_text" to scoreCard.second,
            "message" to message,
            "branch" to session.currentBranch,
            "branch_line" to branchLine(),
            "plans" to planList(),
            "diff_text" to diffText(labels),
            "compare_title" to comparison.first,
            "compare_text" to comparison.second,
            "controlled" to true,
        )
    }

    fun initialBrush(): String? = session.world.types.keys.firstOrNull()

    fun selectInstance(id: String) {
        selectedInstanceId = id
    }

    fun selectScore(id: String) {
        selectedScoreId = if (selectedScoreId == id) null else id
    }

    fun tap(col: Int, row: Int, brush: String?) {
        selectedInstanceId = null
        val use = brush?.takeIf { it.isNotEmpty() && it != ERASE_BRUSH }
        message = messageOf(session.tap(col, row, use))
    }

    fun undo() {
        message = messageOf(session.undoLast())
    }

    fun newProposal() {
        val name = "$PROPOSAL_PREFIX${session.branches.count { it.startsWith(PROPOSAL_PREFIX) } + 1}"
        val result = session.createProposal(name)
        if (result is DispatchResult.Accepted) session.switchBranch(name)
        message = messageOf(result)
    }

    fun switchTo(branch: String) {
        if (branch !in session.branches) return
        session.switchBranch(branch)
        selectedInstanceId = null
        message = ""
    }

    fun renameCurrent(name: String) {
        message = if (plans.rename(session.currentBranch, name)) "" else "Give the plan a name first."
    }

    fun savePlan() {
        val text = encodePlanFile(session.log.dump(), plans.exported())
        val path = writePlan(planFileName(session.world.id), text)
        message = "Saved to $path. Send that file to share this plan; open it again with the Plan option."
    }

    fun cycleBranch() {
        val branches = session.branches
        val next = branches[(branches.indexOf(session.currentBranch) + 1) % branches.size]
        session.switchBranch(next)
        message = ""
    }

    fun actions(publish: () -> Unit): Map<String, ActionHandler> {
        val tapped: ActionHandler = { params ->
            tap((params["col"] as Number).toInt(), (params["row"] as Number).toInt(), params["use"] as? String)
            publish()
        }
        val undone: ActionHandler = { _ ->
            undo()
            publish()
        }
        val proposed: ActionHandler = { _ ->
            newProposal()
            publish()
        }
        val cycled: ActionHandler = { _ ->
            cycleBranch()
            publish()
        }
        val instanceTapped: ActionHandler = { params ->
            (params["id"] as? String)?.let { selectInstance(it) }
            publish()
        }
        val scoreTapped: ActionHandler = { params ->
            (params["id"] as? String)?.let { selectScore(it) }
            publish()
        }
        val saved: ActionHandler = { _ ->
            savePlan()
            publish()
        }
        val switched: ActionHandler = { params ->
            (params["id"] as? String)?.let { switchTo(it) }
            publish()
        }
        val renamed: ActionHandler = { params ->
            renameCurrent(params["name"] as? String ?: "")
            publish()
        }
        return mapOf("world.tileTapped" to tapped, "world.instanceTapped" to instanceTapped, "world.scoreTapped" to scoreTapped, "world.savePlan" to saved, "world.switchPlan" to switched, "world.renamePlan" to renamed, "world.undo" to undone, "world.newProposal" to proposed, "world.cycleBranch" to cycled)
    }

    private fun instanceTallies(): Map<String, InstanceTally> =
        session.instanceRecords().groupBy { it.type }.mapValues { (_, records) ->
            InstanceTally(records.count { it.provenance == "measured" }, records.count { it.provenance != "measured" })
        }

    private fun instanceCard(labels: Map<String, String>): Pair<String, String> {
        val record = session.instanceRecords().firstOrNull { it.id == selectedInstanceId } ?: return "" to ""
        return instanceTitle(record, labels[record.type] ?: record.type) to instanceCardLines(record).joinToString("\n")
    }

    private fun scoreGroupList(scores: List<ai.factoredui.worldengine.session.ScoreView>, useLabels: List<String>): List<Map<String, Any?>> =
        scoreGroups(scores, useLabels).map { group ->
            mapOf("title" to group.title, "rows" to group.rows.map { mapOf("id" to it.id, "line" to it.line) })
        }

    private fun scoreCard(scores: List<ai.factoredui.worldengine.session.ScoreView>): Pair<String, String> {
        val score = scores.firstOrNull { it.id == selectedScoreId } ?: return "" to ""
        return scoreDetailTitle(score) to scoreDetailLines(score, unpricedUseLabels()).joinToString("\n")
    }

    private fun unpricedUseLabels(): List<String> =
        session.world.types.values.filter { type -> type.properties.none { it.kind == "price" } }.map { it.label ?: it.id }

    private fun footprintsOf(): List<Map<String, Any?>> =
        session.placedObjects().map { placed ->
            mapOf("id" to placed.id, "use" to placed.type, "col" to placed.col, "row" to placed.row, "width" to placed.width, "height" to placed.height)
        }

    private fun instancesOf(props: Map<String, Any?>): List<Any?> =
        (props["instances"] as? List<*>).orEmpty().map { entry -> if (entry is JsonElement) plainOf(entry) else entry }

    private fun usesOf(props: Map<String, Any?>): List<Map<String, Any?>> =
        asMaps(props["uses"]).map { use ->
            val extra = presentation[use["id"] as String]
            use + listOfNotNull(extra?.height?.let { "height" to it }, extra?.critter?.let { "critter" to it }, extra?.image?.let { "image" to it })
        }

    private fun areasOf(props: Map<String, Any?>): Map<String, Double> =
        (props["areas"] as Map<*, *>).entries.associate { (key, value) -> key as String to (value as Number).toDouble() }

    private fun branchLine(): String {
        val count = session.branches.size
        return "Viewing: ${plans.nameOf(session.currentBranch)} ($count ${if (count == 1) "plan" else "plans"} in all)"
    }

    private fun planList(): List<Map<String, Any?>> =
        session.branches.map { branch ->
            mapOf("id" to branch, "label" to plans.nameOf(branch), "status" to if (branch == session.currentBranch) "viewing" else "")
        }

    private fun diffText(labels: Map<String, String>): String {
        val parent = session.parentOf(session.currentBranch)
        val diff = if (parent == null) emptyMap() else session.countsDiff(session.currentBranch, parent)
        return diffLine(plans.nameOf(session.currentBranch), parent?.let { plans.nameOf(it) }, diff, labels)
    }

    private fun comparison(): Pair<String, String> {
        val current = session.currentBranch
        val parent = session.parentOf(current)
        if (parent != null) return "Compared with ${plans.nameOf(parent)}" to compareLines(session.scores(parent), session.scores(current))
        if (session.branches.size > 1) return "Compare plans" to "Pick an alternative to see how it differs from ${plans.nameOf(current)}."
        return "" to ""
    }

    private fun messageOf(result: DispatchResult): String = when (result) {
        is DispatchResult.Accepted -> ""
        is DispatchResult.Refused -> "Refused: ${result.message}"
        is DispatchResult.Failed -> "Failed: ${result.message}"
    }
}

private fun wholeWhenIntegral(value: Double): Any = if (value % 1.0 == 0.0) value.toLong() else value

private fun asMaps(value: Any?): List<Map<String, Any?>> =
    (value as List<*>).map { entry -> (entry as Map<*, *>).entries.associate { (key, item) -> key as String to item } }

fun RenderContext.applyBindings(bindings: Map<String, Any?>) {
    bindings.forEach { (key, value) -> setBinding(key, value) }
}
