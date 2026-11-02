package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private val COLUMNS = listOf(
    mapOf("id" to "x_mm", "label" to "x", "unit" to "mm"),
    mapOf("id" to "y_mm", "label" to "y", "unit" to "mm"),
    mapOf("id" to "width_mm", "label" to "width", "unit" to "mm"),
    mapOf("id" to "depth_mm", "label" to "depth", "unit" to "mm"),
    mapOf("id" to "level", "label" to "level"),
)

private val INSTANCE_BASE_COLUMNS = listOf(
    mapOf("id" to "length_mm", "label" to "length", "unit" to "mm"),
    mapOf("id" to "elevation_mm", "label" to "elev.", "unit" to "mm"),
)

private fun instanceColumns(expansion: Expansion): List<Map<String, Any?>> =
    INSTANCE_BASE_COLUMNS + expansion.instances.flatMap { it.fields.keys }.distinct().sorted().map { key ->
        if (key.endsWith("_mm")) mapOf("id" to key, "label" to humanize(key.removeSuffix("_mm")), "unit" to "mm") else mapOf("id" to key, "label" to humanize(key))
    }

private val DISPLAY = mapOf("units" to mapOf("mm" to mapOf("format" to mapOf("scale" to 1, "decimals" to 0), "label" to "mm")))

private const val FIGURE_COLUMN_DP = 50
private const val LEVEL_COLUMN_DP = 44

private val STATIC_OVERRIDES = mapOf(
    "x_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "y_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "width_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "depth_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "level" to mapOf("format" to mapOf("scale" to 1, "decimals" to 0), "width" to LEVEL_COLUMN_DP),
)

private fun overridesOf(columns: List<Map<String, Any?>>): Map<String, Map<String, Any?>> = columns.associate { column ->
    val id = column["id"] as String
    id to (STATIC_OVERRIDES[id] ?: if (id.endsWith("_mm")) mapOf("width" to FIGURE_COLUMN_DP) else mapOf("format" to mapOf("scale" to 1, "decimals" to 0), "width" to LEVEL_COLUMN_DP))
}

private fun objectRow(item: ExpandedObject) = mapOf(
    "id" to item.id, "label" to item.id, "object_ids" to listOf(item.id),
    "values" to mapOf("x_mm" to item.xMm, "y_mm" to item.yMm, "width_mm" to item.widthMm, "depth_mm" to item.depthMm, "level" to item.level),
)

private fun instanceRow(item: ExpandedInstance) = mapOf(
    "id" to "${item.id}/${formatMm(item.elevationMm)}", "label" to "${item.id} · ${formatMm(item.rotationDeg)}°", "object_ids" to listOf(item.id),
    "values" to mapOf("length_mm" to item.lengthMm, "elevation_mm" to item.elevationMm) + item.fields,
)

private fun tableNode(id: String, label: String, columns: List<Map<String, Any?>>, rows: List<Map<String, Any?>>): JsonObject =
    specNode(
        id, "table",
        mapOf(
            "view" to mapOf("id" to id, "label" to label, "columns" to columns, "rows" to rows),
            "display" to DISPLAY,
            "columns" to overridesOf(columns),
            "label_header" to "Object",
        ),
    )

internal fun objectTableNode(expansion: Expansion): JsonObject =
    tableNode("objects", "Every object, in millimetres", COLUMNS, expansion.objects.map(::objectRow))

internal fun instanceTableNode(expansion: Expansion): JsonObject? =
    if (expansion.instances.isEmpty()) null
    else tableNode("instances", "Every placed length, in millimetres", instanceColumns(expansion), expansion.instances.map(::instanceRow))
