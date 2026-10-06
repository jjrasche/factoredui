package ai.factoredui.compose.pixel

import ai.factoredui.compose.scene.DeviceProfile

data class PixelCosts(val isGroundTextured: Boolean, val isWaterAnimated: Boolean, val decorationLimit: Int)

private val DESKTOP_PIXEL_COSTS = PixelCosts(isGroundTextured = true, isWaterAnimated = true, decorationLimit = 4000)

private val PHONE_PIXEL_COSTS = PixelCosts(isGroundTextured = true, isWaterAnimated = false, decorationLimit = 400)

fun pixelCostsFor(profile: DeviceProfile): PixelCosts = when (profile) {
    DeviceProfile.PHONE -> PHONE_PIXEL_COSTS
    DeviceProfile.DESKTOP -> DESKTOP_PIXEL_COSTS
}
