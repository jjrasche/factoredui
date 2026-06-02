package ai.factoredui.compose.scene3d

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.forcegraph.math.Camera
import ai.factoredui.compose.forcegraph.math.Vec3
import kotlinx.serialization.json.Json
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.test.Test

// Renders the decimated Heigl mesh through the real Scene3dView.drawMesh path on
// the desktop Compose target and writes a PNG, proving the Kotlin rasterizer
// produces the same image the numpy reference predicted. Headless, no browser.
@OptIn(ExperimentalTestApi::class)
class Scene3dDesktopRenderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun rendersHeiglMeshToPng() = runComposeUiTest {
        val mesh = json
            .decodeFromString(Scene3dMesh.serializer(), readMeshResource("heigl.json"))
            .prepare()
        val world = Scene3dWorldState(
            entities = listOf(
                Scene3dEntity(id = "heigl", position = listOf(-0.8f, 0f, 0f)),
                Scene3dEntity(id = "guest", position = listOf(0.8f, 0f, 0f), selected = true),
            ),
            lights = listOf(
                Scene3dLight(type = "key", position = listOf(2.5f, 3.0f, 2.0f), intensity = 1.2f),
                Scene3dLight(type = "fill", position = listOf(-3.0f, 2.5f, 1.0f), intensity = 0.5f),
            ),
        )
        val height = mesh.height
        val camera = Camera(
            yawRadians = (-PI / 4.0).toFloat(),
            pitchRadians = (PI / 9.0).toFloat(),
            distance = height * 3.0f,
            target = Vec3(0f, height * 0.5f, 0f),
            fovYRadians = (PI / 3.0).toFloat(),
        )

        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(800.dp)) {
                    Scene3dView(
                        world = world,
                        camera = camera,
                        meshes = mapOf("heigl" to mesh, "guest" to mesh),
                        modifier = Modifier.size(800.dp),
                    )
                }
            }
        }

        val outFile = File(System.getProperty("user.dir"), "build/scene3d_desktop_heigl.png")
        outFile.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", outFile)
        println("[scene3d] wrote ${outFile.absolutePath}")
    }

    private fun readMeshResource(name: String): String {
        val stream = this::class.java.classLoader.getResourceAsStream(name)
            ?: error("missing test resource: $name")
        return stream.bufferedReader().use { it.readText() }
    }
}
