package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.MM_PER_FOOT
import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.layout.TileInstance
import ai.factoredui.compose.layout.instanceGround
import ai.factoredui.compose.layout.rotateGround
import ai.factoredui.compose.layout.rotatedFacing
import ai.factoredui.compose.schema.TileSprite
import ai.factoredui.compose.schema.TilemapUse
import kotlin.math.max
import kotlin.math.roundToInt

private const val FENCE_SEGMENT_FT = 5.0
private const val COW_AREA_FT2 = 300.0
private const val CRITTER_INSET = 0.15
private const val WOODLAND_SPACING_FT = 12.5
private const val WOODLAND_CROWN_PER_SPACING = 0.6
private const val WOODLAND_JITTER = 0.25
private const val BROADLEAF_SHARE_PERCENT = 60
private const val GRAZING_POSES = 2
private const val COW_POSES = 5
private const val WALK_FRAMES = 4
private const val HASH_STEPS = 1000
private const val SHIRT_COUNT = 3

data class PixelDraw(val sprite: PixelSprite, val ramp: String?, val ground: GroundPoint, val depth: Float, val instanceId: String?)

class PixelPlanInput(
    val footprints: List<TileFootprint>,
    val instances: List<TileInstance>,
    val uses: Map<String, TilemapUse>,
    val scale: PixelScale,
    val swaps: Map<String, PixelSwap>,
    val tileFeet: Double,
    val cols: Int,
    val rows: Int,
    val quarterTurns: Int,
    val costs: PixelCosts,
) {
    val sideMm: Double get() = tileFeet * MM_PER_FOOT
}

private class Placement(val spriteName: String, val ground: GroundPoint, val ramp: String? = null, val instanceId: String? = null)

private class DecorationBudget(private var remaining: Int) {
    fun take(): Boolean = (remaining > 0).also { if (it) remaining-- }
}

fun planPixelSprites(input: PixelPlanInput): List<PixelDraw> {
    val budget = DecorationBudget(input.costs.decorationLimit)
    val placements = input.footprints.flatMap { footprintPlacements(it, input, budget) } + input.instances.mapNotNull { instancePlacement(it, input) }
    return placements.mapNotNull { drawOf(it, input) }.sortedWith(compareBy<PixelDraw>({ it.depth }, { rotatedX(it, input) }, { it.sprite.name }, { it.instanceId }))
}

private fun drawOf(placement: Placement, input: PixelPlanInput): PixelDraw? {
    val sprite = input.scale.byName[placement.spriteName] ?: return null
    val rotated = rotateGround(placement.ground, input.quarterTurns, input.cols, input.rows)
    return PixelDraw(sprite, placement.ramp, placement.ground, rotated.x + rotated.y, placement.instanceId)
}

private fun rotatedX(draw: PixelDraw, input: PixelPlanInput): Float = rotateGround(draw.ground, input.quarterTurns, input.cols, input.rows).x

private fun spriteName(cls: String, size: String, facing: String, frame: Int = 0): String =
    listOf(cls, size, facing, if (frame > 0) frame.toString() else "").filter { it.isNotEmpty() }.joinToString("/")

private fun viewFacing(worldFacing: Int, input: PixelPlanInput): String = FACINGS[rotatedFacing(worldFacing, input.quarterTurns)]

private fun footprintPlacements(footprint: TileFootprint, input: PixelPlanInput, budget: DecorationBudget): List<Placement> {
    val choice = input.uses[footprint.use]?.let(::pixelArtFor) ?: return emptyList()
    return when (choice.standing) {
        PixelStanding.NONE -> emptyList()
        PixelStanding.FENCE -> fencePlacements(footprint, input) + if (choice.hasCritters) cowPlacements(footprint, input, budget) else emptyList()
        PixelStanding.HOOP_HOUSE -> listOfNotNull(buildingPlacement(footprint, "hoop_house", input))
        PixelStanding.COMMONS -> listOfNotNull(buildingPlacement(footprint, "commons", input))
        PixelStanding.SHED -> listOfNotNull(buildingPlacement(footprint, "shed", input))
        PixelStanding.WOODLAND -> woodlandPlacements(footprint, input, budget)
        PixelStanding.VAN -> listOf(Placement(spriteName("van", "", viewFacing(extentFacing(footprint), input)), footprintCentre(footprint)))
    }
}

private fun footprintCentre(footprint: TileFootprint): GroundPoint = GroundPoint(footprint.col + footprint.width / 2f, footprint.row + footprint.height / 2f)

private fun extentFacing(footprint: TileFootprint): Int = worldFacingForExtent(footprint.width.toDouble(), footprint.height.toDouble())

private fun buildingPlacement(footprint: TileFootprint, cls: String, input: PixelPlanInput): Placement? {
    val longFt = max(footprint.width, footprint.height) * input.tileFeet
    val shortFt = minOf(footprint.width, footprint.height) * input.tileFeet
    val size = largestSizeThatFits(input.scale.ofClass(cls).map { it.size }, longFt, shortFt) ?: return null
    return Placement(spriteName(cls, size, viewFacing(extentFacing(footprint), input)), footprintCentre(footprint))
}

private fun fencePlacements(footprint: TileFootprint, input: PixelPlanInput): List<Placement> {
    val left = footprint.col.toFloat()
    val top = footprint.row.toFloat()
    val right = (footprint.col + footprint.width).toFloat()
    val bottom = (footprint.row + footprint.height).toFloat()
    return edgeSegments(GroundPoint(left, top), GroundPoint(right, top), footprint.width, input, FACING_ALONG_X) +
        edgeSegments(GroundPoint(left, bottom), GroundPoint(right, bottom), footprint.width, input, FACING_ALONG_X) +
        edgeSegments(GroundPoint(left, top), GroundPoint(left, bottom), footprint.height, input, FACING_ALONG_Y) +
        edgeSegments(GroundPoint(right, top), GroundPoint(right, bottom), footprint.height, input, FACING_ALONG_Y)
}

