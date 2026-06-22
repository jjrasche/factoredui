package ai.factoredui.compose.scene3d

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyFrameParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val snakeRow = """
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

    private val camelDto = """
        {
          "rootX": 0.5, "rootY": 0.0, "rootZ": 1.2,
          "facingX": 0.0, "facingY": 1.0,
          "joints": [[0.1,0.2,0.3],[0.4,0.5,0.6]],
          "jointCount": 2, "upAxis": 2,
          "velocities": [],
          "contacts": [{"effector":"L_Foot","on":true,"load":0.8}],
          "scalarChannel": [], "scalarChannelTag": "",
          "com": [0.0,0.0,1.0], "force": 0.0, "mode": "live"
        }
    """.trimIndent()

    @Test
    fun decodesSnakeRowFields() {
        val frame = json.decodeFromString(BodyFrame.serializer(), snakeRow)
        assertEquals(0.5f, frame.rootX)
        assertEquals(1.2f, frame.rootZ)
        assertEquals(1.0f, frame.facingY)
        assertEquals(2, frame.jointCount)
        assertEquals(2, frame.upAxis)
        assertEquals(2, frame.joints.size)
    }

    @Test
    fun snakeRowAndCamelDtoParseIdentically() {
        val fromSnake = json.decodeFromString(BodyFrame.serializer(), snakeRow)
        val fromCamel = json.decodeFromString(BodyFrame.serializer(), camelDto)
        assertEquals(fromSnake, fromCamel)
    }

    @Test
    fun ignoresDtoOnlyFields() {
        val frame = json.decodeFromString(BodyFrame.serializer(), camelDto)
        assertEquals(2, frame.jointCount)
    }

    @Test
    fun decodesContactWithNormalizedLoad() {
        val contact = json.decodeFromString(BodyFrame.serializer(), camelDto).contacts.single()
        assertEquals("L_Foot", contact.effector)
        assertTrue(contact.on)
        assertEquals(0.8f, contact.load)
    }

    @Test
    fun believableFieldsDefaultEmptyUntilTheSlotLands() {
        val frame = json.decodeFromString(BodyFrame.serializer(), snakeRow)
        assertTrue(frame.velocities.isEmpty())
        assertTrue(frame.scalarChannel.isEmpty())
        assertEquals("", frame.scalarChannelTag)
    }

    @Test
    fun digestFinalizesAgainstBodyFrameJoints() {
        val frame = json.decodeFromString(BodyFrame.serializer(), snakeRow)
        assertTrue(jointFrameDigest(frame.joints).isNotEmpty())
    }

    @Test
    fun responseEnvelopeReadsRingPlayheadAndPlan() {
        val envelope = """
            {
              "frames": [$camelDto],
              "playhead": 0,
              "targetX": 0.4, "targetY": 1.0, "targetZ": 0.6,
              "trajectory": [[0.0,0.0,0.0],[0.4,1.0,0.6]]
            }
        """.trimIndent()
        val response = json.decodeFromString(BodyFramesResponse.serializer(), envelope)
        assertEquals(1, response.frames.size)
        assertEquals(0, response.playhead)
        assertEquals(0.4f, response.targetX)
        assertEquals(2, response.trajectory.size)
    }
}
