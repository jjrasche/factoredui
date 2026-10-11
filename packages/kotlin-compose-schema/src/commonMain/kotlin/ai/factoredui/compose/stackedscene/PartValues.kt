package ai.factoredui.compose.stackedscene

import ai.factoredui.compose.scene.Annotation
import ai.factoredui.compose.scene.SceneRefKind
import ai.factoredui.compose.scene.SceneView

data class PartValue(val value: Double, val text: String, val source: String?)

private fun isPartFigure(annotation: Annotation, channel: String): Boolean =
    annotation.kind == channel &&
        annotation.refKind.equals(SceneRefKind.FOOTPRINT.name, ignoreCase = true) &&
        annotation.refId != null &&
        annotation.value?.isFinite() == true

/** The channel is whatever annotation kind the spec names; a part with no finite figure on it is absent, never zero. */
fun partValues(scene: SceneView, channel: String): Map<String, PartValue> {
    val found = LinkedHashMap<String, PartValue>()
    scene.annotations?.items.orEmpty()
        .filter { isPartFigure(it, channel) }
        .forEach { found[it.refId!!] = PartValue(it.value!!, it.text, it.source) }
    return found
}
