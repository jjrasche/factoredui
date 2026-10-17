package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private val COLUMNS = listOf(
    mapOf("id" to "x_mm", "label" to "x", "unit" to "mm"),
    mapOf("id" to "y_mm", "label" to "y", "unit" to "mm"),
    mapOf("id" to "width_mm", "label" to "width", "unit" to "mm"),
    mapOf("id" to "depth_mm", "label" to "depth", "unit" to "mm"),
    mapOf("id" to "level", "label" to "level"),
)

private val DISPLAY = mapOf("units" to mapOf("mm" to mapOf("format" to mapOf("scale" to 1, "decimals" to 0), "label" to "mm")))

private const val FIGURE_COLUMN_DP = 50
private const val LEVEL_COLUMN_DP = 44

private val COLUMN_OVERRIDES = mapOf(
    "x_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "y_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "width_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "depth_mm" to mapOf("width" to FIGURE_COLUMN_DP),
    "level" to mapOf("format" to mapOf("scale" to 1, "decimals" to 0), "width" to LEVEL_COLUMN_DP),
)

private fun objectRow(item: ExpandedObject) = mapOf(
    "id" to item.id, "label" to item.id, "object_ids" to listOf(item.id),
    "values" to mapOf("x_mm" to item.xMm, "y_mm" to item.yMm, "width_mm" to item.widthMm, "depth_mm" to item.depthMm, "level" to item.level),
)

private fun instanceRow(item: ExpandedInstance) = mapOf(
    "id" to item.id, "label" to "${item.id} · ${formatMm(item.rotationDeg)}°", "object_ids" to listOf(item.id),
    "values" to mapOf("x_mm" to item.xMm, "y_mm" to item.yMm, "width_mm" to item.lengthMm, "depth_mm" to null, "level" to null),
)

internal fun objectTableNode(expansion: Expansion): JsonObject =
    specNode(
        "objects", "table",
        mapOf(
            "view" to mapOf("id" to "objects", "label" to "Every object, in millimetres", "columns" to COLUMNS, "rows" to expansion.objects.map(::objectRow) + expansion.instances.map(::instanceRow)),
            "display" to DISPLAY,
            "columns" to COLUMN_OVERRIDES,
            "label_header" to "Object",
        ),
    )
