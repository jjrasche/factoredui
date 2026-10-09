package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.Annotation
import ai.factoredui.compose.scene.AnnotationLayer
import ai.factoredui.compose.scene.FootprintLayer
import ai.factoredui.compose.scene.Provenance
import ai.factoredui.compose.scene.ProvenanceKind
import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneType
import ai.factoredui.compose.scene.SceneView
import ai.factoredui.compose.scene.StackLevel
import ai.factoredui.compose.scene.WindowMm

internal val HOUSE_WINDOW = WindowMm(0, 0, 9760, 8540)

internal fun houseScene(
    items: List<SceneFootprint> = emptyList(),
    types: List<SceneType> = emptyList(),
    stackLevels: List<StackLevel> = emptyList(),
    annotations: List<Annotation>? = null,
): SceneView = SceneView(
    levelFeet = 1,
    tileFeet = 2.0,
    cols = 16,
    rows = 14,
    window = HOUSE_WINDOW,
    types = types,
    footprints = FootprintLayer(1, items, Provenance(ProvenanceKind.DERIVED)),
    annotations = annotations?.let { AnnotationLayer(1, it) },
    stackLevels = stackLevels,
)

internal fun part(
    id: String,
    type: String = "t",
    col: Int = 0,
    row: Int = 0,
    width: Int = 1,
    height: Int = 1,
    level: Int = 0,
    zMm: Long = 0,
    heightMm: Long? = null,
): SceneFootprint = SceneFootprint(id, type, col, row, width, height, heightMm = heightMm, stackLevel = level, zMm = zMm)

internal fun shown(footprint: SceneFootprint, isDimmed: Boolean = false): VisiblePart = VisiblePart(footprint, footprint.stackLevel, isDimmed)

internal fun SceneView.footprint(id: String): SceneFootprint = footprints!!.items.first { it.id == id }
