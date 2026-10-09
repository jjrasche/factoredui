package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView

sealed interface LevelFocus {
    data object AllLevels : LevelFocus

    data class OneLevel(val level: Int) : LevelFocus
}

data class VisiblePart(val footprint: SceneFootprint, val level: Int, val isDimmed: Boolean)

private fun partsOf(scene: SceneView): List<SceneFootprint> = scene.footprints?.items ?: emptyList()

fun levelsPresent(scene: SceneView): List<Int> = partsOf(scene).map { it.stackLevel }.distinct().sorted()

fun topLevel(scene: SceneView): Int? = levelsPresent(scene).lastOrNull()

private fun mostCommonTypeId(parts: List<SceneFootprint>): String? =
    parts.groupingBy { it.type }.eachCount().entries.sortedByDescending { it.value }.firstOrNull()?.key

fun levelName(scene: SceneView, level: Int): String {
    val declared = scene.stackLevels.firstOrNull { it.stackLevel == level }?.label
    if (!declared.isNullOrEmpty()) return declared
    val typeId = mostCommonTypeId(partsOf(scene).filter { it.stackLevel == level })
    return scene.types.firstOrNull { it.id == typeId }?.label ?: "Level $level"
}

private fun isShown(level: Int, focus: LevelFocus, showLower: Boolean): Boolean = when (focus) {
    LevelFocus.AllLevels -> true
    is LevelFocus.OneLevel -> if (showLower) level <= focus.level else level == focus.level
}

private fun isBelowFocus(level: Int, focus: LevelFocus): Boolean = focus is LevelFocus.OneLevel && level < focus.level

private fun hiddenTopLevels(scene: SceneView, count: Int): Set<Int> = levelsPresent(scene).takeLast(count.coerceAtLeast(0)).toSet()

fun visibleParts(scene: SceneView, focus: LevelFocus, showLower: Boolean, hideTopCount: Int = 0): List<VisiblePart> {
    val hidden = hiddenTopLevels(scene, hideTopCount)
    return partsOf(scene)
        .sortedBy { it.stackLevel }
        .filter { it.stackLevel !in hidden && isShown(it.stackLevel, focus, showLower) }
        .map { VisiblePart(it, it.stackLevel, isBelowFocus(it.stackLevel, focus)) }
}

fun fadeUnhighlighted(parts: List<VisiblePart>, highlightIds: Set<String>): List<VisiblePart> {
    if (highlightIds.isEmpty()) return parts
    return parts.map { it.copy(isDimmed = it.isDimmed || it.footprint.id !in highlightIds) }
}
