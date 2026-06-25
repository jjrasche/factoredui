package ai.factoredui.compose.scene3d

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.testing.DomShadow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class Scene3dViewObservabilityCheck {

    private fun knownSkeleton(seed: Float): List<List<Float>> =
        (0 until 24).map { listOf(it * seed, it * 0.2f, it * 0.3f) }

    @Test
    fun shadowNodeReflectsTheJointsTheRendererDrew() = runComposeUiTest {
        val drawn = knownSkeleton(0.11f)
        setContent {
            Scene3dView(
                world = Scene3dWorldState(entities = listOf(Scene3dEntity(id = "trust-body", jointFrame = drawn))),
                camera = Camera(),
                nodeId = "stage",
                modifier = Modifier.size(400.dp),
            )
        }
        waitForIdle()
        val node = DomShadow.byRole("scene3d-body").first { it.attrs["entity-id"] == "trust-body" }
        assertEquals("24", node.attrs["joint-count"])
        assertEquals(jointFrameDigest(drawn), node.attrs["joints-digest"])
    }

    @Test
    fun nodeChangesWhenTheDrawnFrameChanges() = runComposeUiTest {
        val frameA = knownSkeleton(0.11f)
        val frameB = knownSkeleton(0.47f)
        assertNotEquals(jointFrameDigest(frameA), jointFrameDigest(frameB))
        setContent {
            Scene3dView(
                world = Scene3dWorldState(entities = listOf(Scene3dEntity(id = "moving-body", jointFrame = frameB))),
                camera = Camera(),
                nodeId = "stage2",
                modifier = Modifier.size(400.dp),
            )
        }
        waitForIdle()
        val node = DomShadow.byRole("scene3d-body").first { it.attrs["entity-id"] == "moving-body" }
        assertEquals(jointFrameDigest(frameB), node.attrs["joints-digest"])
    }
}
