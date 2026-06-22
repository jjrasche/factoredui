package ai.factoredui.compose.scene3d

import kotlin.test.Test
import kotlin.test.assertEquals

class BodyFramesToMotionClipTest {

    private val response = BodyFramesResponse(
        frames = listOf(
            BodyFrame(rootX = 1f, rootY = 0f, rootZ = 2f, joints = listOf(listOf(0.1f, 0.2f, 0.3f))),
            BodyFrame(rootX = 5f, rootY = 0f, rootZ = 0f, joints = listOf(listOf(0.1f, 0.2f, 0.3f))),
        ),
        playhead = 1,
    )

    @Test
    fun preservesFrameCount() {
        assertEquals(2, bodyFramesToMotionClip(response).frames.size)
    }

    @Test
    fun bakesRootTranslationIntoEachJoint() {
        val clip = bodyFramesToMotionClip(response)
        assertEquals(listOf(1.1f, 0.2f, 2.3f), clip.frames[0].joints[0])
        assertEquals(listOf(5.1f, 0.2f, 0.3f), clip.frames[1].joints[0])
    }

    @Test
    fun sameJointMovesWithItsRoot() {
        val clip = bodyFramesToMotionClip(response)
        assertEquals(4f, clip.frames[1].joints[0][0] - clip.frames[0].joints[0][0])
    }
}
