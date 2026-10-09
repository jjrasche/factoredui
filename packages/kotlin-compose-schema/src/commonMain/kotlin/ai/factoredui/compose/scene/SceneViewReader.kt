package ai.factoredui.compose.scene

import ai.factoredui.compose.layout.MM_PER_FOOT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class SceneViewFormatException(message: String) : IllegalArgumentException(message)

private const val MILLIMETRES = "mm"

fun readSceneView(text: String): SceneView {
    val root = try {
        Json.parseToJsonElement(text)
    } catch (failure: IllegalArgumentException) {
        throw SceneViewFormatException("the scene is not valid JSON")
    } as? JsonObject ?: throw SceneViewFormatException("the scene must be a JSON object")
    return sceneViewOf(root)
}

private fun sceneViewOf(root: JsonObject): SceneView {
    val units = root.string("units") ?: throw SceneViewFormatException("the scene states no units")
    if (units != MILLIMETRES) throw SceneViewFormatException("the scene must be in millimetres, not $units")
    return SceneView(
        levelFeet = root.requiredInt("level_feet"),
        tileFeet = root.requiredDouble("tile_feet"),
        cols = root.requiredInt("cols"),
        rows = root.requiredInt("rows"),
        window = windowOf(root.requiredObject("window")),
        types = root.objects("types").map(::typeOf),
        ground = root.obj("ground")?.let(::groundOf),
        surface = root.obj("surface")?.let(::surfaceOf),
        footprints = root.obj("footprints")?.let(::footprintsOf),
        instances = root.obj("instances")?.let(::instancesOf),
        water = root.obj("water")?.let(::waterOf),
        boundaries = root.obj("boundaries")?.let(::boundariesOf),
        grid = root.obj("grid")?.let { gridOf(it, root.requiredDouble("tile_feet")) },
        annotations = root.obj("annotations")?.let(::annotationsOf),
        stackLevels = root.objects("stack_levels").map(::stackLevelOf),
        dropped = root.strings("dropped"),
    )
}

