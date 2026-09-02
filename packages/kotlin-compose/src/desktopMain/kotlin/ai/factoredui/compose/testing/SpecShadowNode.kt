package ai.factoredui.compose.testing

import ai.factoredui.compose.schema.SpecNodeType
import androidx.compose.ui.unit.DpRect

/**
 * One rendered spec node: its id/type, its resolved props (bindings applied), and the exact
 * on-screen region the renderer drew it into. A null region means the node did not render.
 *
 * Lives in desktopMain so both the in-test substrate (SpecVisualCheck) and the standalone flow
 * crawler read the same shadow-tree record.
 */
data class SpecShadowNode(
    val id: String,
    val type: SpecNodeType,
    val props: Map<String, Any?>,
    val bounds: DpRect?,
)
