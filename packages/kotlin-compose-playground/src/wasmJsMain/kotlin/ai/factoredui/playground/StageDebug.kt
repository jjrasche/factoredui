package ai.factoredui.playground

import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef

fun publishStageBindings(snapshot: String): Unit =
    js("window.__stageBindings = snapshot")

fun pushStageLog(line: String): Unit =
    js("(window.__stageLog = window.__stageLog || []).push(line)")

fun publishStageLastAction(json: String): Unit =
    js("window.__stageLastAction = json")

fun pushStageTrainingRow(json: String): Unit =
    js("(window.__stageTrainingRows = window.__stageTrainingRows || []).push(json)")

fun nowMillis(): Double =
    js("Date.now()")

class StageDebugObservability : Observability {
    override fun onRender(nodeId: String) {}

    override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
        pushStageLog("spec-interaction node=$nodeId action=${action.action} params=$resolvedParams")
    }
}
