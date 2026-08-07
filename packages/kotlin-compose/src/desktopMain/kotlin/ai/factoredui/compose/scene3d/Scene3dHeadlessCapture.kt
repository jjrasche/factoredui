package ai.factoredui.compose.scene3d

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ai.factoredui.compose.math.Camera
import org.jetbrains.skia.EncodedImageFormat

private const val SETTLE_FRAME_NANOS = 16_666_667L

@OptIn(ExperimentalComposeUiApi::class)
fun captureScene3dPng(
    world: Scene3dWorldState,
    camera: Camera,
    meshes: Map<String, PreparedMesh>,
    width: Int = 1280,
    height: Int = 800,
    density: Float = 1f,
    showGrid: Boolean = false,
    transparentBackground: Boolean = false,
): ByteArray {
    val scene = ImageComposeScene(width = width, height = height, density = Density(density)) {
        Scene3dView(
            world = world,
            camera = camera,
            meshes = meshes,
            showGrid = showGrid,
            transparentBackground = transparentBackground,
        )
    }
    try {
        scene.render(0L).close()
        val image = scene.render(SETTLE_FRAME_NANOS)
        try {
            return image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
        } finally {
            image.close()
        }
    } finally {
        scene.close()
    }
}

fun captureScene3dPoseView(
    world: Scene3dWorldState,
    pose: Scene3dCameraPose,
    meshes: Map<String, PreparedMesh>,
    width: Int = 1280,
    height: Int = 800,
    density: Float = 1f,
    showGrid: Boolean = false,
    transparentBackground: Boolean = false,
): ByteArray = captureScene3dPng(
    world = world,
    camera = cameraOfPose(pose),
    meshes = meshes,
    width = width,
    height = height,
    density = density,
    showGrid = showGrid,
    transparentBackground = transparentBackground,
)
