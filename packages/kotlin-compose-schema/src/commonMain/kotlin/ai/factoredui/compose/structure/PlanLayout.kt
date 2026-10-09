package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneFootprint
import ai.factoredui.compose.scene.SceneView

data class RectMm(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    fun contains(x: Double, y: Double): Boolean = x >= x0 && x < x1 && y >= y0 && y < y1
}

/** A window fitted to a canvas: canvas y grows downward, so the window's top (north, or the highest elevation) lands at the top. */
data class CanvasFit(val scale: Double, val vScale: Double, val offsetX: Double, val offsetY: Double, val westMm: Double, val northMm: Double)

fun partRectMm(scene: SceneView, footprint: SceneFootprint): RectMm {
    val window = scene.window
    val tileX = (window.x1 - window.x0).toDouble() / scene.cols
    val tileY = (window.y1 - window.y0).toDouble() / scene.rows
    return RectMm(
        x0 = window.x0 + footprint.col * tileX,
        y0 = window.y0 + footprint.row * tileY,
        x1 = window.x0 + (footprint.col + footprint.width) * tileX,
        y1 = window.y0 + (footprint.row + footprint.height) * tileY,
    )
}

fun fitWindow(window: RectMm, canvasWidth: Double, canvasHeight: Double, margin: Double, exaggeration: Double = 1.0): CanvasFit {
    val width = window.x1 - window.x0
    val height = window.y1 - window.y0
    val scale = minOf((canvasWidth - 2 * margin) / width, (canvasHeight - 2 * margin) / (height * exaggeration))
    val vScale = scale * exaggeration
    return CanvasFit(
        scale = scale,
        vScale = vScale,
        offsetX = (canvasWidth - scale * width) / 2,
        offsetY = (canvasHeight - vScale * height) / 2,
        westMm = window.x0,
        northMm = window.y1,
    )
}

fun mmToCanvas(fit: CanvasFit, xMm: Double, yMm: Double): Pair<Double, Double> =
    fit.offsetX + (xMm - fit.westMm) * fit.scale to fit.offsetY + (fit.northMm - yMm) * fit.vScale

fun canvasToMm(fit: CanvasFit, x: Double, y: Double): Pair<Double, Double> =
    fit.westMm + (x - fit.offsetX) / fit.scale to fit.northMm - (y - fit.offsetY) / fit.vScale
