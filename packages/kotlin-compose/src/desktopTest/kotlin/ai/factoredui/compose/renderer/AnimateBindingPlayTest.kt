package ai.factoredui.compose.renderer

import ai.factoredui.compose.crawl.driveScreen
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WIDTH_DP = 390
private const val HEIGHT_DP = 844
private const val INTERVAL_MS = 100L

private val SPEC: Spec = Json.decodeFromString(
    Spec.serializer(),
    """
    { "spec_version": 1, "renderer_min": 1, "state": { "heat": { "hour": 0 }, "ui": { "playing": false } },
      "root": { "id": "root", "type": "column", "props": {}, "children": [
        { "id": "play", "type": "button", "props": { "label": "Play" },
          "action": { "action": "animate_binding", "params": { "path": "heat.hour", "from": 0, "to": 23, "interval_ms": $INTERVAL_MS, "loop": true, "playing": "ui.playing" } } } ] } }
    """,
)

private fun hourOf(store: Map<String, Any?>): Double = ((store["heat"] as Map<*, *>)["hour"] as Number).toDouble()

private fun waitFor(screen: ai.factoredui.compose.crawl.DrivenScreen, isReached: () -> Boolean) {
    repeat(60) {
        if (isReached()) return
        Thread.sleep(50)
        screen.frame()
    }
}

class AnimateBindingPlayTest {
    @Test
    fun a_tap_plays_the_number_forward_and_a_second_tap_stops_it() {
        driveScreen(SPEC, widthDp = WIDTH_DP, heightDp = HEIGHT_DP).use { screen ->
            screen.tap("play")
            waitFor(screen) { hourOf(screen.store()) >= 2.0 }
            assertTrue(hourOf(screen.store()) >= 2.0, "the hour did not advance: ${screen.store()["heat"]}")
            assertEquals(true, (screen.store()["ui"] as Map<*, *>)["playing"])
            screen.tap("play")
            val stoppedAt = hourOf(screen.store())
            Thread.sleep(INTERVAL_MS * 4)
            screen.frame()
            assertEquals(stoppedAt, hourOf(screen.store()), "the hour moved after the second tap")
            assertEquals(false, (screen.store()["ui"] as Map<*, *>)["playing"])
        }
    }
}
