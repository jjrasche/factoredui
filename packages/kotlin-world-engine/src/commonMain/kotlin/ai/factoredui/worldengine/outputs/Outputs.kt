package ai.factoredui.worldengine.outputs

import ai.factoredui.worldengine.events.Refusal
import ai.factoredui.worldengine.expression.Evaluation
import ai.factoredui.worldengine.json.missingKey
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.units.parseUnit
import ai.factoredui.worldengine.world.World

data class WorldOutputs(
    val counts: Map<String, Int>,
    val areas: Map<String, Double>,
    val equations: Map<String, Double?>,
    val stocks: Map<String, Double>,
    val scoring: Map<String, Double?>,
    val ticks: Long,
)

fun convertToUnit(value: Double, unit: String?): Double = value / parseUnit(unit).factor

fun convertMeasuredToUnit(value: Double?, unit: String?): Double? = value?.let { convertToUnit(it, unit) }

fun countUses(world: World, state: State): Map<String, Int> {
    val counts = world.types.keys.associateWithTo(LinkedHashMap()) { 0 }
    state.cells.values.forEach { instanceId ->
        val typeId = state.instances[instanceId]?.type ?: throw missingKey(instanceId)
        counts[typeId] = (counts[typeId] ?: throw missingKey(typeId)) + 1
    }
    return counts
}

fun reportOutputs(world: World, state: State): WorldOutputs {
    val evaluation = Evaluation(world, state)
    val counts = countUses(world, state)
    return WorldOutputs(
        counts = counts,
        areas = counts.mapValues { (_, count) -> count * world.tileArea },
        equations = world.equations.values.associate { it.id to convertMeasuredToUnit(evaluation.measuredNumberOf(world.ast(it.expr)), it.requiredUnit()) },
        stocks = world.stocks.values.associate { it.id to convertToUnit(state.stocks.getValue(it.id), it.requiredUnit()) },
        scoring = world.scoring.values.associate { it.id to convertMeasuredToUnit(evaluation.measuredNumberOf(world.ast(it.expr)), it.requiredUnit()) },
        ticks = state.ticks,
    )
}

fun renderProps(world: World, state: State): Map<String, Any?> {
    val props = tilemapProps(world, state)
    props["units"] = "mm"
    props["instances"] = state.sortedInstanceRecords().map { it.drawnFields() }
    world.frame?.let { props["frame"] = it }
    return props
}

private fun tilemapProps(world: World, state: State): MutableMap<String, Any?> {
    val outputs = reportOutputs(world, state)
    return linkedMapOf<String, Any?>(
        "cols" to world.cols,
        "rows" to world.rows,
        "shape" to (world.grid["shape"]?.let { ai.factoredui.worldengine.text.pythonStr(it) } ?: "square"),
        "view" to (world.grid["view"]?.let { ai.factoredui.worldengine.text.pythonStr(it) } ?: "iso"),
        "tile_area" to world.tileArea,
        "uses" to world.types.values.map { mapOf("id" to it.id, "label" to it.label, "color" to it.color, "sprite" to it.sprite) },
        "cells" to state.cells.entries.sortedBy { it.key }.map { (tile, instanceId) ->
            mapOf("col" to tile.col, "row" to tile.row, "use" to state.instances.getValue(instanceId).type)
        },
        "counts" to outputs.counts,
        "areas" to outputs.areas,
    )
}

sealed interface TapDecision {
    data class Place(val type: String, val col: Int, val row: Int) : TapDecision
    data class Remove(val col: Int, val row: Int) : TapDecision
    data class Refused(val refusal: Refusal) : TapDecision
}

fun actionForTap(state: State, col: Int, row: Int, brush: String?): TapDecision {
    val occupant = state.instanceAt(col, row)
    if (occupant == null) {
        if (brush == null) return TapDecision.Refused(Refusal("no-brush", "nothing selected to place"))
        return TapDecision.Place(brush, col, row)
    }
    if (brush == null || occupant.type == brush) return TapDecision.Remove(col, row)
    return TapDecision.Refused(Refusal("occupied", "tile $col,$row holds ${occupant.type}; tap with its own brush to remove it first"))
}
