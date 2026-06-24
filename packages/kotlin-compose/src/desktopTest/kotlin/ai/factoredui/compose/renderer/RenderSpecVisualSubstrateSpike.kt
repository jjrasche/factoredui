package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import kotlin.test.Test

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
    fun specRendersToPixelsAndEveryNodeMapsToAnExactRegion() = runComposeUiTest {
        val check = SpecVisualCheck(this)
        check.render(spec)
        check.assertRenderedToPixels()
        check.assertPresent("greeting")
        check.assertOccupiesRegion("greeting")
        check.assertPresent("cta")
        check.assertOccupiesRegion("cta")
        check.assertAbove("greeting", "cta")
    }
}