private fun edgeSegments(start: GroundPoint, end: GroundPoint, tiles: Int, input: PixelPlanInput, worldFacing: Int): List<Placement> {
    val count = max(1, (tiles * input.tileFeet / FENCE_SEGMENT_FT).roundToInt())
    val name = spriteName("fence", "", viewFacing(worldFacing, input))
    return (0 until count).map { index ->
        val along = (index + 0.5f) / count
        Placement(name, GroundPoint(start.x + (end.x - start.x) * along, start.y + (end.y - start.y) * along))
    }
}

private fun hashFraction(text: String, salt: Int): Float = (stableHash(text, salt) % HASH_STEPS) / HASH_STEPS.toFloat()

private fun cowPlacements(footprint: TileFootprint, input: PixelPlanInput, budget: DecorationBudget): List<Placement> {
    val areaFt2 = footprint.width * footprint.height * input.tileFeet * input.tileFeet
    val count = max(1, (areaFt2 / COW_AREA_FT2).toInt())
    return (0 until count).takeWhile { budget.take() }.map { index ->
        val u = CRITTER_INSET + (1 - 2 * CRITTER_INSET) * hashFraction(footprint.id, index * 3 + 1)
        val v = CRITTER_INSET + (1 - 2 * CRITTER_INSET) * hashFraction(footprint.id, index * 3 + 2)
        val pose = stableHash(footprint.id, index * 3 + 3) % COW_POSES
        val facing = viewFacing(stableHash(footprint.id, index + 101) % FACINGS.size, input)
        val name = if (pose < GRAZING_POSES) spriteName("cow", "grazing", facing) else spriteName("cow", "walking", facing, pose % WALK_FRAMES)
        Placement(name, GroundPoint((footprint.col + u * footprint.width).toFloat(), (footprint.row + v * footprint.height).toFloat()))
    }
}

private fun woodlandPlacements(footprint: TileFootprint, input: PixelPlanInput, budget: DecorationBudget): List<Placement> {
    val across = max(1, (footprint.width * input.tileFeet / WOODLAND_SPACING_FT).roundToInt())
    val down = max(1, (footprint.height * input.tileFeet / WOODLAND_SPACING_FT).roundToInt())
    val cellFt = minOf(footprint.width * input.tileFeet / across, footprint.height * input.tileFeet / down)
    val size = treeSizeFor(input.scale, cellFt * WOODLAND_CROWN_PER_SPACING)
    val cells = (0 until down).flatMap { row -> (0 until across).map { col -> col to row } }
    return cells.filterIndexed { index, _ -> index == 0 || budget.take() }.map { (col, row) ->
        val salt = row * across + col
        val jitterX = (hashFraction(footprint.id, salt * 2 + 7) - 0.5f) * 2 * WOODLAND_JITTER.toFloat()
        val jitterY = (hashFraction(footprint.id, salt * 2 + 8) - 0.5f) * 2 * WOODLAND_JITTER.toFloat()
        val x = footprint.col + (col + 0.5f + jitterX) * footprint.width / across
        val y = footprint.row + (row + 0.5f + jitterY) * footprint.height / down
        val treeClass = if (stableHash(footprint.id, salt + 211) % 100 < BROADLEAF_SHARE_PERCENT) TreeClass.BROADLEAF else TreeClass.CONIFER
        Placement(spriteName(treeClass.cls, size, ""), GroundPoint(x, y))
    }
}

private fun instancePlacement(instance: TileInstance, input: PixelPlanInput): Placement? {
    val ground = instanceGround(instance, input.sideMm, input.rows)
    val facing = viewFacing(facingForRotation(instance.rotationDeg), input)
    val use = input.uses[instance.use]
    val type = instance.use
    return when {
        type.contains("cow") -> Placement(spriteName("cow", "walking", facing), ground, instanceId = instance.id)
        type.contains("person") -> Placement(spriteName("person", "", facing), ground, (stableHash(instance.id, 5) % SHIRT_COUNT).toString(), instance.id)
        type.contains("tractor") -> Placement(spriteName("tractor", "idle", facing), ground, tractorRamp(use, input), instance.id)
        use?.sprite == TileSprite.TREE || instance.crownRadiusMm != null -> treePlacement(instance, ground, input)
        use?.sprite == TileSprite.BLOCK -> smallestBuilding("shed", facing, ground, instance, input)
        use?.sprite == TileSprite.ARCH -> smallestBuilding("hoop_house", facing, ground, instance, input)
        else -> null
    }
}

private fun tractorRamp(use: TilemapUse?, input: PixelPlanInput): String? = input.swaps["tractor"]?.let { nearestRamp(use?.color, it) }

private fun treePlacement(instance: TileInstance, ground: GroundPoint, input: PixelPlanInput): Placement {
    val crownFt = instance.crownRadiusMm?.let { it / MM_PER_FOOT }
    return Placement(spriteName(treeClassFor(instance.use).cls, treeSizeFor(input.scale, crownFt), ""), ground, instanceId = instance.id)
}

private fun smallestBuilding(cls: String, facing: String, ground: GroundPoint, instance: TileInstance, input: PixelPlanInput): Placement? {
    val size = largestSizeThatFits(input.scale.ofClass(cls).map { it.size }, 0.0, 0.0) ?: return null
    return Placement(spriteName(cls, size, facing), ground, instanceId = instance.id)
}
