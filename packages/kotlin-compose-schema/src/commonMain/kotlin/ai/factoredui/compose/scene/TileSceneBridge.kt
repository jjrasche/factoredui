package ai.factoredui.compose.scene

import ai.factoredui.compose.layout.TileFootprint
import ai.factoredui.compose.layout.TileInstance

fun tileFootprintsOf(scene: SceneView): List<TileFootprint> =
    scene.footprints?.items.orEmpty().map { TileFootprint(it.id, it.type, it.col, it.row, it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }

fun tileInstancesOf(scene: SceneView): List<TileInstance> =
    scene.instances?.items.orEmpty().map {
        TileInstance(
            id = it.id,
            use = it.type,
            xMm = it.xMm.toDouble(),
            yMm = it.yMm.toDouble(),
            heightMm = it.heightMm?.toDouble(),
            crownRadiusMm = it.crownRadiusMm?.toDouble(),
            rotationDeg = it.rotationDeg,
        )
    }
