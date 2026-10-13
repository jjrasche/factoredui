package ai.factoredui.compose.schema

data class TableColumn(val id: String, val label: String, val unit: String?, val kind: String?, val nullDisplay: String?, val isText: Boolean)

data class TableView(val id: String, val label: String, val columns: List<TableColumn>, val rows: List<Map<String, Any?>>)

data class ProfileEntry(val format: Any, val label: String?)

data class DisplayProfile(val units: Map<String, ProfileEntry>, val kinds: Map<String, ProfileEntry>)

data class ColumnOverride(val format: Any?, val label: String?, val isHidden: Boolean, val align: String?)

data class ShownColumn(val id: String, val header: String, val format: Any?, val missing: String, val align: String)

data class TableLayout(val columns: List<ShownColumn>, val notice: String?)

private const val TEXT_KIND = "text"
private const val ALIGN_RIGHT = "right"
private const val ALIGN_LEFT = "left"

private fun columnOf(raw: Any?): TableColumn? {
    val fields = raw as? Map<*, *> ?: return null
    val id = fields["id"] as? String ?: return null
    val kind = fields["kind"] as? String
    val isText = fields["type"] == TEXT_KIND || kind == TEXT_KIND
    return TableColumn(id, fields["label"] as? String ?: id, fields["unit"] as? String, kind, fields["null_display"] as? String, isText)
}

fun readTableView(raw: Any?): TableView? {
    val fields = raw as? Map<*, *> ?: return null
    val id = fields["id"] as? String ?: return null
    val columns = (fields["columns"] as? List<*>)?.mapNotNull(::columnOf) ?: return null
    val rows = (fields["rows"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
    return TableView(id, fields["label"] as? String ?: id, columns, rows)
}

private fun entryOf(raw: Any?): ProfileEntry? = when {
    raw is String -> ProfileEntry(raw, null)
    raw is Map<*, *> && "format" in raw -> raw["format"]?.let { format -> ProfileEntry(format, raw["label"] as? String) }
    raw is Map<*, *> -> ProfileEntry(raw, null)
    else -> null
}

private fun entriesOf(raw: Any?): Map<String, ProfileEntry> =
    (raw as? Map<*, *>).orEmpty().entries.mapNotNull { (key, value) -> entryOf(value)?.let { key.toString() to it } }.toMap()

fun readDisplayProfile(raw: Any?): DisplayProfile {
    val fields = raw as? Map<*, *>
    return DisplayProfile(entriesOf(fields?.get("units")), entriesOf(fields?.get("kinds")))
}

fun readColumnOverrides(raw: Any?): Map<String, ColumnOverride> =
    (raw as? Map<*, *>).orEmpty().entries.mapNotNull { (key, value) ->
        val fields = value as? Map<*, *> ?: return@mapNotNull null
        key.toString() to ColumnOverride(fields["format"], fields["label"] as? String, fields["hidden"] == true, fields["align"] as? String)
    }.toMap()

private fun unitLabelOf(entry: ProfileEntry): String? =
    entry.label ?: ((entry.format as? Map<*, *>)?.get("suffix") as? String)?.trim()?.ifEmpty { null }

private fun profileEntryFor(column: TableColumn, profile: DisplayProfile): ProfileEntry? {
    if (column.isText) return null
    return column.unit?.let(profile.units::get) ?: column.kind?.let(profile.kinds::get)
}

private fun isNumeric(column: TableColumn): Boolean = !column.isText && (column.unit != null || column.kind != null)

private fun shownColumn(column: TableColumn, override: ColumnOverride?, entry: ProfileEntry?, showUnit: Boolean, missing: String): ShownColumn {
    val unitLabel = entry?.let(::unitLabelOf)?.takeIf { showUnit }
    val header = override?.label ?: listOfNotNull(column.label, unitLabel?.let { "($it)" }).joinToString(" ")
    val align = override?.align ?: if (isNumeric(column)) ALIGN_RIGHT else ALIGN_LEFT
    return ShownColumn(column.id, header, override?.format ?: entry?.format, column.nullDisplay ?: missing, align)
}

private fun quoted(names: List<String>): String = names.joinToString { "'$it'" }

private fun unmappedNotice(tableId: String, units: List<String>, kinds: List<String>): String? {
    val parts = listOfNotNull(units.takeIf { it.isNotEmpty() }?.let { "unit(s) ${quoted(it)}" }, kinds.takeIf { it.isNotEmpty() }?.let { "kind(s) ${quoted(it)}" })
    if (parts.isEmpty()) return null
    return "table '$tableId': no display entry for ${parts.joinToString(" and ")}; drawn as plain figures"
}

/** A column's format: its override's, else its unit's entry, else its kind's, else none; what no entry covers is named once. */
fun tableLayout(view: TableView, profile: DisplayProfile, overrides: Map<String, ColumnOverride>, showUnit: Boolean, missing: String): TableLayout {
    val shown = view.columns.filterNot { overrides[it.id]?.isHidden == true }
    val unmapped = shown.filter { isNumeric(it) && overrides[it.id]?.format == null && profileEntryFor(it, profile) == null }
    val units = unmapped.mapNotNull { it.unit }.distinct()
    val kinds = unmapped.filter { it.unit == null }.mapNotNull { it.kind }.distinct()
    val columns = shown.map { shownColumn(it, overrides[it.id], profileEntryFor(it, profile), showUnit, missing) }
    return TableLayout(columns, unmappedNotice(view.id, units, kinds))
}
