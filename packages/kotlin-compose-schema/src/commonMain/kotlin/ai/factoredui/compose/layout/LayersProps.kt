package ai.factoredui.compose.layout

data class Layer(val label: String, val size: Double, val sizeText: String, val note: String?, val color: String?)

data class LayersProps(val layers: List<Layer>, val unit: String, val minShare: Double, val notices: List<String>)

private const val DEFAULT_MIN_SHARE = 0.04

private fun sizeTextOf(fields: Map<*, *>, size: Double, unit: String): String {
    (fields["size_text"] as? String)?.takeIf { it.isNotEmpty() }?.let { return it }
    val number = if (size % 1.0 == 0.0) size.toLong().toString() else size.toString()
    return if (unit.isEmpty()) number else "$number $unit"
}

private fun layerOf(index: Int, entry: Any?, unit: String, notices: MutableList<String>): Layer? {
    val fields = entry as? Map<*, *>
    val label = (fields?.get("label") as? String)?.takeIf { it.isNotEmpty() }
    val size = (fields?.get("size") as? Number)?.toDouble()
    return when {
        fields == null || label == null -> null.also { notices += "layers layers[$index] needs a 'label'; it is left out." }
        size == null || size <= 0.0 -> null.also { notices += "layers layers[$index] needs a 'size' above zero; it is left out." }
        else -> Layer(label, size, sizeTextOf(fields, size, unit), (fields["note"] as? String)?.takeIf { it.isNotEmpty() }, (fields["color"] as? String)?.takeIf { it.isNotEmpty() })
    }
}

fun readLayersProps(resolved: Map<String, Any?>): LayersProps {
    val notices = mutableListOf<String>()
    val unit = (resolved["unit"] as? String).orEmpty()
    val entries = resolved["layers"] as? List<*> ?: emptyList<Any?>()
    val layers = entries.mapIndexedNotNull { index, entry -> layerOf(index, entry, unit, notices) }
    if (entries.isEmpty()) notices += "layers has no layers; there is nothing to show."
    val minShare = (resolved["min_share"] as? Number)?.toDouble()?.takeIf { it in 0.0..0.2 } ?: DEFAULT_MIN_SHARE
    return LayersProps(layers, unit, minShare, notices)
}

/** Each layer's share of the strip: in proportion to its size, with none below [minShare], and adding up to one. */
fun layerFractions(layers: List<Layer>, minShare: Double): List<Double> {
    val total = layers.sumOf { it.size }
    if (total <= 0.0) return emptyList()
    val proportional = layers.map { it.size / total }
    val thin = proportional.indices.filter { proportional[it] < minShare }
    if (thin.isEmpty()) return proportional
    val spare = 1.0 - minShare * thin.size
    val thickTotal = proportional.indices.filter { it !in thin }.sumOf { proportional[it] }
    return proportional.indices.map { if (it in thin) minShare else proportional[it] / thickTotal * spare }
}
