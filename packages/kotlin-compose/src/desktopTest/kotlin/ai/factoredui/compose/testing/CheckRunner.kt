package ai.factoredui.compose.testing

import ai.factoredui.compose.fieldgraph.graph.FieldGraphState
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CheckRunner(
    private val interactor: SpecInteractor,
    private val engineClient: EngineClient = FakeEngineClient(),
    private val fieldState: FieldGraphState? = null,
) {
    fun run(check: Check) {
        check.steps.forEach { step -> execute(step) }
    }

    private fun execute(step: CheckStep) = when (step) {
        is CheckStep.Tap -> interactor.tap(step.nodeId)
        is CheckStep.TapFieldNode -> { interactor.tapFieldNode(step.nodeId) }
        is CheckStep.DragFieldNodeToCenter -> interactor.dragFieldNodeToCenter(step.nodeId)
        is CheckStep.DragFieldNode -> interactor.dragFieldNode(step.nodeId, step.toX, step.toY)
        is CheckStep.AdvanceTime -> {
            fieldState?.advanceTime(step.seconds)
            interactor.advanceFrameAndIdle()
        }
        is CheckStep.Await -> executeAwait(step.condition)
        is CheckStep.Expect -> executeExpect(step.assertion)
    }

    private fun executeAwait(condition: AwaitCondition) = when (condition) {
        is AwaitCondition.FieldNodePresent -> interactor.assertFieldNodePresent(condition.nodeId)
        is AwaitCondition.NodeExists -> interactor.assertNodeExists(condition.nodeId)
    }

    private fun executeExpect(assertion: CheckAssertion) = when (assertion) {
        is CheckAssertion.FieldNodePresent -> interactor.assertFieldNodePresent(assertion.nodeId)
        is CheckAssertion.FieldNodeAbsent -> interactor.assertFieldNodeAbsent(assertion.nodeId)
        is CheckAssertion.ActionFired -> interactor.assertActionFired(assertion.action, assertion.params)
        is CheckAssertion.NoActionFired -> interactor.assertNoActionFired()
        is CheckAssertion.DragMagnitude -> interactor.assertLastDragMagnitude(assertion.nodeId, assertion.min)
        is CheckAssertion.FieldNodeAgeSecs -> interactor.assertFieldNodeAgeSecs(assertion.nodeId, assertion.minSecs)
        is CheckAssertion.FieldNodeMinAlpha -> interactor.assertFieldNodeMinAlpha(assertion.nodeId, assertion.minAlpha)
        is CheckAssertion.FieldNodeWcagContrast -> interactor.assertFieldNodeWcagContrast(assertion.nodeId, assertion.minRatio)
        is CheckAssertion.EngineState -> {
            val result = kotlinx.coroutines.runBlocking { engineClient.query(assertion.query) }
            assertTrue(assertion.predicate(result), "EngineState assertion failed for query '${assertion.query}', got: $result")
        }
    }
}
