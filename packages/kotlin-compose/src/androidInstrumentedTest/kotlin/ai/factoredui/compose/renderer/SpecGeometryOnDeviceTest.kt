package ai.factoredui.compose.renderer

import android.app.Activity
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ai.factoredui.compose.schema.BindingResolver
import ai.factoredui.compose.schema.Spec
import ai.factoredui.compose.schema.SpecNode
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private val PROBE_SPEC = json.decodeFromString(
    Spec.serializer(),
    """
    {
      "spec_version": 1, "renderer_min": 1,
      "root": {
        "id": "screen", "type": "column", "props": { "padding": 16, "gap": 12 },
        "children": [
          { "id": "title", "type": "text", "props": { "value": "factored-ui on device", "variant": "heading" } },
          {
            "id": "done-list", "type": "list", "props": {},
            "children": [
              { "id": "row-one", "type": "text", "props": { "value": "a list built from children" } },
              { "id": "row-two", "type": "text", "props": { "value": "each row its own node" } }
            ]
          },
          { "id": "send", "type": "button", "props": { "label": "tap target" } }
        ]
      }
    }
    """.trimIndent(),
)

class SpecGeometryOnDeviceTest {

    private fun drawnRegions(spec: Spec): Map<String, Rect> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val regions = mutableMapOf<String, Rect>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity: Activity ->
                (activity as ComponentActivity).setContent {
                    RenderSpec(spec, RenderContext(theme = SpecTheme.DARK))
                }
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            val root = generateSequence(0) { it + 1 }.take(25).firstNotNullOfOrNull { attempt ->
                if (attempt > 0) Thread.sleep(200)
                automation.rootInActiveWindow
            } ?: error("the device reported no active window to read after 5s")
            collect(root, regions)
        }
        return regions
    }

    private fun collect(node: AccessibilityNodeInfo, into: MutableMap<String, Rect>) {
        node.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let { description ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (description !in into) into[description] = bounds
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> collect(child, into) }
        }
    }

    private fun declaredNodeIds(root: SpecNode): List<String> {
        val ids = mutableListOf<String>()
        fun walk(node: SpecNode) {
            if (!BindingResolver.isVisible(node.visible, emptyMap())) return
            ids += node.id
            node.children.forEach(::walk)
        }
        walk(root)
        return ids
    }

    @Test
    fun the_device_reports_a_region_for_every_node_the_spec_declares() {
        val regions = drawnRegions(PROBE_SPEC)
        assertTrue(regions.isNotEmpty(), "the accessibility tree reported no factored-ui nodes at all")
        val undrawn = declaredNodeIds(PROBE_SPEC.root).filter { id ->
            val bounds = regions[id]
            bounds == null || bounds.width() <= 0 || bounds.height() <= 0
        }
        assertEquals(emptyList(), undrawn, "nodes the device drew no region for; saw ${regions.keys.sorted()}")
    }
}
