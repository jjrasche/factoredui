package ai.factoredui.worldengine.state

import ai.factoredui.worldengine.expression.Value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class Tile(val col: Int, val row: Int) : Comparable<Tile> {
    override fun compareTo(other: Tile): Int = compareValuesBy(this, other, { it.col }, { it.row })

    fun touchKey(): String = "$col,$row"
}

class Instance(
    val id: String,
    val type: String,
    val col: Int,
    val row: Int,
    val tiles: List<Tile>,
    val props: MutableMap<String, Value>,
) {
    fun copy(): Instance = Instance(id, type, col, row, tiles, LinkedHashMap(props))
}

class AgentRecord(
    val id: String,
    val type: String,
    var isSynthetic: Boolean,
    val attributes: MutableMap<String, Double>,
) {
    fun copy(): AgentRecord = AgentRecord(id, type, isSynthetic, LinkedHashMap(attributes))
}

data class Endorsement(val actor: String, val weightClass: String)

data class InstanceRecord(
    val id: String,
    val type: String,
    val xMm: JsonElement,
    val yMm: JsonElement,
    val zMm: JsonElement,
    val rotationDeg: JsonElement,
    val heightMm: JsonElement,
    val crownRadiusMm: JsonElement,
    val provenance: String,
    val source: JsonElement,
    val error: JsonElement,
) {
    fun drawnFields(): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(id),
            "type" to JsonPrimitive(type),
            "x_mm" to xMm,
            "y_mm" to yMm,
            "z_mm" to zMm,
            "rotation_deg" to rotationDeg,
            "height_mm" to heightMm,
            "crown_radius_mm" to crownRadiusMm,
            "provenance" to JsonPrimitive(provenance),
        ),
    )

    fun toJson(): JsonObject = JsonObject(drawnFields() + linkedMapOf("source" to source, "error" to error))
}

class State {
    val instances: MutableMap<String, Instance> = LinkedHashMap()
    val cells: MutableMap<Tile, String> = LinkedHashMap()
    val stocks: MutableMap<String, Double> = LinkedHashMap()
    var ticks: Long = 0
    val agents: MutableMap<String, AgentRecord> = LinkedHashMap()
    val endorsements: MutableList<Endorsement> = mutableListOf()
    val instanceLayer: MutableMap<String, InstanceRecord> = LinkedHashMap()

    fun copy(): State {
        val copied = State()
        instances.forEach { (id, instance) -> copied.instances[id] = instance.copy() }
        copied.cells.putAll(cells)
        copied.stocks.putAll(stocks)
        copied.ticks = ticks
        agents.forEach { (id, agent) -> copied.agents[id] = agent.copy() }
        copied.endorsements.addAll(endorsements)
        copied.instanceLayer.putAll(instanceLayer)
        return copied
    }

    fun addInstance(instance: Instance) {
        instances[instance.id] = instance
        instance.tiles.forEach { cells[it] = instance.id }
    }

    fun dropInstance(instanceId: String): Instance {
        val instance = instances.remove(instanceId) ?: throw ai.factoredui.worldengine.json.missingKey(instanceId)
        instance.tiles.forEach { cells.remove(it) }
        return instance
    }

    fun instanceAt(col: Int, row: Int): Instance? = cells[Tile(col, row)]?.let { instances[it] }

    fun sortedInstanceRecords(): List<InstanceRecord> = instanceLayer.entries.sortedBy { it.key }.map { it.value }

    fun snapshot(): JsonObject {
        val canonical = linkedMapOf<String, JsonElement>(
            "cells" to JsonArray(cells.entries.sortedBy { it.key }.map { cellEntry(it.key, it.value) }),
            "stocks" to JsonObject(stocks.entries.sortedBy { it.key }.associate { it.key to JsonPrimitive(it.value) }),
            "ticks" to JsonPrimitive(ticks),
            "agents" to JsonObject(agents.entries.sortedBy { it.key }.associate { it.key to agentSnapshot(it.value) }),
            "endorsements" to JsonArray(endorsements.map { JsonObject(mapOf("actor" to JsonPrimitive(it.actor), "weight_class" to JsonPrimitive(it.weightClass))) }),
        )
        if (instanceLayer.isNotEmpty()) canonical["instances"] = JsonObject(instanceLayer.entries.sortedBy { it.key }.associate { it.key to it.value.toJson() })
        return JsonObject(canonical)
    }

    private fun cellEntry(tile: Tile, instanceId: String): JsonArray =
        JsonArray(listOf(JsonPrimitive(tile.col), JsonPrimitive(tile.row), JsonPrimitive(instances.getValue(instanceId).type)))

    private fun agentSnapshot(agent: AgentRecord): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(agent.id),
            "type" to JsonPrimitive(agent.type),
            "synthetic" to JsonPrimitive(agent.isSynthetic),
            "attributes" to JsonObject(agent.attributes.mapValues { JsonPrimitive(it.value) }),
        ),
    )
}
