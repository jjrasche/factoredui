package ai.factoredui.compose.scene3d

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyFrameParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val pinnedRow = """
        {
          "root_x": 0.5, "root_y": 0.0, "root_z": 1.2,
          "facing_x": 0.0, "facing_y": 1.0,
          "joints": [[0.1,0.2,0.3],[0.4,0.5,0.6]],
          "joint_count": 2, "up_axis": 2,
          "velocities": [],
          "contacts": [{"effector":"L_Foot","on":true,"load":0.8}],
          "scalar_channel": [], "scalar_channel_tag": ""
        }
    """.trimIndent()

    @Test
    fun decodesPinnedV1Fields() {
        val frame = json.decodeFromString(BodyFrame.serializer(), pinnedRow)
        assertEquals(0.5f, frame.rootX)
        assertEquals(1.2f, frame.rootZ)
        assertEquals(1.0f, frame.facingY)
        assertEquals(2, frame.jointCount)
        assertEquals(2, frame.upAxis)
        assertEquals(2, frame.joints.size)
    }

    @Test
    fun decodesContactWithNormalizedLoad() {
        val frame = json.decodeFromString(BodyFrame.serializer(), pinnedRow)
        val contact = frame.contacts.single()
        assertEquals("L_Foot", contact.effector)
        assertTrue(contact.on)
        assertEquals(0.8f, contact.load)
    }

    @Test
    fun believableFieldsDefaultEmptyUntilTheSlotLands() {
        val frame = json.decodeFromString(BodyFrame.serializer(), pinnedRow)
        assertTrue(frame.velocities.isEmpty())
        assertTrue(frame.scalarChannel.isEmpty())
        assertEquals("", frame.scalarChannelTag)
    }

    @Test
    fun digestFinalizesAgainstBodyFrameJoints() {
        val frame = json.decodeFromString(BodyFrame.serializer(), pinnedRow)
        assertEquals(jointFrameDigest(frame.joints), jointFrameDigest(frame.joints))
        assertTrue(jointFrameDigest(frame.joints).isNotEmpty())
    }
}
