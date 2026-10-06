package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.FlowView
import ai.factoredui.compose.scene.DeviceProfile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PixelViewTest {

    @Test
    fun aFiveFootWorldUsesThirtyTwoPixelArtTilesAtThreeTimes() {
        assertEquals(PixelArtScale(artTileWidth = 32, zoom = 3, maxZoom = 6, worldTileArtPx = 32f), pixelArtScaleFor(5.0, 1f))
    }

    @Test
    fun aCoarserWorldKeepsTheArtAtFiveFeetPerArtTileSoSpritesStayTrueToFeet() {
        val scale = pixelArtScaleFor(25.0, 1f)
        assertEquals(32, scale.artTileWidth)
        assertEquals(160f, scale.worldTileArtPx)
    }

    @Test
    fun aWorldFinerThanFiveFeetSwitchesToSixtyFourPixelArtAtTwoTimes() {
        val scale = pixelArtScaleFor(1.0, 1f)
        assertEquals(64, scale.artTileWidth)
        assertEquals(2, scale.zoom)
        assertEquals(12.8f, scale.worldTileArtPx)
    }

    @Test
    fun theZoomIsAWholeNumberOfScreenPixelsPerArtPixelAtAnyDensity() {
        assertEquals(8, pixelArtScaleFor(5.0, 2.625f).zoom)
        assertEquals(1, pixelArtScaleFor(1.0, 0.25f).zoom)
    }

    @Test
    fun theFitCentresTheFocusOnWholePixels() {
        val view = pixelFitView(100.3f, 40.6f, 801f, 600f, 3)
        assertEquals(3f, view.scale)
        assertEquals(view.translateX, view.translateX.toInt().toFloat())
        assertTrue(abs(view.translateX + 100.3f * 3 - 400.5f) <= 0.5f)
        assertTrue(abs(view.translateY + 40.6f * 3 - 300f) <= 0.5f)
    }

    @Test
    fun aPinchedViewSnapsToTheNearestWholeZoomAroundTheViewCentre() {
        val pinched = FlowView(3.6f, -500f, -200f)
        val snapped = snapPixelView(pinched, 800f, 600f, 6)
        assertEquals(4f, snapped.scale)
        val before = (400f - pinched.translateX) / pinched.scale
        val after = (400f - snapped.translateX) / snapped.scale
        assertTrue(abs(before - after) < 0.5f, "the centre stays on the same content point: $before vs $after")
    }

    @Test
    fun theSnappedZoomStaysBetweenOneAndTheMaximum() {
        assertEquals(1f, snapPixelView(FlowView(0.2f, 0f, 0f), 800f, 600f, 6).scale)
        assertEquals(6f, snapPixelView(FlowView(9f, 0f, 0f), 800f, 600f, 6).scale)
    }

    @Test
    fun aPhoneFreezesWaterAndDrawsFewerDecorationsThanADesktop() {
        val phone = pixelCostsFor(DeviceProfile.PHONE)
        val desktop = pixelCostsFor(DeviceProfile.DESKTOP)
        assertFalse(phone.isWaterAnimated)
        assertTrue(desktop.isWaterAnimated)
        assertTrue(phone.decorationLimit < desktop.decorationLimit)
    }

    @Test
    fun aProfileAlwaysGetsTheSameCostsInstanceSoARecordingKeyedOnItStaysValid() {
        assertSame(pixelCostsFor(DeviceProfile.PHONE), pixelCostsFor(DeviceProfile.PHONE))
        assertEquals(DeviceProfile.entries.size, DeviceProfile.entries.map { pixelCostsFor(it) }.toSet().size)
    }
}
