package ai.factoredui.compose.scene

enum class ProvenanceKind { MEASURED, DERIVED, PROPOSED }

data class Provenance(val kind: ProvenanceKind, val source: String? = null, val asOf: String? = null, val note: String? = null)

data class WindowMm(val x0: Long, val y0: Long, val x1: Long, val y1: Long)

data class SceneType(val id: String, val label: String, val tags: List<String> = emptyList(), val footprintMm: Pair<Long, Long>? = null, val heightMm: Long? = null)

data class GroundLayer(
    val version: Long,
    val cols: Int,
    val rows: Int,
    val heightsMm: List<Long>,
    val cutFillMm: List<Long>? = null,
    val datum: String? = null,
    val provenance: Provenance,
) {
    val vertexCols: Int get() = cols + 1
    val vertexRows: Int get() = rows + 1

    fun heightAt(vertexCol: Int, vertexRow: Int): Long = heightsMm[vertexRow * vertexCols + vertexCol]
}

data class SurfaceClass(val id: Int, val name: String)

data class SurfaceLayer(val version: Long, val cols: Int, val rows: Int, val classes: List<SurfaceClass>, val classIds: List<Int>, val provenance: Provenance)

data class SceneFootprint(val id: String, val type: String, val col: Int, val row: Int, val width: Int, val height: Int, val heightMm: Long? = null)

data class FootprintLayer(val version: Long, val items: List<SceneFootprint>, val provenance: Provenance)

data class SceneInstance(
    val id: String,
    val type: String,
    val xMm: Long,
    val yMm: Long,
    val zMm: Long,
    val rotationDeg: Double,
    val heightMm: Long?,
    val crownRadiusMm: Long?,
    val tags: List<String>,
    val provenance: ProvenanceKind,
)

data class InstanceCluster(val id: String, val centreMm: Pair<Long, Long>, val count: Int, val meanCrownMm: Long, val classMix: Map<String, Int>)

data class InstanceLayer(
    val version: Long,
    val items: List<SceneInstance>,
    val clusters: List<InstanceCluster> = emptyList(),
    val canopyCoverPercent: List<Int>? = null,
    val provenance: Provenance,
)

data class Pond(val id: String, val levelMm: Long, val tiles: List<PondTile>)

data class PondTile(val col: Int, val row: Int, val depthMm: Long)

data class FlowLine(val id: String, val pointsMm: List<Pair<Long, Long>>, val quantity: Double, val unit: String, val kind: String)

data class WaterLayer(val version: Long, val ponds: List<Pond>, val flow: List<FlowLine>, val provenance: Provenance)

data class BoundaryPolygon(val id: String, val kind: String, val pointsMm: List<Pair<Long, Long>>)

data class BoundaryLayer(val version: Long, val polygons: List<BoundaryPolygon>, val provenance: Provenance)

data class GridSpec(val shape: String, val tileFeet: Double, val cols: Int, val rows: Int)

data class Annotation(val refKind: String, val refId: String?, val kind: String, val text: String, val source: String?)

data class AnnotationLayer(val version: Long, val items: List<Annotation>)

enum class SceneRefKind { GROUND, FOOTPRINT, INSTANCE, FLOW, POND, BOUNDARY }

data class SceneRef(val kind: SceneRefKind, val id: String?, val pointMm: Pair<Long, Long>)

data class SceneView(
    val levelFeet: Int,
    val tileFeet: Double,
    val cols: Int,
    val rows: Int,
    val window: WindowMm,
    val types: List<SceneType>,
    val ground: GroundLayer? = null,
    val surface: SurfaceLayer? = null,
    val footprints: FootprintLayer? = null,
    val instances: InstanceLayer? = null,
    val water: WaterLayer? = null,
    val boundaries: BoundaryLayer? = null,
    val grid: GridSpec? = null,
    val annotations: AnnotationLayer? = null,
) {
    val units: String get() = "mm"
}
