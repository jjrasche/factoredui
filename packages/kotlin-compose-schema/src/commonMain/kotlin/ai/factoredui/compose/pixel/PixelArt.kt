package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.TileCoord
import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.schema.TileSprite
import ai.factoredui.compose.schema.TilemapUse

const val WATER_FRAMES = 4

enum class PixelGround(val pattern: String) { GRASS("ground-grass"), PATH("ground-path"), SAND("ground-sand"), DIRT("ground-dirt"), WATER("ground-water") }

enum class PixelStanding { NONE, FENCE, HOOP_HOUSE, COMMONS, SHED, WOODLAND, VAN }

data class PixelArtChoice(val ground: PixelGround?, val standing: PixelStanding, val hasCritters: Boolean = false)

private val SAND_WORDS = listOf("sand", "beach")
private val DIRT_WORDS = listOf("dirt", "gravel", "pad", "yard", "bed")

fun pixelArtFor(use: TilemapUse): PixelArtChoice = when (use.sprite) {
    TileSprite.FLAT -> flatArtFor(use.id)
    TileSprite.WATER -> PixelArtChoice(PixelGround.WATER, PixelStanding.NONE)
    TileSprite.FENCE -> PixelArtChoice(null, PixelStanding.FENCE, hasCritters = use.critter != null)
    TileSprite.ARCH -> PixelArtChoice(null, PixelStanding.HOOP_HOUSE)
    TileSprite.BLOCK -> PixelArtChoice(null, if (use.id.contains("shed")) PixelStanding.SHED else PixelStanding.COMMONS)
    TileSprite.TREE -> PixelArtChoice(null, PixelStanding.WOODLAND)
}

private fun flatArtFor(id: String): PixelArtChoice = when {
    id.contains("van") -> PixelArtChoice(PixelGround.DIRT, PixelStanding.VAN)
    SAND_WORDS.any { id.contains(it) } -> PixelArtChoice(PixelGround.SAND, PixelStanding.NONE)
    DIRT_WORDS.any { id.contains(it) } -> PixelArtChoice(PixelGround.DIRT, PixelStanding.NONE)
    else -> PixelArtChoice(PixelGround.PATH, PixelStanding.NONE)
}

fun pixelGroundTiles(footprints: List<TileFootprint>, uses: Map<String, TilemapUse>): Map<PixelGround, List<TileCoord>> =
    footprints.flatMap { footprint ->
        val ground = uses[footprint.use]?.let(::pixelArtFor)?.ground ?: return@flatMap emptyList()
        footprintTiles(footprint).map { ground to it }
    }.groupBy({ it.first }, { it.second })

fun footprintTiles(footprint: TileFootprint): List<TileCoord> =
    (footprint.row until footprint.row + footprint.height).flatMap { row -> (footprint.col until footprint.col + footprint.width).map { col -> TileCoord(col, row) } }

fun waterFrameFor(phase: Int, isAnimated: Boolean, costs: PixelCosts): Int =
    if (isAnimated && costs.isWaterAnimated) phase % WATER_FRAMES else 0

fun groundPatternName(ground: PixelGround, waterFrame: Int): String =
    "${ground.pattern}/${if (ground == PixelGround.WATER) waterFrame else 0}"
