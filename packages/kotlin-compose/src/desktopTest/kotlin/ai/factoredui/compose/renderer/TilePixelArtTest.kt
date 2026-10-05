package ai.factoredui.compose.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TilePixelArtTest {

    @Test
    fun everyTreeVariantIsARectangleWithAnOutlinedSilhouette() {
        TREE_VARIANT_COUNT.let { count ->
            (0 until count).forEach { variant ->
                val rows = treeSprite(variant).rows
                assertTrue(rows.map { it.length }.toSet().size == 1, "variant $variant rows share one width")
                rows.filter { row -> row.any { it != '.' } }.forEach { row ->
                    val solid = row.filter { it != '.' }
                    if (solid.length > 1 && row.none { it == 'W' }) {
                        assertEquals('O', row.first { it != '.' }, "variant $variant row starts with an outline pixel: $row")
                        assertEquals('O', row.last { it != '.' }, "variant $variant row ends with an outline pixel: $row")
                    }
                }
            }
        }
    }

    @Test
    fun everyTreeStandsOnATrunkAtItsHorizontalCentre() {
        (0 until TREE_VARIANT_COUNT).forEach { variant ->
            val rows = treeSprite(variant).rows
            val base = rows.last()
            assertTrue('W' in base, "variant $variant ends in trunk wood")
            val centre = (base.length - 1) / 2
            assertEquals('W', base[centre], "the trunk sits on the centre column")
        }
    }

    @Test
    fun treeVariantsAreChosenDeterministicallyAndAllAppearAcrossAParcel() {
        assertEquals(treeVariantFor(3, 7), treeVariantFor(3, 7))
        val seen = (0 until 13).flatMap { col -> (0 until 26).map { row -> treeVariantFor(col, row) } }.toSet()
        assertEquals((0 until TREE_VARIANT_COUNT).toSet(), seen)
    }

    @Test
    fun theSpritesDifferFromEachOtherSoTheTreesDoNotLookStamped() {
        assertNotEquals(treeSprite(0).rows, treeSprite(1).rows)
        assertNotEquals(treeSprite(1).rows, treeSprite(2).rows)
    }

    @Test
    fun theSheepHasTwoFramesOfEqualSizeThatDifferInTheirLegs() {
        val first = critterSprite("sheep", frame = 0)
        val second = critterSprite("sheep", frame = 1)
        assertEquals(first.rows.size, second.rows.size)
        assertEquals(first.rows.map { it.length }, second.rows.map { it.length })
        assertNotEquals(first.rows, second.rows)
    }

    @Test
    fun anUnknownCritterHasNoSprite() {
        assertEquals(null, critterSpriteOrNull("dragon", 0))
    }

    @Test
    fun theFrameAdvancesWithThePhaseAndWrapsAroundTwoFrames() {
        assertEquals(listOf(0, 0, 1, 1, 0, 0), (0..5).map { critterFrameFor(it) })
    }

    @Test
    fun aroundOneInThreePaddockTilesHoldAnAnimal() {
        val holders = (0 until 13).flatMap { col -> (0 until 26).map { row -> critterOnTile(col, row) } }.count { it }
        val share = holders.toFloat() / (13 * 26)
        assertTrue(share in 0.2f..0.45f, "share of tiles with an animal: $share")
        assertEquals(critterOnTile(4, 9), critterOnTile(4, 9))
    }

    @Test
    fun theWaterShimmerRepeatsAfterItsCycleAndMovesBetweenFrames() {
        assertEquals(shimmerOffsets(0), shimmerOffsets(SHIMMER_CYCLE))
        assertNotEquals(shimmerOffsets(0), shimmerOffsets(1))
        assertEquals(SHIMMER_DASHES, shimmerOffsets(3).size)
    }

    @Test
    fun theBrickCourseAlternatesToneEveryThirdPixelRow() {
        val tones = (0 until 12).map { brickTone(it) }
        assertEquals(tones.take(6), tones.drop(6).take(6), "the pattern repeats")
        assertTrue(tones.toSet().size == 2, "two tones alternate")
        assertEquals(brickTone(0), brickTone(2))
        assertNotEquals(brickTone(2), brickTone(3))
    }

    @Test
    fun theTileHashIsStableAndSpreadsAcrossTheGrid() {
        assertEquals(tileHash(5, 8), tileHash(5, 8))
        val hashes = (0 until 13).flatMap { col -> (0 until 26).map { row -> tileHash(col, row) } }.toSet()
        assertTrue(hashes.size > 100, "hashes are not clumped: ${hashes.size}")
    }
}
