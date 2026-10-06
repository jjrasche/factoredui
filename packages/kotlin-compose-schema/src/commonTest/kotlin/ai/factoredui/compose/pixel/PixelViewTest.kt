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

    private val variants = listOf(PixelVariant("32-5ft", 32, 5.0), PixelVariant("64-5ft", 64, 5.0), PixelVariant("32-25ft", 32, 25.0))

    private fun variantFor(tileFeet: Double) = pixelVariantFor(variants, tileFeet).id

    @Test
    fun theEmbeddedAtlasOffersTheFiveAndTwentyFiveFootRungs() {
        assertEquals(variants.toSet(), pixelVariantsOf(EMBEDDED_PIXEL_ATLAS.manifest).toSet())
    }

    @Test
    fun eachWorldTakesTheRungNearestItsTileFeetByRatio() {
        assertEquals("32-5ft", variantFor(5.0))
        assertEquals("32-5ft", variantFor(9.0))
        assertEquals("32-25ft", variantFor(15.0))
        assertEquals("32-25ft", variantFor(25.0))
        assertEquals("32-25ft", variantFor(125.0))
    }

    @Test
    fun aWorldFinerThanItsRungTakesTheWidestArtAtThatRung() {
        assertEquals("64-5ft", variantFor(1.0))
        assertEquals("32-25ft", variantFor(20.0))
    }

    @Test
    fun aWorldTileIsOneArtTileOnItsOwnRungAndScalesByFeetOffIt() {
        assertEquals(32f, pixelArtScaleFor(variants[0], 5.0, 1f).worldTileArtPx)
        assertEquals(32f, pixelArtScaleFor(variants[2], 25.0, 1f).worldTileArtPx)
        assertEquals(160f, pixelArtScaleFor(variants[2], 125.0, 1f).worldTileArtPx)
        assertEquals(12.8f, pixelArtScaleFor(variants[1], 1.0, 1f).worldTileArtPx)
    }

    @Test
    fun theMaximumZoomIsTwiceTheArtBaseZoomTimesTheDensity() {
        assertEquals(6, pixelArtScaleFor(variants[0], 5.0, 1f).maxZoom)
        assertEquals(4, pixelArtScaleFor(variants[1], 1.0, 1f).maxZoom)
        assertEquals(16, pixelArtScaleFor(variants[0], 5.0, 2.625f).maxZoom)
    }

    @Test
    fun theOpeningZoomIsTheLargestWholeZoomThatFitsTheWholeParcel() {
        assertEquals(3, fitPixelZoom(300f, 150f, 1000f, 700f, 12f, 6))
        assertEquals(4, fitPixelZoom(200f, 100f, 1000f, 424f, 12f, 6))
    }

    @Test
    fun aParcelLargerThanTheViewOpensAtOneTimesNeverLess() {
        assertEquals(1, fitPixelZoom(3120f, 1560f, 1100f, 770f, 12f, 6))
    }

    @Test
    fun aTinyParcelStopsAtTheMaximumZoom() {
        assertEquals(6, fitPixelZoom(32f, 16f, 1000f, 700f, 12f, 6))
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
