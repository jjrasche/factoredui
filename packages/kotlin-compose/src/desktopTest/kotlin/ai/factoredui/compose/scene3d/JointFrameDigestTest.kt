package ai.factoredui.compose.scene3d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class JointFrameDigestTest {

    private val frameZero = listOf(listOf(0.1f, 0.2f, 0.3f), listOf(1.0f, 1.1f, 1.2f))
    private val frameOne = listOf(listOf(0.4f, 0.5f, 0.6f), listOf(1.0f, 1.1f, 1.2f))

    @Test
    fun sameJointsProduceSameDigest() {
        assertEquals(jointFrameDigest(frameZero), jointFrameDigest(frameZero))
    }

    @Test
    fun aMovedJointChangesTheDigest() {
        assertNotEquals(jointFrameDigest(frameZero), jointFrameDigest(frameOne))
    }

    @Test
    fun digestIsOrderSensitive() {
        assertNotEquals(jointFrameDigest(frameZero), jointFrameDigest(frameZero.reversed()))
    }

    @Test
    fun subMillimetreJitterDoesNotChangeTheDigest() {
        val renderIrrelevantNoise = listOf(listOf(0.1004f, 0.2003f, 0.2997f), listOf(1.0f, 1.1f, 1.2f))
        assertEquals(jointFrameDigest(frameZero), jointFrameDigest(renderIrrelevantNoise))
    }

    @Test
    fun emptyFrameDigestsToEmptyString() {
        assertEquals("", jointFrameDigest(emptyList()))
    }
}
