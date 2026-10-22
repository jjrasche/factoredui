package ai.factoredui.compose.renderer

import ai.factoredui.compose.format.MISSING_FIGURE
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.displayedText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val STATUS_BAR_DP = 4
private const val UNIT_BASELINE_LIFT_DP = 6

private class StatusMark(val colour: Color, val word: String)

private fun statusMarkOf(theme: SpecTheme, name: String?, text: String?): StatusMark? = when (name) {
    "pass" -> StatusMark(theme.pass, text ?: "Pass")
    "warn" -> StatusMark(theme.warn, text ?: "Warn")
    "fail" -> StatusMark(theme.fail, text ?: "Fail")
    else -> null
}

@Composable
internal fun RenderStat(node: SpecNode, resolvedProps: Map<String, Any?>) {
    val theme = LocalSpecTheme.current
    val label = resolvedProps["label"] as? String ?: ""
    val missing = resolvedProps["missing"] as? String ?: MISSING_FIGURE
    val value = if (resolvedProps["value"] == null) missing else displayedText(resolvedProps["value"], resolvedProps["format"], missing)
    val unit = (resolvedProps["unit"] as? String).orEmpty()
    val named = (resolvedProps["status"] as? String)?.takeIf { it.isNotEmpty() }
    val status = statusMarkOf(theme, named, (resolvedProps["status_text"] as? String)?.takeIf { it.isNotEmpty() })
    val notice = if (named != null && status == null) "stat status must be pass, warn or fail, not '$named'; it is drawn without one." else null
    RecordDrawn {
        text = listOf(label, value, unit).filter { it.isNotEmpty() }.joinToString(" ")
        shownProps = status?.let { mapOf("status_shown" to it.word) }.orEmpty()
        notices = listOfNotNull(notice)
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(theme.radius.md.dp)).background(theme.surface).nodeTag(node.id)) {
        status?.let { Box(modifier = Modifier.width(STATUS_BAR_DP.dp).fillMaxHeight().background(it.colour)) }
        Column(modifier = Modifier.padding(theme.space.lg.dp), verticalArrangement = Arrangement.spacedBy(theme.space.xs.dp)) {
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = theme.muted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(theme.space.sm.dp)) {
                Text(value, style = theme.displayMonoStyle(), color = theme.ink, maxLines = 1)
                if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.bodyMedium, color = theme.muted, modifier = Modifier.padding(bottom = UNIT_BASELINE_LIFT_DP.dp))
            }
            status?.let { Text(it.word, style = MaterialTheme.typography.labelMedium, color = it.colour) }
        }
    }
}
