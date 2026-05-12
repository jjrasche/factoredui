package ai.factoredui.compose.schema

/**
 * Resolves binding references against a data context.
 *
 * Binding refs are strings matching "{path.to.value}".
 * Resolution is single-level path lookup — no expressions, no arithmetic.
 * Matches the behaviour of binding.ts in @factoredui/core.
 */
object BindingResolver {

    private val BINDING_PATTERN = Regex("""^\{([a-zA-Z_][a-zA-Z0-9_.]*)\}$""")
    private val INLINE_BINDING_PATTERN = Regex("""\{([a-zA-Z_][a-zA-Z0-9_.]*)\}""")

    fun isBindingRef(value: String): Boolean = BINDING_PATTERN.matches(value)

    /** Resolve a full binding ref like "{shell.inputText}" against context. */
    fun resolveBinding(ref: String, context: Map<String, Any?>): Any? {
        val match = BINDING_PATTERN.find(ref) ?: return ref
        return resolvePath(match.groupValues[1], context)
    }

    /** Interpolate inline bindings within a string, e.g. "Hello {user.name}!" */
    fun resolveText(text: String, context: Map<String, Any?>): String =
        INLINE_BINDING_PATTERN.replace(text) { match ->
            resolvePath(match.groupValues[1], context)?.toString() ?: ""
        }

    /** Resolve a SpecValue against context, replacing binding refs with live values. */
    fun resolveValue(value: SpecValue, context: Map<String, Any?>): Any? = when (value) {
        is SpecValue.StringValue -> {
            val s = value.value
            when {
                isBindingRef(s) -> resolveBinding(s, context)
                s.contains("{") -> resolveText(s, context)
                else -> s
            }
        }
        is SpecValue.NumberValue -> value.value
        is SpecValue.BooleanValue -> value.value
        is SpecValue.NullValue -> null
        is SpecValue.NodeValue -> value.value
        is SpecValue.ArrayValue -> value.value.map { resolveValue(it, context) }
        is SpecValue.ObjectValue -> value.value.mapValues { resolveValue(it.value, context) }
    }

    /**
     * Resolve all props in a map against context.
     * Returns a plain map with resolved values for the renderer to consume.
     */
    fun resolveProps(
        props: Map<String, SpecValue>,
        context: Map<String, Any?>,
    ): Map<String, Any?> = props.mapValues { resolveValue(it.value, context) }

    /**
     * Walk a dot-separated path into a nested map / list structure.
     *
     * Numeric segments (`{items.0.name}`) index into Lists so list templates
     * can reference positional items. Matches binding.ts which gets list
     * indexing for free from JS bracket-access semantics.
     */
    private fun resolvePath(path: String, context: Map<String, Any?>): Any? {
        val segments = path.split(".")
        var current: Any? = context
        for (segment in segments) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                is List<*> -> segment.toIntOrNull()?.let { index ->
                    if (index in current.indices) current[index] else null
                }
                else -> return null
            }
        }
        return current
    }

    /** Evaluate the `visible` binding — node is visible when value is truthy. */
    fun isVisible(visibleRef: String?, context: Map<String, Any?>): Boolean {
        if (visibleRef == null) return true
        val value = resolveBinding(visibleRef, context)
        return when (value) {
            null -> false
            is Boolean -> value
            is String -> value.isNotEmpty() && value != "false"
            is Number -> value.toDouble() != 0.0
            else -> true
        }
    }
}
