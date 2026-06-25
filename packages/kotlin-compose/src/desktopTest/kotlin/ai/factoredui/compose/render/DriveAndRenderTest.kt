package ai.factoredui.compose.render

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriveAndRenderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val canvasFieldSpec = """
        {"spec_version":1,"renderer_min":1,"root":{"id":"field","type":"canvas","children":[
          {"id":"node-a","type":"text","props":{"x":"{nx}","y":"{ny}","value":"drag me"}}
        ]}}
    """.trimIndent()

    private val initialState = """{"nx":50,"ny":50,"relevance":0.8}"""

    private val dragGesture = """
        [{"type":"down","x":110,"y":110},
         {"type":"move","x":170,"y":150},
         {"type":"move","x":240,"y":200},
         {"type":"up","x":240,"y":200}]
    """.trimIndent()

    @Test
    fun aRealDriveMovesPositionAndLeavesRelevanceUntouched() {
        val result = driveAndRender(canvasFieldSpec, initialState, dragGesture, width = 600, height = 600)
        assertTrue(result.png.size > 64, "drive must still produce a PNG, got ${result.png.size}")
        val state = json.parseToJsonElement(result.finalStateJson).jsonObject
        val nx = state["nx"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        assertTrue(nx > 50.0, "a real curation drag must move position.x (nx=$nx)")
        assertEquals(0.8, state["relevance"]?.jsonPrimitive?.doubleOrNull, "relevance is the agent's glow — a position drag never touches it")
    }
}
