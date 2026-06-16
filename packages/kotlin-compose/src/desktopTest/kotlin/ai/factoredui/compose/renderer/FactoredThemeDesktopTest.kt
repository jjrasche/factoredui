package ai.factoredui.compose.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FactoredThemeDesktopTest {

    @Test
    fun tokensDriveThePrimitiveLook() = runComposeUiTest {
        val tokens = FactoredTokens(colors = FactoredColors(accent = Color(0xFF6C5CE7)))
        val button = SpecNode(
            id = "send",
            type = SpecNodeType.BUTTON,
            props = mapOf("label" to SpecValue.StringValue("Send a message now")),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                FactoredTheme(tokens = tokens) {
                    Box(Modifier.size(300.dp)) {
                        RenderSpec(root = button, context = RenderContext())
                    }
                }
            }
        }
        waitForIdle()

        val pixels = onRoot().captureToImage().toPixelMap()
        val accentHits = (4 until 120 step 4).count { x ->
            val p = pixels[x, 18]
            abs(p.red - 0x6C / 255f) + abs(p.green - 0x5C / 255f) + abs(p.blue - 0xE7 / 255f) < 0.05f
        }
        assertTrue(accentHits > 0, "a primary button under FactoredTheme should paint the token accent somewhere along its fill")
    }
}
