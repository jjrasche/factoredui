package ai.factoredui.playground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

private val DROP_BONES = listOf(
    1 to 0, 2 to 0, 3 to 0, 4 to 1, 5 to 2, 6 to 3, 7 to 4, 8 to 5, 9 to 6,
    10 to 7, 11 to 8, 12 to 9, 13 to 9, 14 to 9, 15 to 12, 16 to 13, 17 to 14,
    18 to 16, 19 to 17, 20 to 18, 21 to 19,
)
private const val PELVIS = 0
private const val L_HIP = 1
private const val R_HIP = 2
private const val L_KNEE = 4
private const val R_KNEE = 5
private const val L_ANKLE = 7
private const val R_ANKLE = 8
private const val L_WRIST = 20
private const val R_WRIST = 21

private val STIFF_AMBER = Color(0xFFE0A84E)

private class DropFrame(val joints: List<FloatArray>)
private class DropVerdict(val line: String, val stiff: Boolean)

private fun parseDropStream(text: String): List<DropFrame> = runCatching {
    Json.parseToJsonElement(text).jsonObject["frames"]!!.jsonArray.map { frameEl ->
        DropFrame(frameEl.jsonObject["joints"]!!.jsonArray.map { j ->
            j.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
        })
    }
}.getOrDefault(emptyList())

private fun jointAngleDeg(a: FloatArray, b: FloatArray, c: FloatArray): Float {
    val ba = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    val bc = floatArrayOf(c[0] - b[0], c[1] - b[1], c[2] - b[2])
    val magA = sqrt(ba[0] * ba[0] + ba[1] * ba[1] + ba[2] * ba[2])
    val magC = sqrt(bc[0] * bc[0] + bc[1] * bc[1] + bc[2] * bc[2])
    if (magA == 0f || magC == 0f) return 180f
    val cos = ((ba[0] * bc[0] + ba[1] * bc[1] + ba[2] * bc[2]) / (magA * magC)).coerceIn(-1f, 1f)
    return acos(cos) * 180f / 3.14159265f
}

private fun kneeAngle(f: DropFrame): Float =
    (jointAngleDeg(f.joints[L_HIP], f.joints[L_KNEE], f.joints[L_ANKLE]) +
        jointAngleDeg(f.joints[R_HIP], f.joints[R_KNEE], f.joints[R_ANKLE])) / 2f

private fun deriveVerdict(frames: List<DropFrame>): DropVerdict {
    if (frames.size < 2) return DropVerdict("no drop data", false)
    val pelvisY = frames.map { it.joints[PELVIS][1] }
    val fell = (pelvisY.max() - pelvisY.min()) >= 0.20f
    val fallHalf = frames.take((frames.size / 2).coerceAtLeast(1))
    val armSpread = fallHalf.map { abs(it.joints[L_WRIST][0] - it.joints[R_WRIST][0]) }.average().toFloat()
    val braced = armSpread >= 0.25f
    val settled = pelvisY.takeLast(6).zipWithNext { a, b -> abs(b - a) }.all { it < 0.02f }
    val peakKneeVel = frames.map { kneeAngle(it) }.zipWithNext { a, b -> abs(b - a) }.maxOrNull() ?: 0f
    val buckled = peakKneeVel >= 20f
    val passed = buildList {
        if (fell) add("fell"); if (braced) add("braced"); if (settled) add("landed")
    }.joinToString(" · ").ifEmpty { "nothing" }
    val band = if (fell && braced && settled) "PASS" else "FAIL"
    val buckleNote = if (buckled) "buckle present — knees gave" else "buckle — none; knees hold straight (stiff)"
    val line = "$passed — $band        $buckleNote"
    return DropVerdict(line, !buckled)
}

