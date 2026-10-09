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

fun readSceneViewValue(value: Any?): SceneView = when (value) {
    is String -> readSceneView(value)
    is Map<*, *> -> sceneViewOf(JsonObject(value.entries.associate { (key, field) -> key.toString() to jsonValueOf(field) }))
    null -> throw SceneViewFormatException("no scene is bound to scene_view")
    else -> throw SceneViewFormatException("scene_view must be a scene object or its JSON text, not ${value::class.simpleName}")
}

/** A store decoded from JSON holds whole numbers as doubles, and the reader's whole-number fields refuse "6405.0". */
private fun wholeOrFraction(value: Double): JsonPrimitive =
    if (value.isFinite() && value % 1.0 == 0.0 && kotlin.math.abs(value) < MAX_EXACT_WHOLE) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

private const val MAX_EXACT_WHOLE = 9.0e15

private fun jsonValueOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Double -> wholeOrFraction(value)
    is Float -> wholeOrFraction(value.toDouble())
    is Number -> JsonPrimitive(value.toLong())
    is Map<*, *> -> JsonObject(value.entries.associate { (key, field) -> key.toString() to jsonValueOf(field) })
    is List<*> -> JsonArray(value.map(::jsonValueOf))
    else -> throw SceneViewFormatException("the scene holds a ${value::class.simpleName}, which is not a JSON value")
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

private fun wrongType(key: String, expected: String, found: JsonElement) = SceneViewFormatException("$key must be $expected, not $found")

private fun textOf(element: JsonElement): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun wholeOf(element: JsonElement): Long? = (element as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

private fun numberOf(element: JsonElement): Double? = (element as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

private fun intOf(key: String, whole: Long): Int =
    whole.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: throw SceneViewFormatException("$key must be a whole number that fits an int, not $whole")

private fun JsonObject.value(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

private fun JsonObject.string(key: String): String? = value(key)?.let { textOf(it) ?: throw wrongType(key, "text", it) }

private fun JsonObject.long(key: String): Long? = value(key)?.let { wholeOf(it) ?: throw wrongType(key, "a whole number", it) }

private fun JsonObject.int(key: String): Int? = long(key)?.let { intOf(key, it) }

private fun JsonObject.double(key: String): Double? = value(key)?.let { numberOf(it) ?: throw wrongType(key, "a number", it) }

private fun JsonObject.obj(key: String): JsonObject? = value(key)?.let { it as? JsonObject ?: throw wrongType(key, "an object", it) }

private fun JsonObject.array(key: String): JsonArray = value(key)?.let { it as? JsonArray ?: throw wrongType(key, "an array", it) } ?: JsonArray(emptyList())

private fun JsonObject.objects(key: String): List<JsonObject> =
    array(key).mapIndexed { index, entry -> entry as? JsonObject ?: throw wrongType("$key[$index]", "an object", entry) }

private fun JsonObject.strings(key: String): List<String> =
    array(key).mapIndexed { index, entry -> textOf(entry) ?: throw wrongType("$key[$index]", "text", entry) }

private fun JsonObject.longs(key: String): List<Long> =
    array(key).mapIndexed { index, entry -> wholeOf(entry) ?: throw wrongType("$key[$index]", "a whole number", entry) }

private fun JsonObject.pairs(key: String): List<Pair<Long, Long>> = array(key).mapIndexed { index, entry -> pairOf("$key[$index]", entry) }

private fun JsonObject.requiredLong(key: String): Long = long(key) ?: throw SceneViewFormatException("the scene lacks a whole number $key")

private fun JsonObject.requiredInt(key: String): Int = intOf(key, requiredLong(key))

private fun JsonObject.requiredDouble(key: String): Double = double(key) ?: throw SceneViewFormatException("the scene lacks a number $key")

private fun JsonObject.requiredString(key: String): String = string(key) ?: throw SceneViewFormatException("the scene lacks a text $key")

private fun JsonObject.requiredObject(key: String): JsonObject = obj(key) ?: throw SceneViewFormatException("the scene lacks an object $key")

private fun pairOf(key: String, element: JsonElement): Pair<Long, Long> {
    val values = (element as? JsonArray)?.map(::wholeOf)
    val first = values?.getOrNull(0)
    val second = values?.getOrNull(1)
    if (values?.size != 2 || first == null || second == null) throw wrongType(key, "a pair of whole millimetres", element)
    return first to second
}

private fun windowOf(window: JsonObject) = WindowMm(window.requiredLong("x0"), window.requiredLong("y0"), window.requiredLong("x1"), window.requiredLong("y1"))

private fun typeOf(type: JsonObject): SceneType = SceneType(
    id = type.requiredString("id"),
    label = type.string("label") ?: type.requiredString("id"),
    tags = type.strings("tags"),
    footprintMm = type.value("footprint_mm")?.let { pairOf("footprint_mm", it) },
    heightMm = type.long("height_mm"),
    unitCostUsd = type.double("unit_cost_usd"),
    unitWeightKg = type.double("unit_weight_kg"),
    unitWeightNullReason = type.string("unit_weight_null_reason"),
)

private fun kindByTag(word: String?): ProvenanceKind? = ProvenanceKind.entries.firstOrNull { it.tag == word }

private fun kindByName(word: String?): ProvenanceKind? = ProvenanceKind.entries.firstOrNull { it.name.equals(word, ignoreCase = true) }

private fun unknownProvenance(words: List<String>) =
    SceneViewFormatException("provenance names no kind this reader knows: ${words.joinToString { "'$it'" }}")

private fun layerProvenanceKindOf(fields: JsonObject): ProvenanceKind {
    val tag = fields.string("tag")
    val kind = fields.string("kind")
    if (tag == null && kind == null) return ProvenanceKind.DERIVED
    return kindByTag(tag) ?: kindByName(kind) ?: throw unknownProvenance(listOfNotNull(tag, kind))
}

private fun instanceProvenanceKindOf(item: JsonObject): ProvenanceKind {
    val word = item.string("provenance") ?: return ProvenanceKind.PROPOSED
    return kindByTag(word) ?: kindByName(word) ?: throw unknownProvenance(listOf(word))
}

private fun provenanceOf(layer: JsonObject): Provenance {
    val fields = layer.obj("provenance") ?: return Provenance(ProvenanceKind.DERIVED)
    return Provenance(
        kind = layerProvenanceKindOf(fields),
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
    classIds = layer.longs("class_ids").map { intOf("class_ids", it) },
    provenance = provenanceOf(layer),
)

private fun footprintOf(item: JsonObject): SceneFootprint = SceneFootprint(
    id = item.requiredString("id"),
    type = item.requiredString("type"),
    col = item.requiredInt("col"),
    row = item.requiredInt("row"),
    width = item.int("width") ?: 1,
    height = item.int("height") ?: 1,
    heightMm = item.long("height_mm"),
    stackLevel = item.int("stack_level") ?: 0,
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
    provenance = instanceProvenanceKindOf(item),
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
            line.pairs("points_mm"),
            line.requiredDouble("quantity"),
            line.requiredString("unit"),
            line.requiredString("kind"),
        )
    },
    provenance = provenanceOf(layer),
)

private fun boundariesOf(layer: JsonObject): BoundaryLayer = BoundaryLayer(
    version = layer.long("version") ?: 0L,
    polygons = layer.objects("polygons").map { BoundaryPolygon(it.requiredString("id"), it.requiredString("kind"), it.pairs("points_mm")) },
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
