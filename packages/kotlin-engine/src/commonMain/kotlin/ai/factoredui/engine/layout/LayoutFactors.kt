package ai.factoredui.engine.layout

import ai.factoredui.compose.schema.LayoutThresholds
import ai.factoredui.engine.factors.Factor
import ai.factoredui.engine.factors.FactorTier
import kotlin.math.abs
import kotlin.math.min

private const val LAYOUT_SOURCE = "layout"
private val STOP_ORDER = listOf("peek", "half", "full")
private const val WIDE_BOX_RATIO = 3.0

private class PageView(val page: LayoutPage, val thresholds: LayoutThresholds, val computedAt: String) {
    val screen = Box(0.0, 0.0, page.widthDp, page.heightDp)

    fun reading(name: String, value: Double, threshold: Double, higherIsBetter: Boolean, verdict: LayoutVerdict, note: String? = null) =
        LayoutReading(
            Factor(LAYOUT_SOURCE, page.page, name, FactorTier.STRUCTURAL, value, computedAt, page.page, page.device.wire),
            threshold, higherIsBetter, verdict, note,
        )

    fun graded(name: String, value: Double, threshold: Double, higherIsBetter: Boolean): LayoutReading {
        val passes = if (higherIsBetter) value >= threshold else value <= threshold
        return reading(name, value, threshold, higherIsBetter, if (passes) LayoutVerdict.PASS else LayoutVerdict.FAIL)
    }

    fun blind(name: String, threshold: Double, higherIsBetter: Boolean, note: String) =
        reading(name, Double.NaN, threshold, higherIsBetter, LayoutVerdict.BLIND, note)
}

private fun List<DrawnNode>.shown(): List<DrawnNode> = filter { it.isShown() }

private fun List<DrawnNode>.controls(): List<DrawnNode> = shown().filter { it.type in CONTROL_TYPES || (it.hasAction && it.type != "text") }

private fun List<DrawnNode>.largestMainView(): DrawnNode? =
    shown().filter { it.type in MAIN_VIEW_TYPES }.maxByOrNull { it.box!!.area }

private fun List<DrawnNode>.overlays(): List<DrawnNode> = shown().filter { it.type in OVERLAY_TYPES }

private fun coveredArea(main: Box, overlays: List<DrawnNode>): Double = overlays.sumOf { intersectionArea(main, it.box!!) }

private fun visibleMainShare(view: PageView, nodes: List<DrawnNode>, main: DrawnNode): Double {
    val onScreen = intersectionArea(main.box!!, view.screen)
    return (onScreen - coveredArea(main.box, nodes.overlays())) / view.screen.area
}

private fun nameOfMainShare(capture: LayoutCapture): String = capture.stop?.let { "main_share_$it" } ?: "main_share"

/** The edge of the covering panel by pixels against its edge in the tree; null when there is nothing to disagree about. */
private fun pixelDisagreement(view: PageView, capture: LayoutCapture, nodes: List<DrawnNode>): Result<Double>? {
    val sheet = nodes.overlays().firstOrNull { it.type == "sheet" } ?: return null
    val grid = capture.pixels ?: return Result.failure(IllegalStateException("a sheet covers the page and no screenshot was taken"))
    val fill = sheet.fillHex ?: return Result.failure(IllegalStateException("the sheet reports no fill colour to find in the pixels"))
    val pixelTop = findFillTopEdge(grid, sheet.box!!.x.toInt(), sheet.box.right.toInt(), parseRgb(fill))
    return Result.success(abs(pixelTop - sheet.box.y) / view.page.heightDp)
}

private fun mainShareReadings(view: PageView): List<LayoutReading> = view.page.captures.map { capture ->
    val nodes = flattenTree(capture.tree)
    val name = nameOfMainShare(capture)
    val threshold = view.thresholds.minMainShare
    val main = nodes.largestMainView()
    val disagreement = pixelDisagreement(view, capture, nodes)
    when {
        main == null -> view.blind(name, threshold, true, "no main view is drawn")
        disagreement?.isFailure == true -> view.blind(name, threshold, true, disagreement.exceptionOrNull()!!.message.orEmpty())
        disagreement != null && disagreement.getOrThrow() > view.thresholds.maxPixelDisagreement ->
            view.blind(name, threshold, true, "the tree and the pixels disagree on where the sheet starts by ${(disagreement.getOrThrow() * 100).toInt()} percent of the height")
        else -> view.graded(name, visibleMainShare(view, nodes, main), threshold, true)
    }
}

private fun restingNodes(view: PageView): List<DrawnNode> = flattenTree(view.page.captures.first().tree)

private fun chromeReading(view: PageView): LayoutReading {
    val controlArea = restingNodes(view).controls().sumOf { intersectionArea(it.box!!, view.screen) }
    return view.graded("chrome_share", min(1.0, controlArea / view.screen.area), view.thresholds.maxChromeShare, false)
}

private fun tapTargetReading(view: PageView): List<LayoutReading> {
    val sizes = restingNodes(view).controls().map { min(it.box!!.w, it.box.h) }
    return if (sizes.isEmpty()) emptyList() else listOf(view.graded("smallest_tap_target_dp", sizes.min(), view.thresholds.minTapTargetDp, true))
}

