package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.actionRefOf

data class ReadAlongWord(val text: String, val at: Double, val end: Double, val marker: String?, val startsParagraph: Boolean)

data class ReadAlongProps(
    val words: List<ReadAlongWord>,
    val sizeSp: Double?,
    val follows: Boolean,
    val highlightColor: String?,
    val onWordTap: ActionRef?,
    val notices: List<String>,
)

private fun wordOf(index: Int, entry: Any?, notices: MutableList<String>): ReadAlongWord? {
    val fields = entry as? Map<*, *>
    val text = (fields?.get("t") as? String)?.takeIf { it.isNotEmpty() }
    val at = (fields?.get("at") as? Number)?.toDouble()
    val end = (fields?.get("end") as? Number)?.toDouble()
    val problem = when {
        text == null -> "needs a 't' text"
        at == null -> "needs an 'at' number of seconds"
        end == null -> "needs an 'end' number of seconds"
        end < at -> "ends before it starts"
        else -> null
    }
    if (problem != null || text == null || at == null || end == null) {
        notices += "readalong words[$index] $problem; it is left out."
        return null
    }
    return ReadAlongWord(text, at, end, (fields["v"] as? String)?.takeIf { it.isNotEmpty() }, fields["p"] as? Boolean ?: false)
}

fun readReadAlongProps(resolved: Map<String, Any?>, raw: Map<String, SpecValue>): ReadAlongProps {
    val notices = mutableListOf<String>()
    val entries = resolved["words"] as? List<*> ?: emptyList<Any?>()
    val words = entries.mapIndexedNotNull { index, entry -> wordOf(index, entry, notices) }
    if (words.isEmpty() && entries.isEmpty()) notices += "readalong has no words; there is nothing to show."
    return ReadAlongProps(
        words = words,
        sizeSp = (resolved["size"] as? Number)?.toDouble()?.takeIf { it > 0.0 },
        follows = resolved["follow"] as? Boolean ?: true,
        highlightColor = (resolved["highlight_color"] as? String)?.takeIf { it.isNotEmpty() },
        onWordTap = actionRefOf(raw["on_word_tap"]),
        notices = notices,
    )
}

/** The last word that has started by [position], or null before the first one starts. Words are in order of `at`. */
fun litWordIndex(words: List<ReadAlongWord>, position: Double): Int? {
    var low = 0
    var high = words.size - 1
    var lit: Int? = null
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (words[middle].at <= position) {
            lit = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return lit
}
