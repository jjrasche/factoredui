package ai.factoredui.compose.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneRenderingTest {

    private val pixelMap = RendererCapability("pixel-map", setOf(SceneLayerKind.GROUND, SceneLayerKind.INSTANCES), setOf(1, 5, 25))
    private val vectorMap = RendererCapability("vector-map", setOf(SceneLayerKind.GROUND, SceneLayerKind.BOUNDARIES), setOf(25, 125, 625))
    private val registry = listOf(pixelMap, vectorMap)

    @Test
    fun aRendererSupportsOnlyTheLevelsAndProfilesItDeclares() {
        val phoneOnly = RendererCapability("tiny", emptySet(), setOf(5), setOf(DeviceProfile.PHONE))
        assertTrue(phoneOnly.supports(DeviceProfile.PHONE, 5))
        assertFalse(phoneOnly.supports(DeviceProfile.DESKTOP, 5))
        assertFalse(phoneOnly.supports(DeviceProfile.PHONE, 25))
    }

    @Test
    fun theLandingLevelIsServedByTheVectorRendererBecauseThePixelOneDoesNotSupportIt() {
        assertEquals("vector-map", selectRenderer(registry, DeviceProfile.PHONE, LANDING_LEVEL_FEET, "pixel-map")?.id)
    }

    @Test
    fun thePreferredRendererWinsWhereMoreThanOneSupportsTheLevel() {
        assertEquals("vector-map", selectRenderer(registry, DeviceProfile.DESKTOP, 25, "vector-map")?.id)
        assertEquals("pixel-map", selectRenderer(registry, DeviceProfile.DESKTOP, 25, "pixel-map")?.id)
        assertEquals("pixel-map", selectRenderer(registry, DeviceProfile.DESKTOP, 25, null)?.id)
    }

    @Test
    fun noRendererIsSelectedWhereNoneSupportsTheLevel() {
        assertNull(selectRenderer(registry, DeviceProfile.DESKTOP, 3, null))
        assertNull(selectRenderer(emptyList(), DeviceProfile.DESKTOP, 25, null))
    }

    @Test
    fun onlyTheLevelsTheVectorMapAloneServesAreVectorLevels() {
        assertEquals(listOf(1, 5, 25, 125, 625).map { it == 625 }, listOf(1, 5, 25, 125, 625).map(::isVectorLevel))
        assertFalse(isVectorLevel(3))
    }

    @Test
    fun theLadderStepsOneRungAtATimeAndStopsAtItsEnds() {
        assertEquals(listOf(625, 125, 25, 5, 1, 1), generateSequence(625) { zoomInLevel(it) }.take(6).toList())
        assertEquals(listOf(1, 5, 25, 125, 625, 625), generateSequence(1) { zoomOutLevel(it) }.take(6).toList())
    }

    @Test
    fun aLevelBetweenRungsStepsToTheNextRungInTheDirectionOfTheZoom() {
        assertEquals(5, zoomInLevel(25))
        assertEquals(25, zoomInLevel(30))
        assertEquals(125, zoomOutLevel(30))
    }

    @Test
    fun theNearestRungOfAnOddTileSizeIsFoundOnTheLadder() {
        assertEquals(25, nearestLadderLevel(30.0))
        assertEquals(125, nearestLadderLevel(100.0))
        assertEquals(1, nearestLadderLevel(0.2))
    }

    @Test
    fun aViewStateReadsItsFieldsRoundsItsCentreAndWrapsTurns() {
        val view = resolveViewState(mapOf("level_feet" to 125, "quarter_turns" to -1, "centre_mm" to listOf(10.5, -0.5), "renderer" to "vector-map"))
        assertEquals(ViewState(125, 3, 11L to 0L, "vector-map"), view)
        assertEquals(1, resolveViewState(mapOf("quarter_turns" to 5)).quarterTurns)
    }

    @Test
    fun aMissingOrUnreadableViewStateLeavesTheLevelForTheCallerToChoose() {
        assertEquals(ViewState(null), resolveViewState(null))
        assertEquals(ViewState(null), resolveViewState(mapOf("level_feet" to 0, "centre_mm" to listOf(1))))
    }

    @Test
    fun aViewStateSurvivesBeingWrittenToABindingAndReadBack() {
        val view = ViewState(25, 2, 5L to 6L, "pixel-map")
        assertEquals(view, resolveViewState(viewStateRecord(view)))
    }
}
