package ai.factoredui.compose.forcegraph.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom-of-canvas DVR scrubber. Lays out as:
 *
 *   [▶/⏸]  [─────●───]  HH:MM:SS.mmm   N/total   [LIVE | REPLAY]
 *    play    scrub      timestamp      counter   mode toggle
 *
 * The bar is purely presentational — all state lives in [ReplayController];
 * callbacks here are non-suspend so the host composable can wrap each in
 * its own coroutine launch (matching the Mutex-guarded controller API).
 */
@Composable
fun ReplayControlBar(
    events: List<ReplayEvent>,
    cursor: Int,
    isLive: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleLive: () -> Unit,
) {
    val total = events.size
    val cursorTimestamp = if (cursor in events.indices) events[cursor].timestamp else "--:--:--.---"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12141A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play / pause. Disabled visually in live mode (you don't "play"
        // live; you just are live).
        val playColor = if (isLive) Color(0xFF555555) else Color(0xFFD0D0D0)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1C1E26))
                .clickable(enabled = !isLive && total > 0) { onTogglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶",
                color = playColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        // Scrub slider takes most of the row.
        Box(modifier = Modifier.weight(1f)) {
            if (total == 0) {
                Text(
                    text = "no events yet",
                    color = Color(0xFF555555),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else {
                val sliderValue = cursor.coerceAtLeast(0).toFloat()
                Slider(
                    value = sliderValue,
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..((total - 1).coerceAtLeast(0)).toFloat(),
                    steps = if (total > 2) total - 2 else 0,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF74C0FC),
                        activeTrackColor = Color(0xFF4DA3E6),
                        inactiveTrackColor = Color(0x33FFFFFF),
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))

        // Timestamp + counter.
        Text(
            text = cursorTimestamp,
            color = Color(0xFFA0A0B0),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.width(10.dp))
        val counter = if (total == 0) "0/0" else "${(cursor + 1).coerceAtLeast(0)}/$total"
        Text(
            text = counter,
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.width(12.dp))

        // LIVE / REPLAY toggle. Live = green pill; replay = grey pill.
        val (pillBg, pillFg, pillLabel) = if (isLive) {
            Triple(Color(0xFF2B6E3F), Color(0xFFD8FFE0), "LIVE")
        } else {
            Triple(Color(0xFF3A3D45), Color(0xFFC8CACF), "REPLAY")
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(pillBg)
                .clickable { onToggleLive() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = pillLabel,
                color = pillFg,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
