package ai.factoredui.compose.scene

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class Georeference(val originLatitude: Double, val originLongitude: Double, val northRotationDeg: Double)

fun sceneViewJson(scene: SceneView, georeference: Georeference? = null): String = sceneObject(scene, georeference).toString()

private fun sceneObject(scene: SceneView, georeference: Georeference?): JsonObject = JsonObject(
    buildMap {
        put("units", JsonPrimitive(scene.units))
        put("level_feet", JsonPrimitive(scene.levelFeet))
        put("tile_feet", JsonPrimitive(scene.tileFeet))
        put("cols", JsonPrimitive(scene.cols))
        put("rows", JsonPrimitive(scene.rows))
        put("window", windowJson(scene.window))
        put("types", JsonArray(scene.types.map(::typeJson)))
        scene.ground?.let { put("ground", groundJson(it)) }
        scene.surface?.let { put("surface", surfaceJson(it)) }
        scene.footprints?.let { put("footprints", footprintsJson(it)) }
        scene.instances?.let { put("instances", instancesJson(it)) }
        scene.water?.let { put("water", waterJson(it)) }
        scene.boundaries?.let { put("boundaries", boundariesJson(it)) }
        scene.grid?.let { put("grid", gridJson(it)) }
        scene.annotations?.let { put("annotations", annotationsJson(it)) }
        georeference?.let { put("georeference", georeferenceJson(it)) }
    },
)

private fun optional(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

private fun optional(value: Long?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

private fun longs(values: List<Long>): JsonArray = JsonArray(values.map(::JsonPrimitive))

private fun ints(values: List<Int>): JsonArray = JsonArray(values.map(::JsonPrimitive))

private fun strings(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

private fun pointsJson(points: List<Pair<Long, Long>>): JsonArray = JsonArray(points.map { longs(listOf(it.first, it.second)) })

private fun provenanceJson(provenance: Provenance): JsonObject = JsonObject(
    mapOf(
        "kind" to JsonPrimitive(provenance.kind.name.lowercase()),
        "source" to optional(provenance.source),
        "as_of" to optional(provenance.asOf),
        "note" to optional(provenance.note),
    ),
)

private fun windowJson(window: WindowMm): JsonObject = JsonObject(
    mapOf("x0" to JsonPrimitive(window.x0), "y0" to JsonPrimitive(window.y0), "x1" to JsonPrimitive(window.x1), "y1" to JsonPrimitive(window.y1)),
)

private fun typeJson(type: SceneType): JsonObject = JsonObject(
    mapOf(
        "id" to JsonPrimitive(type.id),
        "label" to JsonPrimitive(type.label),
        "tags" to strings(type.tags),
        "footprint_mm" to (type.footprintMm?.let { longs(listOf(it.first, it.second)) } ?: JsonNull),
        "height_mm" to optional(type.heightMm),
    ),
)

private fun groundJson(ground: GroundLayer): JsonObject = JsonObject(
    mapOf(
        "version" to JsonPrimitive(ground.version),
        "cols" to JsonPrimitive(ground.cols),
        "rows" to JsonPrimitive(ground.rows),
        "heights_mm" to longs(ground.heightsMm),
        "cut_fill_mm" to (ground.cutFillMm?.let(::longs) ?: JsonNull),
        "datum" to optional(ground.datum),
        "provenance" to provenanceJson(ground.provenance),
    ),
)

private fun surfaceJson(surface: SurfaceLayer): JsonObject = JsonObject(
    mapOf(
        "version" to JsonPrimitive(surface.version),
        "cols" to JsonPrimitive(surface.cols),
        "rows" to JsonPrimitive(surface.rows),
        "classes" to JsonArray(surface.classes.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "name" to JsonPrimitive(it.name))) }),
        "class_ids" to ints(surface.classIds),
        "provenance" to provenanceJson(surface.provenance),
    ),
)

private fun footprintJson(item: SceneFootprint): JsonObject = JsonObject(
    mapOf(
        "id" to JsonPrimitive(item.id),
        "type" to JsonPrimitive(item.type),
        "col" to JsonPrimitive(item.col),
        "row" to JsonPrimitive(item.row),
        "width" to JsonPrimitive(item.width),
        "height" to JsonPrimitive(item.height),
        "height_mm" to optional(item.heightMm),
    ),
)

