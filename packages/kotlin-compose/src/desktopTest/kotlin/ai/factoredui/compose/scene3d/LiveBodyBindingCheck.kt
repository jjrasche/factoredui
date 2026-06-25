package ai.factoredui.compose.scene3d

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.RenderNode
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.DomShadow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class LiveBodyBindingCheck {

    private fun skeleton(seed: Float): List<List<Float>> =
        (0 until 24).map { listOf(it * seed, it * 0.2f, it * 0.3f) }

    // The body draw maps stream Z-up (x,y,z) to entity space (x, z, -y) — match it for the digest.
    private fun entitySpace(joints: List<List<Float>>): List<List<Float>> =
        joints.map { listOf(it[0], it[2], -it[1]) }

    private fun bodyState(joints: List<List<Float>>): Map<String, Any?> = mapOf("joints" to joints)

    private val liveBodyScene = SpecNode(
        id = "stage",
        type = SpecNodeType.SCENE3D,
        props = mapOf("body" to SpecValue.StringValue("{runtime.bodyState}")),
    )

    @Test
    fun aPushedBodyFrameDrivesTheDrawnSkeleton() = runComposeUiTest {
        val first = skeleton(0.11f)
        val context = RenderContext(initialData = mapOf("runtime" to mapOf("bodyState" to bodyState(first))))
        setContent { Box(Modifier.size(400.dp)) { RenderNode(liveBodyScene, context) } }
        waitForIdle()
        val drawn = DomShadow.byRole("scene3d-body").first { it.attrs["entity-id"] == "body" }
        assertEquals(jointFrameDigest(entitySpace(first)), drawn.attrs["joints-digest"],
            "the live {body} binding must drive the drawn skeleton from host state")
    }

    @Test
    fun pushingANewFrameRecomposesTheBodyLive() = runComposeUiTest {
        val first = skeleton(0.11f)
        val next = skeleton(0.47f)
        assertNotEquals(jointFrameDigest(entitySpace(first)), jointFrameDigest(entitySpace(next)))
        val context = RenderContext(initialData = mapOf("runtime" to mapOf("bodyState" to bodyState(first))))
        setContent { Box(Modifier.size(400.dp)) { RenderNode(liveBodyScene, context) } }
        waitForIdle()

        context.setBinding("runtime.bodyState", bodyState(next))
        waitForIdle()

        val drawn = DomShadow.byRole("scene3d-body").first { it.attrs["entity-id"] == "body" }
        assertEquals(jointFrameDigest(entitySpace(next)), drawn.attrs["joints-digest"],
            "pushing a new frame @runtime must recompose the body live — the realtime control loop")
    }
}
