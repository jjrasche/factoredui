package ai.factoredui.compose.schema

/** A list's rows narrowed by `where` ({field, in}, or a list of them that must all hold) and then ordered by `order` ([{field, rank}]); both arrive with their bindings already resolved. */
fun selectListRows(rows: List<Any?>, where: Any?, order: Any?): List<Any?> =
    orderedRows(rows.filter { keepsRow(it, where) }, rankKeysOf(order))

private data class RankKey(val field: String, val rank: List<String>)

private fun keepsRow(row: Any?, where: Any?): Boolean = when (where) {
    is List<*> -> where.all { keepsRowOnCondition(row, it) }
    else -> keepsRowOnCondition(row, where)
}

private fun keepsRowOnCondition(row: Any?, where: Any?): Boolean {
    val condition = where as? Map<*, *> ?: return true
    val field = condition["field"]?.toString().orEmpty()
    val allowed = allowedTexts(condition["in"])
    if (field.isEmpty() || allowed.isEmpty()) return true
    return textAt(row, field) in allowed
}

private fun allowedTexts(raw: Any?): List<String> = when (raw) {
    null -> emptyList()
    is List<*> -> raw.map(::valueText)
    else -> valueText(raw).takeIf { it.isNotEmpty() }?.let(::listOf) ?: emptyList()
}

private fun rankKeysOf(order: Any?): List<RankKey> = (order as? List<*>).orEmpty().mapNotNull { entry ->
    val key = entry as? Map<*, *> ?: return@mapNotNull null
    val field = key["field"]?.toString().orEmpty()
    if (field.isEmpty()) null else RankKey(field, (key["rank"] as? List<*>).orEmpty().map(::valueText))
}

private fun orderedRows(rows: List<Any?>, keys: List<RankKey>): List<Any?> =
    if (keys.isEmpty()) rows else rows.sortedWith(compareBy(*keys.map { key -> { row: Any? -> rankOf(row, key) } }.toTypedArray()))

private fun rankOf(row: Any?, key: RankKey): Int = key.rank.indexOf(textAt(row, key.field)).let { if (it < 0) key.rank.size else it }

private fun textAt(row: Any?, path: String): String = valueText(path.split('.').fold(row) { node, name -> (node as? Map<*, *>)?.get(name) })

private fun valueText(value: Any?): String = when {
    value == null -> ""
    value is Number && value.toDouble() == value.toLong().toDouble() -> value.toLong().toString()
    else -> value.toString()
}
