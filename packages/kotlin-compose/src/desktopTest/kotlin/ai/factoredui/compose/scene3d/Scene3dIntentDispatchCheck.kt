package ai.factoredui.compose.scene3d

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.renderer.sceneIntentDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Scene3dIntentDispatchCheck {

    @Test
    fun anEmittedIntentReachesTheHostActionRegistry() = runTest {
        var seenEntity: String? = null
        var seenAction: String? = null
        val capture: ActionHandler = { params -> seenEntity = params["entity_id"] as? String }
        val context = RenderContext(actions = mapOf("move-entity" to capture))

        val dispatch = sceneIntentDispatcher(context, nodeId = "scene") { action -> seenAction = action }
        dispatch.invoke("move-entity", mapOf("entity_id" to "chair_1", "position" to listOf(0.0, 0.0, 0.0)))

        assertEquals("chair_1", seenEntity, "a scene3d intent must reach the host's registered handler with its params")
        assertEquals("move-entity", seenAction, "the intent's observability hook must witness the action name")
    }

    @Test
    fun anUnregisteredIntentIsANoOpNotACrash() = runTest {
        val context = RenderContext(actions = emptyMap())
        val dispatch = sceneIntentDispatcher(context, nodeId = "scene") {}
        dispatch("camera-update", mapOf("fov" to 1.2))
    }
}
