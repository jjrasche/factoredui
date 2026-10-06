package ai.factoredui.compose.scene

import ai.factoredui.compose.layout.MM_PER_FOOT
import ai.factoredui.compose.schema.DEFAULT_TILEMAP_SIZE
import ai.factoredui.compose.schema.resolveTileArea
import ai.factoredui.compose.schema.resolveTilemapSize
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class LookEntry(val color: String? = null, val sprite: String? = null, val height: Double? = null, val critter: String? = null, val image: String? = null)

typealias LookTable = Map<String, LookEntry>

data class AdaptedScene(val scene: SceneView, val look: LookTable, val dropped: List<String>)

private val LIVE_PROVENANCE = Provenance(ProvenanceKind.DERIVED, source = "world engine render props", note = "the as-of date is not carried by render props")

fun roundHalfUp(value: Double): Long = floor(value + 0.5).toLong()

private fun contentVersion(items: List<Any>): Long = items.hashCode().toLong()

private fun millimetres(value: Any?): Long? = (value as? Number)?.toDouble()?.let(::roundHalfUp)

fun adaptRenderProps(props: Map<String, Any?>): AdaptedScene {
    val dropped = mutableListOf<String>()
    val cols = resolveTilemapSize(props["cols"], DEFAULT_TILEMAP_SIZE)
    val rows = resolveTilemapSize(props["rows"], DEFAULT_TILEMAP_SIZE)
    val tileFeet = sqrt(resolveTileArea(props["tile_area"]))
    val tileMm = roundHalfUp(tileFeet * MM_PER_FOOT)
    val uses = (props["uses"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
    val scene = SceneView(
        levelFeet = tileFeet.roundToInt(),
        tileFeet = tileFeet,
        cols = cols,
        rows = rows,
        window = WindowMm(0, 0, cols * tileMm, rows * tileMm),
        types = uses.mapNotNull { sceneTypeOf(it) },
        ground = groundOf(props, cols, rows),
        footprints = footprintsOf(props, dropped),
        instances = instancesOf(props, dropped),
        grid = GridSpec((props["shape"] as? String) ?: "square", tileFeet, cols, rows),
    )
    return AdaptedScene(scene, lookTableOf(uses), dropped)
}

private fun sceneTypeOf(fields: Map<*, *>): SceneType? {
    val id = fields["id"] as? String ?: return null
    return SceneType(id, fields["label"] as? String ?: id)
}

private fun lookTableOf(uses: List<Map<*, *>>): LookTable =
    uses.mapNotNull { fields ->
        val id = fields["id"] as? String ?: return@mapNotNull null
        id to LookEntry(fields["color"] as? String, fields["sprite"] as? String, (fields["height"] as? Number)?.toDouble(), fields["critter"] as? String, fields["image"] as? String)
    }.toMap()

private fun footprintsOf(props: Map<String, Any?>, dropped: MutableList<String>): FootprintLayer? {
    val records = (props["cells"] as? List<*>).orEmpty() + (props["footprints"] as? List<*>).orEmpty()
    if (records.isEmpty()) return null
    val items = records.mapIndexedNotNull { index, entry ->
        val fields = entry as? Map<*, *>
        val col = (fields?.get("col") as? Number)?.toInt()
        val row = (fields?.get("row") as? Number)?.toInt()
        val type = (fields?.get("use") as? String)
        if (fields == null || col == null || row == null || type == null) {
            dropped.add("footprint $index is not a record with col, row and use")
            return@mapIndexedNotNull null
        }
        SceneFootprint(fields["id"] as? String ?: "$col,$row", type, col, row, (fields["width"] as? Number)?.toInt() ?: 1, (fields["height"] as? Number)?.toInt() ?: 1)
    }
    return FootprintLayer(contentVersion(items), items, LIVE_PROVENANCE)
}

private fun instancesOf(props: Map<String, Any?>, dropped: MutableList<String>): InstanceLayer? {
    val records = (props["instances"] as? List<*>).orEmpty()
    if (records.isEmpty()) return null
    val items = records.mapIndexedNotNull { index, entry ->
        val fields = entry as? Map<*, *>
        val id = fields?.get("id") as? String
        val type = fields?.get("type") as? String
        val xMm = millimetres(fields?.get("x_mm"))
        val yMm = millimetres(fields?.get("y_mm"))
        if (fields == null || id == null || type == null || xMm == null || yMm == null) {
            dropped.add("instance $index lacks an id, a type or a numeric position")
            return@mapIndexedNotNull null
        }
        SceneInstance(
            id = id,
            type = type,
            xMm = xMm,
            yMm = yMm,
            zMm = millimetres(fields["z_mm"]) ?: 0L,
            rotationDeg = (fields["rotation_deg"] as? Number)?.toDouble() ?: 0.0,
            heightMm = millimetres(fields["height_mm"]),
            crownRadiusMm = millimetres(fields["crown_radius_mm"]),
            tags = emptyList(),
            provenance = if (fields["provenance"] == "measured") ProvenanceKind.MEASURED else ProvenanceKind.PROPOSED,
        )
    }
    return InstanceLayer(contentVersion(items), items, provenance = LIVE_PROVENANCE)
}

private fun groundOf(props: Map<String, Any?>, cols: Int, rows: Int): GroundLayer? {
    val ground = props["ground"] as? Map<*, *> ?: return null
    val heights = (ground["heights_mm"] as? List<*>)?.map { millimetres(it) ?: return null } ?: return null
    val expected = (cols + 1) * (rows + 1)
    if (heights.size != expected) return null
    val cutFill = ((props["ground_base"] as? Map<*, *>)?.get("cut_fill_mm") as? List<*>)?.map { millimetres(it) ?: return null }?.takeIf { it.size == expected }
    return GroundLayer(
        version = (ground["version"] as? Number)?.toLong() ?: 0L,
        cols = cols,
        rows = rows,
        heightsMm = heights,
        cutFillMm = cutFill,
        datum = ground["datum"] as? String,
        provenance = Provenance(ProvenanceKind.MEASURED, source = ground["source"] as? String, note = "as-of date is not carried by render props"),
    )
}
