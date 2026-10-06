package ai.factoredui.compose.terrain

import ai.factoredui.compose.scene.DeviceProfile

data class TerrainCosts(val gridMinTilePixels: Float, val thinContourMinTilePixels: Float) {
    fun isGridShown(tilePixels: Float): Boolean = tilePixels >= gridMinTilePixels

    fun areThinContoursShown(tilePixels: Float): Boolean = tilePixels >= thinContourMinTilePixels
}

private val DESKTOP_TERRAIN_COSTS = TerrainCosts(gridMinTilePixels = 24f, thinContourMinTilePixels = 16f)

private val PHONE_TERRAIN_COSTS = TerrainCosts(gridMinTilePixels = 32f, thinContourMinTilePixels = 24f)

fun terrainCostsFor(profile: DeviceProfile): TerrainCosts = when (profile) {
    DeviceProfile.PHONE -> PHONE_TERRAIN_COSTS
    DeviceProfile.DESKTOP -> DESKTOP_TERRAIN_COSTS
}
