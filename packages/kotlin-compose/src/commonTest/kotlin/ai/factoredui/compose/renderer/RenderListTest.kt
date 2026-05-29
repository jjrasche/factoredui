package ai.factoredui.compose.renderer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import ai.factoredui.compose.adapter.HostDataSource
import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Compose UI tests for the LIST primitive's reactive `data_source` binding.
 *
 * These exercise the live-query contract: a host-provided [HostDataSource]
 * streams result sets, the list re-renders on every emission, each row is
 * scoped under "{row.x}", and per-row actions interpolate that same row into
 * their params. The static `data` path is covered for backwards-compatibility.
 */
@OptIn(ExperimentalTestApi::class)
class RenderListTest {

    private val alpha = mapOf("id" to 1, "label" to "Alpha")
    private val beta = mapOf("id" to 2, "label" to "Beta")

    /** Row template: a text node bound to the per-row "{row.label}". */
    private fun textRowTemplate() = SpecNode(
        id = "row-text",
        type = SpecNodeType.TEXT,
        props = mapOf("value" to SpecValue.StringValue("{row.label}")),
    )

    /** A list node driven by a live query. */
    private fun liveListNode(template: SpecNode) = SpecNode(
        id = "live-list",
        type = SpecNodeType.LIST,
        props = mapOf(
            "data_source" to SpecValue.StringValue("query:approvals"),
            "itemTemplate" to SpecValue.NodeValue(template),
        ),
    )

    @Test
    fun liveListRendersRowsFromDataSource() = runComposeUiTest {
        val rows = MutableStateFlow<List<Any?>>(listOf(alpha, beta))
        val context = RenderContext(hostDataSource = HostDataSource { _ -> rows })

        setContent { RenderSpec(root = liveListNode(textRowTemplate()), context = context) }
        waitForIdle()

        onNodeWithText("Alpha").assertIsDisplayed()
        onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun liveListReRendersWhenDataSourceEmits() = runComposeUiTest {
        val rows = MutableStateFlow<List<Any?>>(listOf(alpha, beta))
        val context = RenderContext(hostDataSource = HostDataSource { _ -> rows })

        setContent { RenderSpec(root = liveListNode(textRowTemplate()), context = context) }
        waitForIdle()
        onNodeWithText("Alpha").assertIsDisplayed()

        // Host reports a change: Alpha was approved and drops out of the query.
        rows.value = listOf(beta)
        waitForIdle()

        onNodeWithText("Alpha").assertDoesNotExist()
        onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun perRowActionInterpolatesRowContext() = runComposeUiTest {
        var approvedParams: Map<String, Any?>? = null
        val rows = MutableStateFlow<List<Any?>>(listOf(alpha, beta))
        val context = RenderContext(
            actions = mapOf("approve" to { params -> approvedParams = params }),
            hostDataSource = HostDataSource { _ -> rows },
        )
        // Each row is a button labelled "{row.label}" that approves "{row.id}".
        val buttonTemplate = SpecNode(
            id = "row-approve",
            type = SpecNodeType.BUTTON,
            props = mapOf("label" to SpecValue.StringValue("{row.label}")),
            action = ActionRef(
                action = "approve",
                params = mapOf("id" to SpecValue.StringValue("{row.id}")),
            ),
        )

        setContent { RenderSpec(root = liveListNode(buttonTemplate), context = context) }
        waitForIdle()

        onNodeWithText("Beta").performClick()
        waitForIdle()

        assertEquals(2, approvedParams?.get("id"), "action should interpolate the clicked row's id")
    }

    @Test
    fun perRowActionSurfacesResolvedParamsToObservability() = runComposeUiTest {
        // Capture anchoring: the observability hook must see the resolved row
        // params (not the raw "{row.id}" binding ref), so a captured interaction
        // is anchored to the data it was about.
        val captured = mutableListOf<Map<String, Any?>>()
        val recordingObservability = object : Observability {
            override fun onRender(nodeId: String) = Unit
            override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
                captured.add(resolvedParams)
            }
        }
        val rows = MutableStateFlow<List<Any?>>(listOf(alpha, beta))
        val context = RenderContext(
            observability = recordingObservability,
            hostDataSource = HostDataSource { _ -> rows },
        )
        val buttonTemplate = SpecNode(
            id = "row-approve",
            type = SpecNodeType.BUTTON,
            props = mapOf("label" to SpecValue.StringValue("{row.label}")),
            action = ActionRef(action = "approve", params = mapOf("id" to SpecValue.StringValue("{row.id}"))),
        )

        setContent { RenderSpec(root = liveListNode(buttonTemplate), context = context) }
        waitForIdle()
        onNodeWithText("Beta").performClick()
        waitForIdle()

        val betaInteraction = captured.firstOrNull { it["id"] == 2 }
        assertNotNull(betaInteraction, "observability should witness the clicked row's resolved id")
    }

    @Test
    fun staticListResolvesRowAndItemScopeKeys() = runComposeUiTest {
        // No data_source: falls back to the static `data` key. Both the new
        // "{row.x}" and the legacy "{item.x}" syntaxes must resolve per row.
        val context = RenderContext(
            initialData = mapOf("people" to listOf(alpha, beta)),
        )
        val rowTemplate = SpecNode(
            id = "person-row",
            type = SpecNodeType.ROW,
            children = listOf(
                SpecNode(
                    id = "via-row",
                    type = SpecNodeType.TEXT,
                    props = mapOf("value" to SpecValue.StringValue("{row.label}")),
                ),
                SpecNode(
                    id = "via-item",
                    type = SpecNodeType.TEXT,
                    props = mapOf("value" to SpecValue.StringValue("id={item.id}")),
                ),
            ),
        )
        val node = SpecNode(
            id = "static-list",
            type = SpecNodeType.LIST,
            props = mapOf(
                "data" to SpecValue.StringValue("people"),
                "itemTemplate" to SpecValue.NodeValue(rowTemplate),
            ),
        )

        setContent { RenderSpec(root = node, context = context) }
        waitForIdle()

        onNodeWithText("Alpha").assertIsDisplayed()
        onNodeWithText("id=1").assertIsDisplayed()
        onNodeWithText("id=2").assertIsDisplayed()
    }
}
