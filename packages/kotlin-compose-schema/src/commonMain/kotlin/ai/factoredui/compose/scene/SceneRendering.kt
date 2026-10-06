package ai.factoredui.compose.scene

enum class DeviceProfile { PHONE, DESKTOP }

enum class SceneLayerKind { GROUND, SURFACE, WATER, FOOTPRINTS, INSTANCES, BOUNDARIES, GRID, ANNOTATIONS }

val ZOOM_LADDER_FEET: List<Int> = listOf(1, 5, 25, 125, 625)

const val LANDING_LEVEL_FEET = 625

data class RendererCapability(
    val id: String,
    val layers: Set<SceneLayerKind>,
    val levelsFeet: Set<Int>,
    val profiles: Set<DeviceProfile> = DeviceProfile.entries.toSet(),
) {
    fun supports(profile: DeviceProfile, levelFeet: Int): Boolean = profile in profiles && levelFeet in levelsFeet
}

const val VECTOR_DEFAULT_TERRAIN_MODE = "hillshade"

private val MAP_LAYERS = setOf(SceneLayerKind.GROUND, SceneLayerKind.FOOTPRINTS, SceneLayerKind.INSTANCES, SceneLayerKind.GRID)

val PIXEL_MAP_CAPABILITY = RendererCapability("pixel-map", MAP_LAYERS, setOf(1, 5, 25, 125))

val VECTOR_MAP_CAPABILITY = RendererCapability("vector-map", MAP_LAYERS, setOf(25, 125, 625))

val STANDARD_RENDERER_CAPABILITIES = listOf(PIXEL_MAP_CAPABILITY, VECTOR_MAP_CAPABILITY)

fun isVectorLevel(levelFeet: Int): Boolean =
    selectRenderer(STANDARD_RENDERER_CAPABILITIES, DeviceProfile.DESKTOP, levelFeet, null)?.id == VECTOR_MAP_CAPABILITY.id

fun selectRenderer(capabilities: List<RendererCapability>, profile: DeviceProfile, levelFeet: Int, preferredId: String?): RendererCapability? {
    val supporting = capabilities.filter { it.supports(profile, levelFeet) }
    return supporting.firstOrNull { it.id == preferredId } ?: supporting.firstOrNull()
}

fun nearestLadderLevel(levelFeet: Double): Int = ZOOM_LADDER_FEET.minBy { kotlin.math.abs(it - levelFeet) }

fun zoomInLevel(levelFeet: Int): Int = ZOOM_LADDER_FEET.lastOrNull { it < levelFeet } ?: ZOOM_LADDER_FEET.first()

fun zoomOutLevel(levelFeet: Int): Int = ZOOM_LADDER_FEET.firstOrNull { it > levelFeet } ?: ZOOM_LADDER_FEET.last()

data class ViewState(
    val levelFeet: Int?,
    val quarterTurns: Int = 0,
    val centreMm: Pair<Long, Long>? = null,
    val rendererId: String? = null,
)

fun resolveViewState(resolved: Any?): ViewState {
    val fields = resolved as? Map<*, *>
    val level = (fields?.get("level_feet") as? Number)?.toInt()?.takeIf { it > 0 }
    val turns = (fields?.get("quarter_turns") as? Number)?.toInt() ?: 0
    val centre = centreOf(fields?.get("centre_mm"))
    return ViewState(level, ((turns % 4) + 4) % 4, centre, fields?.get("renderer") as? String)
}

fun viewStateRecord(view: ViewState): Map<String, Any?> = buildMap {
    view.levelFeet?.let { put("level_feet", it) }
    put("quarter_turns", view.quarterTurns)
    view.centreMm?.let { put("centre_mm", listOf(it.first, it.second)) }
    view.rendererId?.let { put("renderer", it) }
}

private fun centreOf(raw: Any?): Pair<Long, Long>? {
    val pair = raw as? List<*> ?: return null
    if (pair.size != 2) return null
    val x = (pair[0] as? Number)?.toDouble() ?: return null
    val y = (pair[1] as? Number)?.toDouble() ?: return null
    return roundHalfUp(x) to roundHalfUp(y)
}
