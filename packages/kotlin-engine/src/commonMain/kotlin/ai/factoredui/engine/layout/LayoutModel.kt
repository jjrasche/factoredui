package ai.factoredui.engine.layout

import ai.factoredui.compose.schema.DeviceClass
import ai.factoredui.engine.factors.Factor
import kotlinx.serialization.json.JsonObject

/** The window's pixels as drawn; one pixel is one dp, so the runner captures at device scale 1. */
interface PixelGrid {
    val width: Int
    val height: Int
    fun rgbAt(x: Int, y: Int): Int
}

/** One reading of the screen: the /tree the control port gave and the pixels drawn at the same moment, at a sheet stop if the page has a sheet. */
data class LayoutCapture(val stop: String?, val tree: JsonObject, val pixels: PixelGrid?)

data class LayoutPage(
    val page: String,
    val device: DeviceClass,
    val widthDp: Double,
    val heightDp: Double,
    val groundHex: String,
    val captures: List<LayoutCapture>,
)

enum class LayoutVerdict { PASS, FAIL, BLIND }

data class LayoutReading(
    val factor: Factor,
    val threshold: Double,
    val higherIsBetter: Boolean,
    val verdict: LayoutVerdict,
    val note: String?,
)
