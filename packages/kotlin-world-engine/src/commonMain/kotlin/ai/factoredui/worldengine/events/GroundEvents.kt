package ai.factoredui.worldengine.events

import ai.factoredui.worldengine.ground.GROUND_AMOUNT_FIELDS
import ai.factoredui.worldengine.ground.GROUND_CEILING_MM
import ai.factoredui.worldengine.ground.GROUND_FLOOR_MM
import ai.factoredui.worldengine.ground.MAX_GROUND_CHANGE_MM
import ai.factoredui.worldengine.ground.isGroundNumber
import ai.factoredui.worldengine.ground.isWithinGroundRange
import ai.factoredui.worldengine.ground.tileCornerIndices
import ai.factoredui.worldengine.json.missingKey
import ai.factoredui.worldengine.json.pythonInt
import ai.factoredui.worldengine.json.required
import ai.factoredui.worldengine.state.GroundTile
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.state.Tile
import ai.factoredui.worldengine.text.formatFixedTrimmed
import ai.factoredui.worldengine.world.World
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun formatMm(value: Double): String = formatFixedTrimmed(value, 3)

private fun amountField(verb: String): String = GROUND_AMOUNT_FIELDS[verb] ?: throw missingKey(verb)

internal fun groundAmountMm(verb: String, parameters: JsonObject): Double {
    val field = amountField(verb)
    val amount = parameters[field]?.takeIf { isGroundNumber(it) } ?: refuse("ground-amount", "$verb needs $field as a number of millimetres")
    val millimetres = (amount as JsonPrimitive).content.toDouble()
    if (millimetres <= 0 || millimetres > MAX_GROUND_CHANGE_MM) {
        refuse("ground-amount", "$verb needs $field above 0 and at most $MAX_GROUND_CHANGE_MM mm, found ${formatMm(millimetres)}")
    }
    return millimetres
}

private fun checkGroundRange(verb: String, tile: Tile, cornersMm: List<Double>) {
    val outside = cornersMm.firstOrNull { !isWithinGroundRange(it) } ?: return
    refuse("ground-range", "$verb at ${tile.col},${tile.row} would take ground to ${formatMm(outside)} mm, outside $GROUND_FLOOR_MM to $GROUND_CEILING_MM mm")
}

internal fun applyGroundChange(world: World, state: State, event: AppliedEvent): State {
    val verb = event.action
    if (verb !in world.actions) refuse("unknown-action", "no action '$verb'")
    val surface = state.ground ?: refuse("no-ground", "world ${world.id} has no ground to dig or raise")
    val parameters = event.parameters
    val tile = Tile(pythonInt(parameters.required("col")).toInt(), pythonInt(parameters.required("row")).toInt())
    if (!isOnGrid(world, tile)) refuse("off-grid", "tile ${tile.col},${tile.row} is not on the ${world.cols} x ${world.rows} grid")
    val amount = groundAmountMm(verb, parameters)
    val corners = tileCornerIndices(world.cols, tile)
    val reshaped = surface.reshaped(corners, if (verb == "dig") -amount else amount)
    checkGroundRange(verb, tile, corners.map { reshaped.heightsMm[it] })
    val candidate = state.copy()
    candidate.ground = reshaped
    checkRules(world, candidate, verb, RuleSubject.TileObject(GroundTile(tile, candidate.instanceAt(tile.col, tile.row)?.type)))
    return candidate
}

fun inverseGroundChange(verb: String, parameters: JsonObject): JsonObject {
    val inverse = if (verb == "dig") "raise" else "dig"
    val undone = linkedMapOf("col" to parameters.required("col"), "row" to parameters.required("row"), amountField(inverse) to parameters.required(amountField(verb)))
    return JsonObject(linkedMapOf("action" to JsonPrimitive(inverse), "parameters" to JsonObject(undone)))
}
