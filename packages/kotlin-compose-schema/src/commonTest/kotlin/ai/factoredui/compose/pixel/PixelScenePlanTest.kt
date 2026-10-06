package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.MM_PER_FOOT
import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.layout.TileInstance
import ai.factoredui.compose.schema.TileSprite
import ai.factoredui.compose.schema.TilemapUse
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixelScenePlanTest {

    private val scale = EMBEDDED_PIXEL_ATLAS.manifest.scales.getValue(32)
    private val swaps = EMBEDDED_PIXEL_ATLAS.manifest.swaps
    private val roomy = PixelCosts(isGroundTextured = true, isWaterAnimated = false, decorationLimit = 1000)

    private val uses = listOf(
        TilemapUse("paddock", "Paddock", sprite = TileSprite.FENCE, critter = "sheep"),
        TilemapUse("hoop_house", "Hoop house", sprite = TileSprite.ARCH),
        TilemapUse("commons_building", "Commons", sprite = TileSprite.BLOCK),
        TilemapUse("woodland_tree", "Woodland", sprite = TileSprite.TREE),
        TilemapUse("van_pad", "Van pad", sprite = TileSprite.FLAT),
        TilemapUse("lidar_tree", "Lidar tree", sprite = TileSprite.TREE),
        TilemapUse("pine", "Pine", sprite = TileSprite.TREE),
        TilemapUse("tractor", "Tractor", color = "#3060D0", sprite = TileSprite.BLOCK),
        TilemapUse("person", "Person", sprite = TileSprite.BLOCK),
    ).associateBy { it.id }

    private fun plan(footprints: List<TileFootprint> = emptyList(), instances: List<TileInstance> = emptyList(), costs: PixelCosts = roomy, turns: Int = 0) =
        planPixelSprites(PixelPlanInput(footprints, instances, uses, scale, swaps, tileFeet = 5.0, cols = 40, rows = 40, quarterTurns = turns, costs = costs))

    private fun tree(id: String, type: String, x: Float, y: Float, crownMm: Double?) =
        TileInstance(id, type, x * 5 * MM_PER_FOOT, (40 - y) * 5 * MM_PER_FOOT, heightMm = null, crownRadiusMm = crownMm, rotationDeg = 0.0)

    @Test
    fun aFiftyByTwentyFiveFootHoopHouseFootprintTakesTheLargestHoopHouseThatFitsFacingAlongItsLength() {
        val draw = plan(listOf(TileFootprint("h", "hoop_house", 4, 6, 10, 5))).single()
        assertEquals("hoop_house/48x24/SE", draw.sprite.name)
        assertEquals(GroundPoint(9f, 8.5f), draw.ground)
    }

    @Test
    fun aFootprintLongerDownTheRowsFacesAlongTheRows() {
        assertEquals("hoop_house/48x24/SW", plan(listOf(TileFootprint("h", "hoop_house", 4, 6, 5, 10))).single().sprite.name)
    }

    @Test
    fun aFootprintSmallerThanEverySizeStillDrawsTheSmallest() {
        assertEquals("commons/20x16/SE", plan(listOf(TileFootprint("c", "commons_building", 0, 0, 1, 1))).single().sprite.name)
    }

    @Test
    fun aPaddockIsFencedOnAllFourEdgesWithCowsInside() {
        val draws = plan(listOf(TileFootprint("p", "paddock", 10, 10, 5, 5)))
        val fences = draws.filter { it.sprite.cls == "fence" }
        val cows = draws.filter { it.sprite.cls == "cow" }
        assertEquals(20, fences.size, "four 25 ft edges of 5 ft segments")
        assertEquals(setOf("fence/SE", "fence/SW"), fences.map { it.sprite.name }.toSet())
        assertEquals(2, cows.size, "one cow per 300 sq ft of 625")
        assertTrue(cows.all { it.ground.x > 10f && it.ground.x < 15f && it.ground.y > 10f && it.ground.y < 15f })
    }

    @Test
    fun theDecorationLimitDropsCowsAndExtraWoodlandTreesButNeverTheFenceOrTheFirstTree() {
        val starved = roomy.copy(decorationLimit = 0)
        val draws = plan(listOf(TileFootprint("p", "paddock", 0, 0, 5, 5), TileFootprint("w", "woodland_tree", 10, 10, 5, 5)), costs = starved)
        assertEquals(0, draws.count { it.sprite.cls == "cow" })
        assertEquals(20, draws.count { it.sprite.cls == "fence" })
        assertEquals(1, draws.count { it.sprite.cls.startsWith("tree") })
        assertEquals(4, plan(listOf(TileFootprint("w", "woodland_tree", 10, 10, 5, 5))).count { it.sprite.cls.startsWith("tree") })
    }

    @Test
    fun aTreeInstanceTakesTheCrownSizeNearestItsMeasuredRadiusAndItsClassFromTheType() {
        val draws = plan(instances = listOf(tree("a", "lidar_tree", 5f, 5f, 6000.0), tree("b", "lidar_tree", 9f, 5f, 1000.0), tree("c", "pine", 13f, 5f, null)))
        val byId = draws.associateBy { it.instanceId }
        assertEquals("tree-unknown/XL", byId.getValue("a").sprite.name)
        assertEquals("tree-unknown/S", byId.getValue("b").sprite.name)
        assertEquals("tree-conifer/M", byId.getValue("c").sprite.name)
    }

    @Test
    fun anInstanceStaysAtItsMillimetrePosition() {
        val ground = plan(instances = listOf(tree("a", "lidar_tree", 5.3f, 7.9f, 2000.0))).single().ground
        assertTrue(abs(ground.x - 5.3f) < 1e-4f && abs(ground.y - 7.9f) < 1e-4f, "$ground")
    }

    @Test
    fun nearerThingsDrawLaterSoATreeInFrontOfTheCommonsCoversIt() {
        val draws = plan(listOf(TileFootprint("c", "commons_building", 4, 4, 4, 4)), listOf(tree("front", "lidar_tree", 7.5f, 9f, 2000.0), tree("back", "lidar_tree", 4.5f, 3f, 2000.0)))
        assertEquals(listOf("back", null, "front"), draws.map { it.instanceId })
    }

    @Test
    fun aTractorTakesTheRampNearestItsUseColourAndAPersonOneOfTheShirts() {
        val tractor = TileInstance("t1", "tractor", 1000.0, 1000.0, null, null, 90.0)
        val person = TileInstance("p1", "person", 2000.0, 2000.0, null, null, 0.0)
        val byId = plan(instances = listOf(tractor, person)).associateBy { it.instanceId }
        assertEquals("tractor/idle/SW", byId.getValue("t1").sprite.name)
        assertEquals("Blue", byId.getValue("t1").ramp)
        assertTrue(byId.getValue("p1").ramp in setOf("0", "1", "2"))
    }

    @Test
    fun aVanPadParksAVan() {
        assertEquals("van/SE", plan(listOf(TileFootprint("v", "van_pad", 2, 2, 5, 5))).single().sprite.name)
    }

    @Test
    fun theSpriteBoxHangsFromItsAnchorAtTheRoundedGroundPoint() {
        val sprite = scale.byName.getValue("tree-unknown/M")
        assertEquals(SpriteBox(100 - sprite.anchorX, 51 - sprite.anchorY, sprite.width, sprite.height), spriteBoxAt(sprite, 100.4f, 50.6f))
    }

    @Test
    fun aTreeBehindABuildingIsStillPickedThroughIt() {
        val draws = plan(listOf(TileFootprint("c", "commons_building", 10, 10, 4, 4)), listOf(tree("behind", "lidar_tree", 11f, 11f, 2000.0)))
        val toContent: (GroundPoint) -> Pair<Float, Float> = { (it.x - it.y) * 16f to (it.x + it.y) * 8f }
        val (x, y) = toContent(GroundPoint(11f, 11f))
        assertEquals(listOf("behind", null), draws.map { it.instanceId })
        assertEquals("behind", pickPixelInstance(draws, toContent, x, y - 5f, 0f))
    }

    @Test
    fun aPickReturnsTheFrontmostInstanceWhoseSpriteCoversThePointAndIgnoresFootprints() {
        val draws = plan(listOf(TileFootprint("c", "commons_building", 30, 0, 2, 2)), listOf(tree("back", "lidar_tree", 10f, 10f, 4000.0), tree("front", "lidar_tree", 10.3f, 10.3f, 4000.0)))
        val toContent: (GroundPoint) -> Pair<Float, Float> = { (it.x - it.y) * 16f to (it.x + it.y) * 8f }
        val (x, y) = toContent(GroundPoint(10.15f, 10.15f))
        assertEquals("front", pickPixelInstance(draws, toContent, x, y - 10f, 0f))
        assertNull(pickPixelInstance(draws, toContent, x + 500f, y, 0f))
        val (commonsX, commonsY) = toContent(GroundPoint(31f, 1f))
        assertNull(pickPixelInstance(draws, toContent, commonsX, commonsY - 5f, 0f))
    }
}