private fun isOneLine(node: DrawnNode): Boolean = node.box!!.w >= node.box.h * WIDE_BOX_RATIO

private fun textLineReading(view: PageView): List<LayoutReading> {
    val lines = restingNodes(view).shown().filter { it.type == "text" && isOneLine(it) }.map { it.box!!.h }
    return if (lines.isEmpty()) emptyList() else listOf(view.graded("smallest_text_line_dp", lines.min(), view.thresholds.minTextLineDp, true))
}

private fun isInsideOverlay(node: DrawnNode, byId: Map<String, DrawnNode>): Boolean {
    var current: DrawnNode? = node
    while (current != null) {
        if (current.type in OVERLAY_TYPES) return true
        current = current.parentId?.let(byId::get)
    }
    return false
}

private fun isAncestor(first: DrawnNode, second: DrawnNode, byId: Map<String, DrawnNode>): Boolean {
    var current = second.parentId?.let(byId::get)
    while (current != null) {
        if (current.id == first.id) return true
        current = current.parentId?.let(byId::get)
    }
    return false
}

private fun overlapReading(view: PageView): LayoutReading {
    val nodes = restingNodes(view)
    val byId = nodes.associateBy { it.id }
    val candidates = nodes.controls().filter { it.type !in OVERLAY_TYPES }
    val pairs = candidates.indices.flatMap { first -> (first + 1 until candidates.size).map { candidates[first] to candidates[it] } }
    val overlapping = pairs.count { (first, second) ->
        isInsideOverlay(first, byId) == isInsideOverlay(second, byId) &&
            !isAncestor(first, second, byId) && !isAncestor(second, first, byId) &&
            overlapsByMoreThanSlack(first.box!!, second.box!!)
    }
    return view.graded("overlapping_controls", overlapping.toDouble(), 0.0, false)
}

private fun overflowReading(view: PageView): LayoutReading {
    val past = restingNodes(view).shown().count { !contains(view.screen, it.box!!) }
    return view.graded("nodes_past_the_window", past.toDouble(), 0.0, false)
}

private fun cutOffReading(view: PageView): LayoutReading {
    val nodes = restingNodes(view)
    val byId = nodes.associateBy { it.id }
    val cut = nodes.shown().count { node ->
        val parent = node.parentId?.let(byId::get)
        parent != null && parent.isShown() && parent.type !in OVERLAY_TYPES && !contains(parent.box!!, node.box!!)
    }
    return view.graded("nodes_cut_off_by_their_parent", cut.toDouble(), 0.0, false)
}

private fun backgroundOf(node: DrawnNode, byId: Map<String, DrawnNode>, groundHex: String): String {
    var current = node.parentId?.let(byId::get)
    while (current != null) {
        current.fillHex?.let { return it }
        current = current.parentId?.let(byId::get)
    }
    return groundHex
}

private fun contrastReading(view: PageView): List<LayoutReading> {
    val nodes = restingNodes(view)
    val byId = nodes.associateBy { it.id }
    val ratios = nodes.shown().filter { it.inkHex != null }.map { contrastRatio(it.inkHex!!, backgroundOf(it, byId, view.page.groundHex)) }
    return if (ratios.isEmpty()) emptyList() else listOf(view.graded("min_contrast_ratio", ratios.min(), view.thresholds.minContrast, true))
}

private fun tapsToReach(view: PageView): Int {
    val stopIndex = view.page.captures.mapIndexed { index, capture -> (capture.stop?.let(STOP_ORDER::indexOf)?.takeIf { it >= 0 }) ?: index }
    val firstSeen = mutableMapOf<String, Int>()
    view.page.captures.forEachIndexed { index, capture ->
        flattenTree(capture.tree).controls().forEach { firstSeen.putIfAbsent(it.id, stopIndex[index]) }
    }
    return firstSeen.values.maxOrNull() ?: 0
}

private fun aboveFoldReading(view: PageView): List<LayoutReading> {
    val controls = restingNodes(view).controls()
    if (controls.isEmpty()) return emptyList()
    val count = controls.count { it.box!!.bottom <= view.screen.bottom }
    return listOf(view.graded("controls_above_the_fold", count.toDouble(), view.thresholds.minAboveFoldControls.toDouble(), true))
}

/** Every layout factor of one page on one device, each graded against that device's thresholds, or BLIND where the tree and the pixels cannot both be read. */
fun readLayout(page: LayoutPage, thresholds: LayoutThresholds, computedAt: String): List<LayoutReading> {
    val view = PageView(page, thresholds, computedAt)
    return mainShareReadings(view) + chromeReading(view) + tapTargetReading(view) + textLineReading(view) +
        overlapReading(view) + overflowReading(view) + cutOffReading(view) + contrastReading(view) +
        view.graded("taps_to_reach_every_control", tapsToReach(view).toDouble(), thresholds.maxTapsToControl.toDouble(), false) +
        aboveFoldReading(view)
}
