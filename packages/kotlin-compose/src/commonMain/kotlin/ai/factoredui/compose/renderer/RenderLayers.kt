package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.Layer
import ai.factoredui.compose.layout.layerFractions
import ai.factoredui.compose.layout.readLayersProps
import ai.factoredui.compose.schema.SpecNode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val STRIP_HEIGHT_DP = 40
private const val SWATCH_DP = 16

private fun colourOfLayer(theme: SpecTheme, layer: Layer, index: Int): Color =
    layer.color?.let { theme.tokenColor(it) ?: it.parseColor() } ?: theme.series[index % theme.series.size]

private fun lineOf(layer: Layer): String = listOfNotNull(layer.label, layer.sizeText, layer.note).joinToString(" ")

@Composable
internal fun RenderLayers(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val props = readLayersProps(resolvedProps)
    val theme = LocalSpecTheme.current
    val shares = layerFractions(props.layers, props.minShare)
    RecordDrawn {
        text = props.layers.joinToString(" | ", transform = ::lineOf)
        shownProps = mapOf("shares" to shares.joinToString(",") { "%.3f".format(it) })
        notices = props.notices
    }
    Column(verticalArrangement = Arrangement.spacedBy(theme.space.sm.dp), modifier = Modifier.fillMaxWidth().nodeTag(node.id)) {
        Row(modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT_DP.dp).clip(RoundedCornerShape(theme.radius.sm.dp))) {
            props.layers.forEachIndexed { index, layer ->
                Box(modifier = Modifier.weight(shares[index].toFloat()).height(STRIP_HEIGHT_DP.dp).background(colourOfLayer(theme, layer, index)))
            }
        }
        props.layers.forEachIndexed { index, layer ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.space.sm.dp)) {
                Box(modifier = Modifier.size(SWATCH_DP.dp).clip(RoundedCornerShape(theme.radius.sm.dp)).background(colourOfLayer(theme, layer, index)))
                Text(layer.label, style = MaterialTheme.typography.bodyMedium, color = theme.ink)
                Text(layer.sizeText, style = theme.monoStyle(), color = theme.muted)
                layer.note?.let { Text(it, style = theme.monoStyle(), color = theme.muted) }
            }
        }
    }
}
