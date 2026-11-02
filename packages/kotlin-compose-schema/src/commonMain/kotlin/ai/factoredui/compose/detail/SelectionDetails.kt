package ai.factoredui.compose.detail

/** One line of a detail card: a [label], its [value] (null reads as a dash), and an optional [note] saying where the value comes from. */
data class DetailRow(val label: String, val value: String?, val note: String?)

data class DetailSection(val heading: String?, val rows: List<DetailRow>)

/** What a card says about one selectable thing. */
data class SelectionDetail(val title: String, val sections: List<DetailSection>)

private fun textIn(fields: Map<*, *>, key: String): String? = (fields[key] as? String)?.takeIf { it.isNotEmpty() }

private fun rowOf(raw: Any?): DetailRow? {
    val fields = raw as? Map<*, *> ?: return null
    val label = textIn(fields, "label") ?: return null
    return DetailRow(label, fields["value"]?.let { if (it is String) it else it.toString() }, textIn(fields, "note"))
}

private fun sectionOf(raw: Any?): DetailSection? {
    val fields = raw as? Map<*, *> ?: return null
    val rows = (fields["rows"] as? List<*>).orEmpty().mapNotNull(::rowOf)
    return DetailSection(textIn(fields, "heading"), rows)
}

/** Reads `details`: a map from a selectable id to `{ title, sections: [{ heading, rows: [{ label, value, note }] }] }`. An entry without a title is left out. */
fun readSelectionDetails(raw: Any?): Map<String, SelectionDetail> =
    (raw as? Map<*, *>).orEmpty().mapNotNull { (key, body) ->
        val id = key as? String ?: return@mapNotNull null
        val fields = body as? Map<*, *> ?: return@mapNotNull null
        val title = textIn(fields, "title") ?: return@mapNotNull null
        id to SelectionDetail(title, (fields["sections"] as? List<*>).orEmpty().mapNotNull(::sectionOf))
    }.toMap()
