package ai.factoredui.compose.terrain

import ai.factoredui.compose.scene.DeviceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TerrainCostsTest {

    private val costs = TerrainCosts(gridMinTilePixels = 30f, thinContourMinTilePixels = 20f)

    @Test
    fun theGridAppearsAtItsThresholdAndNotBelow() {
        assertFalse(costs.isGridShown(29.9f))
        assertTrue(costs.isGridShown(30f))
    }

    @Test
    fun thinContoursAppearAtTheirThresholdAndNotBelow() {
        assertFalse(costs.areThinContoursShown(19.9f))
        assertTrue(costs.areThinContoursShown(20f))
    }

    @Test
    fun everyProfileDeclaresItsCostsAndAPhoneSpendsLessThanADesktop() {
        val phone = terrainCostsFor(DeviceProfile.PHONE)
        val desktop = terrainCostsFor(DeviceProfile.DESKTOP)
        assertTrue(phone.gridMinTilePixels > desktop.gridMinTilePixels)
        assertTrue(phone.thinContourMinTilePixels > desktop.thinContourMinTilePixels)
    }

    @Test
    fun aProfileAlwaysGetsTheSameInstanceSoARecordingKeyedOnItStaysValid() {
        assertSame(terrainCostsFor(DeviceProfile.PHONE), terrainCostsFor(DeviceProfile.PHONE))
        assertEquals(DeviceProfile.entries.size, DeviceProfile.entries.map { terrainCostsFor(it) }.toSet().size)
    }
}
