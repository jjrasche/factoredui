package ai.factoredui.worldengine.events

import ai.factoredui.worldengine.json.MalformedDataException
import ai.factoredui.worldengine.json.asTextOrNull
import ai.factoredui.worldengine.json.pythonFloat
import ai.factoredui.worldengine.json.required
import ai.factoredui.worldengine.json.requiredText
import ai.factoredui.worldengine.state.InstanceRecord
import ai.factoredui.worldengine.state.State
import ai.factoredui.worldengine.text.formatFixedTrimmed
import ai.factoredui.worldengine.text.pythonStr
import ai.factoredui.worldengine.units.ExactRatio
import ai.factoredui.worldengine.units.exactDecimalOf
import ai.factoredui.worldengine.world.ObjectType
import ai.factoredui.worldengine.world.World
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val MAX_INSTANCES = 5000

private val ZERO_MM: JsonElement = JsonPrimitive(0)

private fun instanceType(world: World, typeElement: JsonElement): ObjectType =
    typeElement.asTextOrNull()?.let { world.types[it] } ?: refuse("unknown-type", "no object type '${pythonStr(typeElement)}'")

private fun instanceHeight(objectType: ObjectType, ownHeight: JsonElement?): JsonElement {
    val height = ownHeight?.takeIf { it !is JsonNull } ?: objectType.heightMm?.takeIf { it !is JsonNull }
    return height ?: refuse("instance-needs-height", "${objectType.id} has no height_mm and the action gives none")
}

private fun proposedInstance(world: World, event: AppliedEvent): InstanceRecord {
    val parameters = event.parameters
    val objectType = instanceType(world, parameters.required("type"))
    return InstanceRecord(
        id = event.id,
        type = objectType.id,
        xMm = parameters.required("x_mm"),
        yMm = parameters.required("y_mm"),
        zMm = ZERO_MM,
        rotationDeg = parameters["rotation_deg"] ?: ZERO_MM,
        heightMm = instanceHeight(objectType, null),
        crownRadiusMm = JsonNull,
        provenance = "proposed",
        source = JsonNull,
        error = JsonNull,
    )
}

private fun measuredInstance(world: World, entry: JsonObject): InstanceRecord {
    val parameters = entry.required("parameters") as? JsonObject ?: throw MalformedDataException("seed parameters are not an object")
    val objectType = instanceType(world, parameters.required("type"))
    return InstanceRecord(
        id = entry.requiredText("id"),
        type = objectType.id,
        xMm = parameters.required("x_mm"),
        yMm = parameters.required("y_mm"),
        zMm = parameters["z_mm"] ?: ZERO_MM,
        rotationDeg = parameters["rotation_deg"] ?: ZERO_MM,
        heightMm = instanceHeight(objectType, parameters["height_mm"]),
        crownRadiusMm = parameters["crown_radius_mm"] ?: JsonNull,
        provenance = "measured",
        source = parameters["source"] ?: JsonNull,
        error = parameters["error"] ?: JsonNull,
    )
}

private fun isInsideGridMm(world: World, xMm: JsonElement, yMm: JsonElement): Boolean {
    val (width, height) = world.extentMm()
    return isWithin(exactDecimalOf(xMm), width) && isWithin(exactDecimalOf(yMm), height)
}

private fun isWithin(position: ExactRatio, extent: ExactRatio): Boolean = position >= ExactRatio.ZERO && position < extent

private fun formatMm(value: Double): String = formatFixedTrimmed(value, 3)

private fun checkInstanceRoom(world: World, state: State, record: InstanceRecord) {
    if (!isInsideGridMm(world, record.xMm, record.yMm)) {
        val (width, height) = world.extentMm()
        refuse(
            "instance-off-grid",
            "${record.type} at ${formatMm(pythonFloat(record.xMm))},${formatMm(pythonFloat(record.yMm))} mm " +
                "lies outside the ${formatMm(width.toDouble())} x ${formatMm(height.toDouble())} mm grid",
        )
    }
    if (state.instanceLayer.size >= MAX_INSTANCES) refuse("instance-cap", "the instance layer already holds $MAX_INSTANCES instances, the cap")
}

// A measured instance is a fact the seed records, not an action, so no rule is checked against it.
fun seatMeasuredInstance(world: World, state: State, entry: JsonObject) {
    val record = measuredInstance(world, entry)
    checkInstanceRoom(world, state, record)
    state.instanceLayer[record.id] = record
}

internal fun applyPlaceInstance(world: World, state: State, event: AppliedEvent): State {
    requireWorldVerb(world, "place_instance")
    val record = proposedInstance(world, event)
    val candidate = state.copy()
    checkInstanceRoom(world, candidate, record)
    candidate.instanceLayer[event.id] = record
    checkRules(world, candidate, "place_instance", RuleSubject.PointObject(record))
    return candidate
}

internal fun applyRemoveInstance(world: World, state: State, event: AppliedEvent): State {
    requireWorldVerb(world, "remove_instance")
    val idElement = event.parameters.required("id")
    val instanceId = idElement.asTextOrNull()?.takeIf { it in state.instanceLayer } ?: refuse("unknown-instance", "no instance '${pythonStr(idElement)}' to remove")
    val candidate = state.copy()
    val removed = candidate.instanceLayer.remove(instanceId) ?: throw MalformedDataException(instanceId)
    checkRules(world, candidate, "remove_instance", RuleSubject.PointObject(removed))
    return candidate
}
