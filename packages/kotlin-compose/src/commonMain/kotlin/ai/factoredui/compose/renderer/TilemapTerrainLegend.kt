package ai.factoredui.compose.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val LEGEND_BACKDROP_ALPHA = 0.88f
private val LEGEND_TITLE = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
private val LEGEND_LINE = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)

@Composable
internal fun TerrainLegendCard(pass: TerrainPass, tag: String, modifier: Modifier = Modifier) {
    val legend = pass.legend ?: return
    val theme = LocalSpecTheme.current
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier.padding(10.dp).nodeTag(tag)
            .background(theme.ground.copy(alpha = LEGEND_BACKDROP_ALPHA), shape)
            .widthIn(max = 260.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(legend.title, style = LEGEND_TITLE.copy(color = theme.ink))
        if (legend.ramp.isNotEmpty()) {
            Box(Modifier.size(180.dp, 10.dp).background(Brush.horizontalGradient(legend.ramp.map { Color(it) }), RoundedCornerShape(2.dp)))
        }
        Text(legend.lowLabel, style = LEGEND_LINE.copy(color = theme.ink))
        Text(legend.highLabel, style = LEGEND_LINE.copy(color = theme.ink))
        for (note in legend.notes) Text(note, style = LEGEND_LINE.copy(color = theme.muted))
    }
}
