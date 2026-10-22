package ai.factoredui.compose.schema

fun actionRefOf(raw: SpecValue?): ActionRef? {
    val fields = (raw as? SpecValue.ObjectValue)?.value ?: return null
    val action = (fields["action"] as? SpecValue.StringValue)?.value ?: return null
    return ActionRef(action, (fields["params"] as? SpecValue.ObjectValue)?.value.orEmpty())
}
