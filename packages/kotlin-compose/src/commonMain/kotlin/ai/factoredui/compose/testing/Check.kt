package ai.factoredui.compose.testing

data class Check(
    val id: String,
    val steps: List<CheckStep>,
)

sealed class CheckStep {
    data class Tap(val nodeId: String) : CheckStep()
    data class TapFieldNode(val nodeId: String) : CheckStep()
    data class DragFieldNodeToCenter(val nodeId: String) : CheckStep()
    data class DragFieldNode(val nodeId: String, val toX: Float, val toY: Float) : CheckStep()
    data class AdvanceTime(val seconds: Float) : CheckStep()
    data class Await(val condition: AwaitCondition) : CheckStep()
    data class Expect(val assertion: CheckAssertion) : CheckStep()
}

sealed class AwaitCondition {
    data class FieldNodePresent(val nodeId: String) : AwaitCondition()
    data class NodeExists(val nodeId: String) : AwaitCondition()
}

sealed class CheckAssertion {
    data class FieldNodePresent(val nodeId: String) : CheckAssertion()
    data class FieldNodeAbsent(val nodeId: String) : CheckAssertion()
    data class ActionFired(val action: String, val params: Map<String, String> = emptyMap()) : CheckAssertion()
    data class NoActionFired(val unused: Unit = Unit) : CheckAssertion()
    data class DragMagnitude(val nodeId: String, val min: Float) : CheckAssertion()
    data class FieldNodeAgeSecs(val nodeId: String, val minSecs: Float) : CheckAssertion()
    data class FieldNodeMinAlpha(val nodeId: String, val minAlpha: Float) : CheckAssertion()
    data class EngineState(val query: String, val predicate: (Any?) -> Boolean) : CheckAssertion()
}

fun check(id: String, block: CheckBuilder.() -> Unit): Check =
    CheckBuilder(id).apply(block).build()

class CheckBuilder(private val id: String) {
    private val steps = mutableListOf<CheckStep>()

    fun tap(nodeId: String) { steps += CheckStep.Tap(nodeId) }
    fun tapFieldNode(nodeId: String) { steps += CheckStep.TapFieldNode(nodeId) }
    fun dragFieldNodeToCenter(nodeId: String) { steps += CheckStep.DragFieldNodeToCenter(nodeId) }
    fun dragFieldNode(nodeId: String, toX: Float, toY: Float) { steps += CheckStep.DragFieldNode(nodeId, toX, toY) }
    fun advanceTime(seconds: Float) { steps += CheckStep.AdvanceTime(seconds) }
    fun await(condition: AwaitCondition) { steps += CheckStep.Await(condition) }
    fun expect(assertion: CheckAssertion) { steps += CheckStep.Expect(assertion) }

    fun awaitFieldNode(nodeId: String) = await(AwaitCondition.FieldNodePresent(nodeId))
    fun expectFieldNodePresent(nodeId: String) = expect(CheckAssertion.FieldNodePresent(nodeId))
    fun expectFieldNodeAbsent(nodeId: String) = expect(CheckAssertion.FieldNodeAbsent(nodeId))
    fun expectActionFired(action: String, params: Map<String, String> = emptyMap()) =
        expect(CheckAssertion.ActionFired(action, params))
    fun expectNoActionFired() = expect(CheckAssertion.NoActionFired())
    fun expectDragMagnitude(nodeId: String, min: Float) = expect(CheckAssertion.DragMagnitude(nodeId, min))
    fun expectFieldNodeAgeSecs(nodeId: String, minSecs: Float) = expect(CheckAssertion.FieldNodeAgeSecs(nodeId, minSecs))
    fun expectFieldNodeMinAlpha(nodeId: String, minAlpha: Float) = expect(CheckAssertion.FieldNodeMinAlpha(nodeId, minAlpha))

    fun build() = Check(id, steps.toList())
}
