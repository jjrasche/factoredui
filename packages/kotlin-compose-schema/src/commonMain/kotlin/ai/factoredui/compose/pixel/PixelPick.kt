package ai.factoredui.compose.pixel

import ai.factoredui.compose.layout.GroundPoint
import kotlin.math.roundToInt

data class SpriteBox(val left: Int, val top: Int, val width: Int, val height: Int) {
    fun contains(x: Float, y: Float, pad: Float): Boolean = x >= left - pad && x <= left + width + pad && y >= top - pad && y <= top + height + pad
}

fun spriteBoxAt(sprite: PixelSprite, contentX: Float, contentY: Float): SpriteBox =
    SpriteBox(contentX.roundToInt() - sprite.anchorX, contentY.roundToInt() - sprite.anchorY, sprite.width, sprite.height)

fun pickPixelInstance(draws: List<PixelDraw>, toContent: (GroundPoint) -> Pair<Float, Float>, x: Float, y: Float, pad: Float): String? =
    draws.lastOrNull { draw ->
        draw.instanceId != null && toContent(draw.ground).let { (contentX, contentY) -> spriteBoxAt(draw.sprite, contentX, contentY).contains(x, y, pad) }
    }?.instanceId
