package ai.factoredui.compose.schema

data class SlotSize(val dp: Double?, val fault: String?)

private fun figureText(raw: Any?): String = when (raw) {
    is Double -> if (raw.isFinite() && raw % 1.0 == 0.0) raw.toLong().toString() else raw.toString()
    is SpecValue.NumberValue -> figureText(raw.value)
    is SpecValue.StringValue -> raw.value
    is SpecValue.BooleanValue -> raw.value.toString()
    else -> raw.toString()
}

private fun numberIn(raw: Any?): Double? = when (raw) {
    is Number -> raw.toDouble()
    is SpecValue.NumberValue -> raw.value
    else -> null
}

/** A width or height is a finite number of dp above 0; anything else is ignored and named in [SlotSize.fault]. */
fun slotSizeOf(name: String, raw: Any?): SlotSize {
    if (raw == null || raw is SpecValue.NullValue) return SlotSize(null, null)
    val dp = numberIn(raw)?.takeIf { it.isFinite() && it > 0 } ?: return SlotSize(null, "$name must be a number of dp above 0, not '${figureText(raw)}'; it is ignored.")
    return SlotSize(dp, null)
}
