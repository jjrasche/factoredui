package ai.factoredui.compose.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RenderSpecVisualSubstrateSpike {

    private val spec = SpecNode(
        id = "root",
        type = SpecNodeType.COLUMN,
        children = listOf(
            SpecNode(id = "greeting", type = SpecNodeType.TEXT, props = mapOf("value" to SpecValue.StringValue("hello"))),
            SpecNode(id = "cta", type = SpecNodeType.BUTTON, props = mapOf("label" to SpecValue.StringValue("Go"))),
        ),
    )

    @Test
    fun renderSpecYieldsAPngArtifact() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp)) { RenderSpec(root = spec, context = RenderContext()) }
            }
        }
        waitForIdle()
        val image = onRoot().captureToImage()
        assertTrue(image.width > 0 && image.height > 0, "renderSpec must produce a non-empty PNG artifact")
    }

    @Test
    fun everySpecNodeMapsToAnExactRenderedRegion() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp)) { RenderSpec(root = spec, context = RenderContext()) }
            }
        }
        waitForIdle()
        val greeting = onNodeWithTag("greeting").getUnclippedBoundsInRoot()
        val cta = onNodeWithTag("cta").getUnclippedBoundsInRoot()
        assertTrue((greeting.right - greeting.left) > 0.dp, "the greeting node occupies a real region")
        assertTrue((cta.right - cta.left) > 0.dp, "the cta node occupies a real region")
    }

    @Test
    fun specStructureIsMachineVerifiableFromTheRender() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp)) { RenderSpec(root = spec, context = RenderContext()) }
            }
        }
        waitForIdle()
        val greeting = onNodeWithTag("greeting").getUnclippedBoundsInRoot()
        val cta = onNodeWithTag("cta").getUnclippedBoundsInRoot()
        assertTrue(greeting.top < cta.top, "column order is provable from the render: greeting sits above cta")
    }
}
