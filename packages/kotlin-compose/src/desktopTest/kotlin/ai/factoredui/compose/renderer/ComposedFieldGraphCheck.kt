package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ComposedFieldGraphCheck {

    private fun fieldNode(id: String, x: Int, y: Int, label: String) = SpecNode(
        id = id,
        type = SpecNodeType.TEXT,
        props = mapOf(
            "x" to SpecValue.NumberValue(x.toDouble()),
            "y" to SpecValue.NumberValue(y.toDouble()),
            "value" to SpecValue.StringValue(label),
        ),
    )

    private fun edge(from: String, to: String) = SpecValue.ObjectValue(
        mapOf("from" to SpecValue.StringValue(from), "to" to SpecValue.StringValue(to)),
    )

    private val field = SpecNode(
        id = "field",
        type = SpecNodeType.CANVAS,
        props = mapOf("edges" to SpecValue.ArrayValue(listOf(edge("claim-a", "entity"), edge("claim-b", "entity")))),
        children = listOf(
            fieldNode("claim-a", x = 20, y = 20, label = "Knee pain"),
            fieldNode("claim-b", x = 200, y = 40, label = "Lateral lurch"),
            fieldNode("entity", x = 110, y = 200, label = "Knee"),
        ),
    )

    @Test
    fun theFieldGraphComposesFromAtomsAndRendersAsAField() = runComposeUiTest {
        val check = SpecVisualCheck(this)
        check.render(field, viewport = 400.dp)
        check.assertRenderedToPixels()
        check.assertPresent("claim-a")
        check.assertPresent("claim-b")
        check.assertPresent("entity")
    }

    @Test
    fun composedNodesLandAtTheirDeclaredCoordinates() = runComposeUiTest {
        val check = SpecVisualCheck(this)
        check.render(field, viewport = 400.dp)
        check.assertAbove("claim-a", "entity")
        check.assertLeftOf("claim-a", "claim-b")
    }
}