private fun JsonObject.value(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

private fun JsonObject.string(key: String): String? = (value(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.long(key: String): Long? = (value(key) as? JsonPrimitive)?.longOrNull

private fun JsonObject.double(key: String): Double? = (value(key) as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.obj(key: String): JsonObject? = value(key) as? JsonObject

private fun JsonObject.array(key: String): JsonArray = value(key) as? JsonArray ?: JsonArray(emptyList())

private fun JsonObject.objects(key: String): List<JsonObject> = array(key).mapNotNull { it as? JsonObject }

private fun JsonObject.strings(key: String): List<String> = array(key).mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }

private fun JsonObject.longs(key: String): List<Long> = array(key).map { (it as? JsonPrimitive)?.longOrNull ?: throw SceneViewFormatException("$key holds a value that is not a whole number") }

private fun JsonObject.requiredLong(key: String): Long = long(key) ?: throw SceneViewFormatException("the scene lacks a whole number $key")

private fun JsonObject.requiredInt(key: String): Int = requiredLong(key).toInt()

private fun JsonObject.requiredDouble(key: String): Double = double(key) ?: throw SceneViewFormatException("the scene lacks a number $key")

private fun JsonObject.requiredString(key: String): String = string(key) ?: throw SceneViewFormatException("the scene lacks a text $key")

private fun JsonObject.requiredObject(key: String): JsonObject = obj(key) ?: throw SceneViewFormatException("the scene lacks an object $key")

private fun pairOf(raw: JsonElement?): Pair<Long, Long>? {
    val values = (raw as? JsonArray)?.map { (it as? JsonPrimitive)?.longOrNull } ?: return null
    if (values.size != 2 || values.any { it == null }) return null
    return values[0]!! to values[1]!!
}

private fun windowOf(window: JsonObject) = WindowMm(window.requiredLong("x0"), window.requiredLong("y0"), window.requiredLong("x1"), window.requiredLong("y1"))

private fun typeOf(type: JsonObject): SceneType = SceneType(
    id = type.requiredString("id"),
    label = type.string("label") ?: type.requiredString("id"),
    tags = type.strings("tags"),
    footprintMm = pairOf(type.value("footprint_mm")),
    heightMm = type.long("height_mm"),
    unitCostUsd = type.double("unit_cost_usd"),
    unitWeightKg = type.double("unit_weight_kg"),
    unitWeightNullReason = type.string("unit_weight_null_reason"),
)

private fun provenanceOf(layer: JsonObject): Provenance {
    val fields = layer.obj("provenance") ?: return Provenance(ProvenanceKind.DERIVED)
    val kind = ProvenanceKind.entries.firstOrNull { it.tag == fields.string("tag") }
        ?: ProvenanceKind.entries.firstOrNull { it.name.equals(fields.string("kind"), ignoreCase = true) }
        ?: ProvenanceKind.DERIVED
    return Provenance(
        kind = kind,
        source = fields.string("source"),
        asOf = fields.string("as_of"),
        note = fields.string("note"),
        sourceSha256 = fields.string("source_sha256"),
        observedAt = fields.string("observed_at"),
        registrationErrorMm = fields.long("registration_error_mm"),
    )
}

private fun groundOf(layer: JsonObject): GroundLayer = GroundLayer(
    version = layer.long("version") ?: 0L,
    cols = layer.requiredInt("cols"),
    rows = layer.requiredInt("rows"),
    heightsMm = layer.longs("heights_mm"),
    cutFillMm = layer.value("cut_fill_mm")?.let { layer.longs("cut_fill_mm") },
    datum = layer.string("datum"),
    provenance = provenanceOf(layer),
)

private fun surfaceOf(layer: JsonObject): SurfaceLayer = SurfaceLayer(
    version = layer.long("version") ?: 0L,
    cols = layer.requiredInt("cols"),
    rows = layer.requiredInt("rows"),
    classes = layer.objects("classes").map { SurfaceClass(it.requiredInt("id"), it.requiredString("name")) },
    classIds = layer.longs("class_ids").map { it.toInt() },
    provenance = provenanceOf(layer),
)

private fun footprintOf(item: JsonObject): SceneFootprint = SceneFootprint(
    id = item.requiredString("id"),
    type = item.requiredString("type"),
    col = item.requiredInt("col"),
    row = item.requiredInt("row"),
    width = item.long("width")?.toInt() ?: 1,
    height = item.long("height")?.toInt() ?: 1,
    heightMm = item.long("height_mm"),
    stackLevel = item.long("stack_level")?.toInt() ?: 0,
    zMm = item.long("z_mm") ?: 0L,
)

private fun footprintsOf(layer: JsonObject): FootprintLayer =
    FootprintLayer(layer.long("version") ?: 0L, layer.objects("items").map(::footprintOf), provenanceOf(layer))

private fun instanceOf(item: JsonObject): SceneInstance = SceneInstance(
    id = item.requiredString("id"),
    type = item.requiredString("type"),
    xMm = item.requiredLong("x_mm"),
    yMm = item.requiredLong("y_mm"),
    zMm = item.long("z_mm") ?: 0L,
    rotationDeg = item.double("rotation_deg") ?: 0.0,
    heightMm = item.long("height_mm"),
    crownRadiusMm = item.long("crown_radius_mm"),
    tags = item.strings("tags"),
    provenance = ProvenanceKind.entries.firstOrNull { it.name.equals(item.string("provenance"), ignoreCase = true) } ?: ProvenanceKind.PROPOSED,
)

private fun instancesOf(layer: JsonObject): InstanceLayer =
    InstanceLayer(layer.long("version") ?: 0L, layer.objects("items").map(::instanceOf), provenance = provenanceOf(layer))

private fun waterOf(layer: JsonObject): WaterLayer = WaterLayer(
    version = layer.long("version") ?: 0L,
    ponds = layer.objects("ponds").map { pond ->
        Pond(pond.requiredString("id"), pond.requiredLong("level_mm"), pond.objects("tiles").map { PondTile(it.requiredInt("col"), it.requiredInt("row"), it.requiredLong("depth_mm")) })
    },
    flow = layer.objects("flow").map { line ->
        FlowLine(
            line.requiredString("id"),
            line.array("points_mm").mapNotNull(::pairOf),
            line.requiredDouble("quantity"),
            line.requiredString("unit"),
            line.requiredString("kind"),
        )
    },
    provenance = provenanceOf(layer),
)

private fun boundariesOf(layer: JsonObject): BoundaryLayer = BoundaryLayer(
    version = layer.long("version") ?: 0L,
    polygons = layer.objects("polygons").map { BoundaryPolygon(it.requiredString("id"), it.requiredString("kind"), it.array("points_mm").mapNotNull(::pairOf)) },
    provenance = provenanceOf(layer),
)

private fun gridOf(grid: JsonObject, sceneTileFeet: Double): GridSpec {
    val tileFeet = grid.double("tile_feet") ?: grid.double("tile_mm")?.let { it / MM_PER_FOOT } ?: sceneTileFeet
    return GridSpec(grid.string("shape") ?: "square", tileFeet, grid.requiredInt("cols"), grid.requiredInt("rows"))
}

private fun annotationsOf(layer: JsonObject): AnnotationLayer = AnnotationLayer(
    version = layer.long("version") ?: 0L,
    items = layer.objects("items").map {
        Annotation(it.requiredString("ref_kind"), it.string("ref_id"), it.requiredString("kind"), it.string("text") ?: "", it.string("source"), it.double("value"))
    },
)

private fun stackLevelOf(level: JsonObject): StackLevel = StackLevel(level.requiredInt("stack_level"), level.long("z_base_mm"), level.string("label"))
