package ai.factoredui.worldengine

import ai.factoredui.worldengine.log.EventLog
import ai.factoredui.worldengine.log.LogResult
import ai.factoredui.worldengine.script.runScript
import ai.factoredui.worldengine.world.MapWorldLibrary
import ai.factoredui.worldengine.world.World
import ai.factoredui.worldengine.world.WorldLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertIs

const val STAMP = "2026-10-05T12:00:00Z"
const val PARCEL_FILE = "parcel-five-acre.world.json"
const val LOCALITY_FILE = "locality-stub.world.json"
const val DUNGEON_FILE = "dungeon-tiny.world.json"

val REFERENCE_WORLD_FILES: Map<String, String> = mapOf(
    PARCEL_FILE to PARCEL_WORLD_JSON,
    LOCALITY_FILE to LOCALITY_WORLD_JSON,
    DUNGEON_FILE to DUNGEON_WORLD_JSON,
)

fun referenceLibrary(overrides: Map<String, String> = emptyMap()): MapWorldLibrary = MapWorldLibrary(REFERENCE_WORLD_FILES + overrides)

fun parcelWorld(): World = WorldLoader.load(PARCEL_FILE, referenceLibrary())

fun dungeonWorld(): World = WorldLoader.load(DUNGEON_FILE, referenceLibrary())

fun demoSteps(world: World): JsonArray {
    val demo = if (world.id == "dungeon-tiny") DUNGEON_DEMO_JSON else PARCEL_DEMO_JSON
    return Json.parseToJsonElement(demo).jsonObject.getValue("steps").jsonArray
}

fun runDemo(world: World): EventLog = runScript(world, demoSteps(world)).log

fun editedWorld(file: String, edit: (JsonObject) -> JsonObject): String =
    edit(Json.parseToJsonElement(REFERENCE_WORLD_FILES.getValue(file)).jsonObject).toString()

fun placeParameters(type: String, col: Int, row: Int): JsonObject = buildJsonObject {
    put("type", type)
    put("col", col)
    put("row", row)
}

fun tileParameters(col: Int, row: Int): JsonObject = buildJsonObject {
    put("col", col)
    put("row", row)
}

fun EventLog.place(type: String, col: Int, row: Int, branch: String = "main"): LogResult =
    attempt(branch, "jim", "place", placeParameters(type, col, row), STAMP)

fun EventLog.remove(col: Int, row: Int, branch: String = "main"): LogResult =
    attempt(branch, "jim", "remove", tileParameters(col, row), STAMP)

fun EventLog.layPath(vararg tiles: Pair<Int, Int>) {
    tiles.forEach { (col, row) -> assertIs<LogResult.Committed>(place("path", col, row)) }
}

fun LogResult.committedId(): String = assertIs<LogResult.Committed>(this).event.id

fun LogResult.refusedRule(): String = assertIs<LogResult.Refused>(this).refusal.rule
