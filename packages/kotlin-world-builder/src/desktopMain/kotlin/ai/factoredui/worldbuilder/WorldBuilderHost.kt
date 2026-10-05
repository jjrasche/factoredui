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
) {
    private var message: String = ""

    fun bindings(): Map<String, Any?> {
        val props = session.renderProps()
        val uses = usesOf(props)
        val counts = session.counts()
        val areas = areasOf(props)
        val labels = uses.associate { it["id"] as String to it["label"] as String }
        return linkedMapOf(
            "parcel" to mapOf("cols" to props["cols"], "rows" to props["rows"], "tile_area" to props["tile_area"], "view" to props["view"]),
            "uses" to uses,
            "cells" to emptyList<Any?>(),
            "footprints" to footprintsOf(),
            "instances" to instancesOf(props),
            "counts" to counts,
            "areas" to areas.mapValues { wholeWhenIntegral(it.value) },
            "usage_text" to usageLines(uses, counts, areas),
            "scores_text" to scoreLines(session.scores()),
            "message" to message,
            "branch" to session.currentBranch,
            "branch_line" to branchLine(),
            "diff_text" to diffText(labels),
            "controlled" to true,
        )
    }

    fun initialBrush(): String? = session.world.types.keys.firstOrNull()

    fun tap(col: Int, row: Int, brush: String?) {
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
        return mapOf("world.tileTapped" to tapped, "world.undo" to undone, "world.newProposal" to proposed, "world.cycleBranch" to cycled)
    }

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
        val kind = if (session.isProposal(session.currentBranch)) "proposal" else "branch"
        return "$kind ${session.currentBranch} (${session.branches.size} in all)"
    }

    private fun diffText(labels: Map<String, String>): String {
        val parent = session.parentOf(session.currentBranch)
        val diff = if (parent == null) emptyMap() else session.countsDiff(session.currentBranch, parent)
        return diffLine(session.currentBranch, parent, diff, labels)
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
