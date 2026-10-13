package ai.factoredui.compose.stackedscene

import kotlin.math.pow

const val MIN_ZOOM = 0.25
const val MAX_ZOOM = 16.0
const val ZOOM_PER_NOTCH = 1.25

/** Framing over the fitted drawing in every view: [zoom] about the drawing area's centre, then a shift by [panX] of its width and [panY] of its height. */
data class SceneCamera(val zoom: Double = 1.0, val panX: Double = 0.0, val panY: Double = 0.0)

fun clampedZoom(zoom: Double): Double = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

fun cameraFit(base: CanvasFit, camera: SceneCamera, widthPx: Double, heightPx: Double): CanvasFit {
    val centreX = widthPx / 2
    val centreY = heightPx / 2
    return base.copy(
        scale = base.scale * camera.zoom,
        vScale = base.vScale * camera.zoom,
        offsetX = centreX + camera.zoom * (base.offsetX - centreX) + camera.panX * widthPx,
        offsetY = centreY + camera.zoom * (base.offsetY - centreY) + camera.panY * heightPx,
    )
}

fun pannedBy(camera: SceneCamera, travelXPx: Double, travelYPx: Double, widthPx: Double, heightPx: Double): SceneCamera {
    if (widthPx <= 0 || heightPx <= 0) return camera
    return camera.copy(panX = camera.panX + travelXPx / widthPx, panY = camera.panY + travelYPx / heightPx)
}

/** Positive [notches] turn the wheel toward the person and zoom out; the drawn point under the pointer stays under it, so the pan follows. */
fun zoomedAbout(camera: SceneCamera, notches: Double, pointerXPx: Double, pointerYPx: Double, widthPx: Double, heightPx: Double): SceneCamera {
    if (widthPx <= 0 || heightPx <= 0) return camera
    val zoom = clampedZoom(camera.zoom * ZOOM_PER_NOTCH.pow(-notches))
    val centreX = widthPx / 2
    val centreY = heightPx / 2
    val fittedX = (pointerXPx - centreX - camera.panX * widthPx) / camera.zoom + centreX
    val fittedY = (pointerYPx - centreY - camera.panY * heightPx) / camera.zoom + centreY
    return SceneCamera(
        zoom = zoom,
        panX = (pointerXPx - centreX - zoom * (fittedX - centreX)) / widthPx,
        panY = (pointerYPx - centreY - zoom * (fittedY - centreY)) / heightPx,
    )
}
