package ai.factoredui.compose.capture

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The interaction-payload contract: action name at the top level, resolved
 * action params nested one-to-one under `params`. Nesting means a user param
 * can never collide with the reserved `action` key, so there's no reserved-key
 * policy to get wrong.
 */
class CaptureObservabilityTest {

    @Test
    fun payloadHasOnlyActionWhenNoParams() {
        val payload = buildInteractionPayload("approve", emptyMap())
        assertEquals(JsonPrimitive("approve"), payload["action"])
        assertNull(payload["params"], "no params declared ⇒ no params key (anchoring is opt-in)")
        assertEquals(1, payload.size)
    }

    @Test
    fun resolvedParamsNestUnderParams() {
        val payload = buildInteractionPayload("approve", mapOf("id" to 2, "name" to "Beta", "ok" to true))
        assertEquals(JsonPrimitive("approve"), payload["action"])
        val params = payload["params"]
        assertTrue(params is JsonObject)
        assertEquals(JsonPrimitive(2), params["id"])
        assertEquals(JsonPrimitive("Beta"), params["name"])
        assertEquals(JsonPrimitive(true), params["ok"])
    }

    @Test
    fun aParamNamedActionDoesNotCollide() {
        // The reserved key lives at the top level; user params live under `params`.
        val payload = buildInteractionPayload("approve", mapOf("action" to "user-value"))
        assertEquals(JsonPrimitive("approve"), payload["action"])
        assertEquals(JsonPrimitive("user-value"), (payload["params"] as JsonObject)["action"])
    }

    @Test
    fun nestedMapsAndListsConvert() {
        val payload = buildInteractionPayload(
            "a",
            mapOf("frame" to mapOf("idx" to 3), "tags" to listOf("x", "y")),
        )
        val params = payload["params"] as JsonObject
        assertEquals(JsonPrimitive(3), (params["frame"] as JsonObject)["idx"])
        assertEquals(listOf(JsonPrimitive("x"), JsonPrimitive("y")), (params["tags"] as JsonArray).toList())
    }

    @Test
    fun nullParamBecomesJsonNull() {
        val payload = buildInteractionPayload("a", mapOf("maybe" to null))
        assertEquals(JsonNull, (payload["params"] as JsonObject)["maybe"])
    }
}