@Composable
fun DropLongExposure(streamUrl: String) {
    var frames by remember { mutableStateOf<List<DropFrame>>(emptyList()) }
    var verdict by remember { mutableStateOf(DropVerdict("loading drop…", false)) }
    var playing by remember { mutableStateOf(false) }
    var currentFrame by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(streamUrl) {
        val text = runCatching { loadSpecText(streamUrl) }.getOrNull()
        if (text != null) {
            val parsed = parseDropStream(text)
            frames = parsed
            verdict = deriveVerdict(parsed)
        }
    }
    Column(Modifier.fillMaxSize().padding(StageTokens.gapMd)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StageTokens.gapMd),
        ) {
            Text("Drop — long exposure", color = StageTokens.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Button(onClick = {
                if (!playing && frames.size > 1) {
                    playing = true
                    scope.launch {
                        for (i in frames.indices) { currentFrame = i; delay(34) }
                        playing = false
                    }
                }
            }) { Text(if (playing) "Replaying…" else "Replay the drop") }
        }
        Text(
            verdict.line,
            color = if (verdict.stiff) STIFF_AMBER else StageTokens.accent,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = StageTokens.gapSm),
        )
        Text(
            if (verdict.stiff)
                "The machine is satisfied — fell, braced, landed. Your eye sees the lie: the amber knees hold straight through impact. A person's would buckle."
            else
                "Fell, braced, landed — and the knees gave like a person's.",
            color = StageTokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = StageTokens.gapXs),
        )
        Box(Modifier.fillMaxWidth().padding(top = StageTokens.gapMd).then(Modifier.fillMaxSize())) {
            Canvas(Modifier.fillMaxSize()) {
                val fr = frames
                if (fr.size < 2) return@Canvas
                var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                for (f in fr) for (j in f.joints) {
                    if (j[0] < minX) minX = j[0]; if (j[0] > maxX) maxX = j[0]
                    if (j[1] < minY) minY = j[1]; if (j[1] > maxY) maxY = j[1]
                }
                val pad = 28f
                val w = size.width - pad * 2; val h = size.height - pad * 2
                val spanX = (maxX - minX).coerceAtLeast(0.001f); val spanY = (maxY - minY).coerceAtLeast(0.001f)
                val scale = minOf(w / spanX, h / spanY)
                val offX = pad + (w - spanX * scale) / 2f
                val offY = pad + (h - spanY * scale) / 2f
                fun project(j: FloatArray): Offset =
                    Offset(offX + (j[0] - minX) * scale, offY + (maxY - j[1]) * scale)
                val n = fr.size
                val upTo = if (playing) currentFrame.coerceIn(0, fr.lastIndex) else fr.lastIndex
                val floorY = offY + (maxY - minY) * scale
                drawLine(StageTokens.textMuted.copy(alpha = 0.3f), Offset(pad, floorY), Offset(size.width - pad, floorY), strokeWidth = 1f)
                for (idx in 0..upTo) {
                    val f = fr[idx]
                    val ramp = (0.05f + 0.8f * (idx.toFloat() / (n - 1))).coerceIn(0f, 1f)
                    val live = playing && idx == upTo
                    val a = if (live) 0.95f else ramp
                    val bone = StageTokens.textPrimary.copy(alpha = a * 0.5f)
                    for ((c, p) in DROP_BONES) {
                        drawLine(bone, project(f.joints[c]), project(f.joints[p]), strokeWidth = if (live) 2f else 1.5f)
                    }
                    val knee = (if (verdict.stiff) STIFF_AMBER else StageTokens.accent).copy(alpha = a)
                    drawCircle(knee, radius = if (live) 4.5f else 3.5f, center = project(f.joints[L_KNEE]))
                    drawCircle(knee, radius = if (live) 4.5f else 3.5f, center = project(f.joints[R_KNEE]))
                }
                for (i in 0 until upTo) {
                    drawLine(StageTokens.accent.copy(alpha = 0.85f), project(fr[i].joints[PELVIS]), project(fr[i + 1].joints[PELVIS]), strokeWidth = 2.5f)
                }
            }
        }
    }
}