private fun footprintsJson(layer: FootprintLayer): JsonObject = JsonObject(
    mapOf("version" to JsonPrimitive(layer.version), "items" to JsonArray(layer.items.map(::footprintJson)), "provenance" to provenanceJson(layer.provenance)),
)

private fun instanceJson(item: SceneInstance): JsonObject = JsonObject(
    mapOf(
        "id" to JsonPrimitive(item.id),
        "type" to JsonPrimitive(item.type),
        "x_mm" to JsonPrimitive(item.xMm),
        "y_mm" to JsonPrimitive(item.yMm),
        "z_mm" to JsonPrimitive(item.zMm),
        "rotation_deg" to JsonPrimitive(item.rotationDeg),
        "height_mm" to optional(item.heightMm),
        "crown_radius_mm" to optional(item.crownRadiusMm),
        "tags" to strings(item.tags),
        "provenance" to JsonPrimitive(item.provenance.name.lowercase()),
    ),
)

private fun instancesJson(layer: InstanceLayer): JsonObject = JsonObject(
    mapOf("version" to JsonPrimitive(layer.version), "items" to JsonArray(layer.items.map(::instanceJson)), "provenance" to provenanceJson(layer.provenance)),
)

private fun pondJson(pond: Pond): JsonObject = JsonObject(
    mapOf(
        "id" to JsonPrimitive(pond.id),
        "level_mm" to JsonPrimitive(pond.levelMm),
        "tiles" to JsonArray(pond.tiles.map { JsonObject(mapOf("col" to JsonPrimitive(it.col), "row" to JsonPrimitive(it.row), "depth_mm" to JsonPrimitive(it.depthMm))) }),
    ),
)

private fun flowJson(line: FlowLine): JsonObject = JsonObject(
    mapOf(
        "id" to JsonPrimitive(line.id),
        "points_mm" to pointsJson(line.pointsMm),
        "quantity" to JsonPrimitive(line.quantity),
        "unit" to JsonPrimitive(line.unit),
        "kind" to JsonPrimitive(line.kind),
    ),
)

private fun waterJson(layer: WaterLayer): JsonObject = JsonObject(
    mapOf(
        "version" to JsonPrimitive(layer.version),
        "ponds" to JsonArray(layer.ponds.map(::pondJson)),
        "flow" to JsonArray(layer.flow.map(::flowJson)),
        "provenance" to provenanceJson(layer.provenance),
    ),
)

private fun boundariesJson(layer: BoundaryLayer): JsonObject = JsonObject(
    mapOf(
        "version" to JsonPrimitive(layer.version),
        "polygons" to JsonArray(
            layer.polygons.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "kind" to JsonPrimitive(it.kind), "points_mm" to pointsJson(it.pointsMm))) },
        ),
        "provenance" to provenanceJson(layer.provenance),
    ),
)

private fun gridJson(grid: GridSpec): JsonObject = JsonObject(
    mapOf("shape" to JsonPrimitive(grid.shape), "tile_feet" to JsonPrimitive(grid.tileFeet), "cols" to JsonPrimitive(grid.cols), "rows" to JsonPrimitive(grid.rows)),
)

private fun annotationsJson(layer: AnnotationLayer): JsonObject = JsonObject(
    mapOf(
        "version" to JsonPrimitive(layer.version),
        "items" to JsonArray(
            layer.items.map {
                JsonObject(
                    mapOf(
                        "ref_kind" to JsonPrimitive(it.refKind),
                        "ref_id" to optional(it.refId),
                        "kind" to JsonPrimitive(it.kind),
                        "text" to JsonPrimitive(it.text),
                        "source" to optional(it.source),
                    ),
                )
            },
        ),
    ),
)

private fun georeferenceJson(georeference: Georeference): JsonObject = JsonObject(
    mapOf(
        "origin_latitude" to JsonPrimitive(georeference.originLatitude),
        "origin_longitude" to JsonPrimitive(georeference.originLongitude),
        "north_rotation_deg" to JsonPrimitive(georeference.northRotationDeg),
    ),
)
