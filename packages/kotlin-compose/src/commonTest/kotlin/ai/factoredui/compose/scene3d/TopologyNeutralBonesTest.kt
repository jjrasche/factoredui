package ai.factoredui.compose.scene3d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopologyNeutralBonesTest {

    @Test
    fun providedParentsDriveConnectivityNotSmpl() {
        val dogParents = listOf(-1, 0, 1, 0, 3)
        val bones = bonesFor(dogParents, jointCount = 5)
        assertEquals(listOf(1 to 0, 2 to 1, 3 to 0, 4 to 3), bones,
            "a non-SMPL body must draw the connectivity it provides, not SMPL")
    }

    @Test
    fun nullParentsFallBackToSmplDefault() {
        val bones = bonesFor(parents = null, jointCount = 24)
        assertTrue(bones.isNotEmpty() && bones.all { it.first in 0..23 && it.second in 0..23 },
            "with no parents the leaf defaults to SMPL24 — a default, not a hardcode")
    }

    @Test
    fun rootAndOutOfRangeParentsAreSkipped() {
        val parents = listOf(-1, 0, 99)
        assertEquals(listOf(1 to 0), bonesFor(parents, jointCount = 3),
            "root (-1) and out-of-range parents draw no bone")
    }
}
