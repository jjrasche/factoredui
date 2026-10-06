package ai.factoredui.compose.layout

import kotlin.math.sqrt

const val MM_PER_FOOT = 304.8

data class TileFootprint(val id: String, val use: String, val col: Int, val row: Int, val width: Int, val height: Int)

data class TileInstance(
    val id: String,
    val use: String,
    val xMm: Double,
    val yMm: Double,
    val heightMm: Double?,
    val crownRadiusMm: Double?,
    val rotationDeg: Double,
)

fun tileSideMm(tileAreaSquareFeet: Double): Double = sqrt(tileAreaSquareFeet) * MM_PER_FOOT

fun instanceGround(instance: TileInstance, tileSideMm: Double, rows: Int): GroundPoint =
    GroundPoint((instance.xMm / tileSideMm).toFloat(), (rows - instance.yMm / tileSideMm).toFloat())

fun instanceRadiusTiles(instance: TileInstance, tileSideMm: Double): Float =
    ((instance.crownRadiusMm ?: 0.0) / tileSideMm).toFloat()

fun footprintCorners(footprint: TileFootprint): List<GroundPoint> = listOf(
    GroundPoint(footprint.col.toFloat(), footprint.row.toFloat()),
    GroundPoint((footprint.col + footprint.width).toFloat(), footprint.row.toFloat()),
    GroundPoint((footprint.col + footprint.width).toFloat(), (footprint.row + footprint.height).toFloat()),
    GroundPoint(footprint.col.toFloat(), (footprint.row + footprint.height).toFloat()),
)

fun footprintCentre(footprint: TileFootprint): GroundPoint =
    GroundPoint(footprint.col + footprint.width / 2f, footprint.row + footprint.height / 2f)

sealed interface TileDrawable {
    val centre: GroundPoint
}

data class FootprintDrawable(val footprint: TileFootprint, override val centre: GroundPoint) : TileDrawable

data class InstanceDrawable(val instance: TileInstance, override val centre: GroundPoint) : TileDrawable

fun footprintGroundCentre(shape: TileShape, footprint: TileFootprint): GroundPoint =
    if (footprint.width == 1 && footprint.height == 1) tileCenter(shape, footprint.col, footprint.row) else footprintCentre(footprint)

fun drawOrder(shape: TileShape, footprints: List<TileFootprint>, instances: List<TileInstance>, tileSideMm: Double, rows: Int): List<TileDrawable> {
    val footprintDrawables: List<TileDrawable> = footprints.map { FootprintDrawable(it, footprintGroundCentre(shape, it)) }
    val instanceDrawables: List<TileDrawable> = instances.map { InstanceDrawable(it, instanceGround(it, tileSideMm, rows)) }
    return (footprintDrawables + instanceDrawables).sortedWith(compareBy({ it.centre.x + it.centre.y }, { it.centre.x }))
}

fun cellsAsFootprints(cells: List<TileCell>): List<TileFootprint> =
    cells.map { TileFootprint("${it.col},${it.row}", it.use, it.col, it.row, 1, 1) }
