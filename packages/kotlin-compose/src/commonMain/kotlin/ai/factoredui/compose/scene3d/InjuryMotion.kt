package ai.factoredui.compose.scene3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// Client-side port of il-injury's injury loop (board HANDOFF 2026-06-15). Math runs in the clip's
// native joint frame so il-injury's axis conventions hold; RenderScene3d applies Z-up→Y-up only at
// render time. The scipy optimizer stays offline as ground truth; this is the fast version.

typealias Frame = List<List<Float>>

private const val PELVIS = 0

private val PARENTS = intArrayOf(
    -1, 0, 1, 2, 3, 0, 5, 6, 7, 0, 9, 10, 11, 12, 11, 14, 15, 16, 11, 18, 19, 20, 17, 21,
)

private data class Chain(val shoulder: Int, val elbow: Int, val wrist: Int, val tip: Int)

private fun effectorChain(name: String): Chain = when (name) {
    "L_Hand" -> Chain(14, 16, 17, 22)
    "R_Foot" -> Chain(5, 6, 7, 8)
    "L_Foot" -> Chain(1, 2, 3, 4)
    else -> Chain(18, 20, 21, 23)
}

private fun v(a: List<Float>) = floatArrayOf(a.getOrElse(0) { 0f }, a.getOrElse(1) { 0f }, a.getOrElse(2) { 0f })
private fun sub(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
private fun add(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])
private fun scale(a: FloatArray, s: Float) = floatArrayOf(a[0] * s, a[1] * s, a[2] * s)
private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
private fun len(a: FloatArray) = sqrt(dot(a, a))
private fun norm(a: FloatArray): FloatArray { val l = len(a); return if (l < 1e-6f) floatArrayOf(0f, 0f, 0f) else scale(a, 1f / l) }
private fun cross(a: FloatArray, b: FloatArray) =
    floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

private fun minJerk(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u * u * (10f - 15f * u + 6f * u * u)
}

private fun lerpJoints(a: Frame, b: Frame, f: Float): Frame =
    a.indices.map { j ->
        val pa = v(a[j]); val pb = v(b.getOrElse(j) { a[j] })
        listOf(pa[0] + (pb[0] - pa[0]) * f, pa[1] + (pb[1] - pa[1]) * f, pa[2] + (pb[2] - pa[2]) * f)
    }

private fun sampleAt(frames: List<Frame>, progress: Float): Frame {
    if (frames.isEmpty()) return emptyList()
    val idx = (progress.coerceIn(0f, 1f) * (frames.size - 1))
    val lo = idx.toInt(); val hi = minOf(lo + 1, frames.size - 1)
    return lerpJoints(frames[lo], frames[hi], idx - lo)
}

private fun solveLimbReach(rest: Frame, chain: Chain, target: FloatArray): Frame {
    val s = v(rest[chain.shoulder])
    val l1 = len(sub(v(rest[chain.elbow]), s))
    val l2 = len(sub(v(rest[chain.wrist]), v(rest[chain.elbow])))
    val toTarget = sub(target, s)
    val d = len(toTarget).coerceIn(kotlin.math.abs(l1 - l2) + 1e-3f, l1 + l2 - 1e-3f)
    val dir = norm(toTarget)
    val cosA = ((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f)
    val a = acos(cosA)
    val restElbowDir = norm(sub(v(rest[chain.elbow]), s))
    var bendAxis = cross(dir, restElbowDir)
    if (len(bendAxis) < 1e-4f) bendAxis = cross(dir, floatArrayOf(0f, 1f, 0f))
    bendAxis = norm(bendAxis)
    val perp = norm(cross(bendAxis, dir))
    val elbow = add(s, add(scale(dir, l1 * cos(a)), scale(perp, l1 * sin(a))))
    val wristDir = norm(sub(target, elbow))
    val wrist = add(elbow, scale(wristDir, l2))
    val tipOffset = sub(v(rest[chain.tip]), v(rest[chain.wrist]))
    val tip = add(wrist, scale(wristDir, len(tipOffset)))
    val solved = rest.map { it.toList() }.toMutableList()
    solved[chain.elbow] = listOf(elbow[0], elbow[1], elbow[2])
    solved[chain.wrist] = listOf(wrist[0], wrist[1], wrist[2])
    solved[chain.tip] = listOf(tip[0], tip[1], tip[2])
    return solved
}

// The STATIC destination: body stood at the goal's ground position with the effector reaching the
// target point. A ghost reference so you can see whether the walk/reach actually honors the target.
fun targetPose(rest: Frame, effector: String, target: List<Float>): Frame {
    if (rest.size < 24) return rest
    val dx = target.getOrElse(0) { 0f } - rest[PELVIS].getOrElse(0) { 0f }
    val dy = target.getOrElse(1) { 0f } - rest[PELVIS].getOrElse(1) { 0f }
    val stood = rest.map { listOf(it.getOrElse(0) { 0f } + dx, it.getOrElse(1) { 0f } + dy, it.getOrElse(2) { 0f }) }
    return solveLimbReach(stood, effectorChain(effector), v(target))
}

fun healthyReach(rest: Frame, effector: String, target: List<Float>, baseFrames: Int = 28): List<Frame> {
    if (rest.size < 24) return listOf(rest)
    val solved = solveLimbReach(rest, effectorChain(effector), v(target))
    return (0 until baseFrames).map { i ->
        lerpJoints(rest, solved, minJerk(i.toFloat() / (baseFrames - 1).coerceAtLeast(1)))
    }
}

// Client-side injured walk-to-target: the body LOCOMOTES across the ground toward the goal with a
// limping gait, guards the trunk, and stalls short (immobility) when badly hurt. Fast preview tuned
// against il-injury's optimizer (their validated characterization swaps in). Clip-native frame:
// Z is up, X/Y the ground.
fun injuredWalk(rest: Frame, target: List<Float>, severity: Float, baseFrames: Int = 44): List<Frame> {
    if (rest.size < 24) return listOf(rest)
    val sev = severity.coerceIn(0f, 1f)
    val start = v(rest[PELVIS])
    val gx = target.getOrElse(0) { 0f } - start[0]
    val gy = target.getOrElse(1) { 0f } - start[1]
    val dist = sqrt(gx * gx + gy * gy)
    val dirX = if (dist > 1e-4f) gx / dist else 0f
    val dirY = if (dist > 1e-4f) gy / dist else 1f
    val travelFrac = 1f - 0.45f * sev * sev
    val nSteps = (dist / (0.5f - 0.28f * sev)).toInt().coerceIn(2, 12)
    val frames = (baseFrames * (1f + 2.2f * sev)).toInt().coerceAtLeast(10)
    val twoPi = 2f * PI.toFloat()
    val yaw = atan2(dirX, dirY)
    val cosY = cos(yaw); val sinY = sin(yaw)
    val pelvis0 = v(rest[PELVIS])
    val faced = rest.map { joint ->
        val rx = joint.getOrElse(0) { 0f } - pelvis0[0]
        val ry = joint.getOrElse(1) { 0f } - pelvis0[1]
        listOf(pelvis0[0] + rx * cosY - ry * sinY, pelvis0[1] + rx * sinY + ry * cosY, joint.getOrElse(2) { 0f })
    }
    val out = mutableListOf<Frame>()
    for (i in 0 until frames) {
        val p = minJerk(i.toFloat() / (frames - 1)) * travelFrac
        val phase = p * nSteps * twoPi
        val bob = abs(sin(phase)) * 0.03f
        val tgx = gx * p; val tgy = gy * p
        val walked = faced.mapIndexed { j, joint ->
            var x = joint.getOrElse(0) { 0f } + tgx
            var y = joint.getOrElse(1) { 0f } + tgy
            var z = joint.getOrElse(2) { 0f } + bob
            val legPhase = when {
                j in 1..4 -> sin(phase)
                j in 5..8 -> sin(phase + PI.toFloat())
                else -> Float.NaN
            }
            if (!legPhase.isNaN()) {
                val lift = if (legPhase > 0f) legPhase else 0f
                val stepHeight = if (j in 5..8) 0.08f * (1f - 0.85f * sev) else 0.08f
                z += lift * stepHeight
                val swing = legPhase * (0.14f * (1f - 0.45f * sev))
                x += dirX * swing; y += dirY * swing
            }
            listOf(x, y, z)
        }
        val pelvis = v(walked[PELVIS])
        out.add(walked.mapIndexed { j, joint -> if (j in 9..23) hunchPoint(v(joint), pelvis, 0.3f * sev).toList() else joint })
    }
    return out
}

private fun burstWarpProgress(severity: Float): List<Float> {
    val out = mutableListOf(0f)
    var progress = 0f
    var guard = 0
    while (progress < 0.998f && guard < 4000) {
        val gain = (0.6f - 0.46f * severity * minOf(1f, progress + 0.25f)).coerceIn(0.03f, 0.95f)
        val nBurst = (2 + 11 * severity).toInt().coerceAtLeast(1)
        val start = progress
        val end = (progress + gain * (1f - progress)).coerceAtMost(1f)
        for (i in 1..nBurst) out.add(start + (end - start) * minJerk(i.toFloat() / nBurst))
        progress = end
        guard++
    }
    out.add(1f)
    return out
}

private fun hunchPoint(p: FloatArray, pelvis: FloatArray, theta: Float): FloatArray {
    val r = sub(p, pelvis)
    val c = cos(theta); val s = sin(theta)
    return add(pelvis, floatArrayOf(r[0] * c + r[2] * s, r[1], -r[0] * s + r[2] * c))
}

fun guardTransform(baseReach: List<Frame>, severity: Float): List<Frame> {
    if (baseReach.isEmpty()) return baseReach
    val sev = severity.coerceIn(0f, 1f)
    val progresses = burstWarpProgress(sev)
    return progresses.mapIndexed { i, p ->
        val frame = sampleAt(baseReach, p)
        val progress = i.toFloat() / (progresses.size - 1).coerceAtLeast(1)
        val theta = 0.42f * sev * progress
        if (theta < 1e-4f) frame
        else {
            val pelvis = v(frame[PELVIS])
            frame.mapIndexed { j, joint -> if (j in 9..23) hunchPoint(v(joint), pelvis, theta).toList() else joint }
        }
    }
}

// il-injury flags the spring as the least-proven piece (spec, not distilled from the optimizer).
fun forceLurch(
    rest: Frame,
    hitJoint: Int = 11,
    impulseMag: Float = 3f,
    k: Float = 80f,
    c: Float = 12f,
    dt: Float = 1f / 60f,
    frames: Int = 60,
): List<Frame> {
    if (rest.size < 24) return listOf(rest)
    val pos = rest.map { v(it) }.toMutableList()
    val vel = MutableList(rest.size) { floatArrayOf(0f, 0f, 0f) }
    vel[hitJoint] = floatArrayOf(0f, 0f, impulseMag)
    val out = mutableListOf<Frame>()
    repeat(frames) {
        for (j in pos.indices) {
            val accel = sub(scale(sub(v(rest[j]), pos[j]), k), scale(vel[j], c))
            vel[j] = add(vel[j], scale(accel, dt))
            pos[j] = add(pos[j], scale(vel[j], dt))
        }
        for (j in pos.indices) { val p = PARENTS[j]; if (p >= 0) vel[j] = add(vel[j], scale(vel[p], 0.15f)) }
        out.add(pos.map { listOf(it[0], it[1], it[2]) })
    }
    return out
}

private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

fun painBloom(hitJoint: Int, t: Float, hitTime: Float, jointCount: Int, severity: Float): List<Float> {
    val site = sigmoid((t - hitTime) * 6f) * (0.35f + 0.65f * exp(-(t - hitTime).coerceAtLeast(0f) / 2.3f))
    val hops = bfsHops(hitJoint, jointCount)
    val strength = (0.4f + 0.6f * severity.coerceIn(0f, 1f)) * site
    return (0 until jointCount).map { j ->
        val h = hops[j]
        if (h == Int.MAX_VALUE) 0f else (strength * exp(-0.55f * h)).coerceIn(0f, 1f)
    }
}

private fun bfsHops(from: Int, n: Int): IntArray {
    val adj = Array(n) { mutableListOf<Int>() }
    PARENTS.forEachIndexed { child, parent -> if (parent in 0 until n && child < n) { adj[child].add(parent); adj[parent].add(child) } }
    val hops = IntArray(n) { Int.MAX_VALUE }
    if (from !in 0 until n) return hops
    hops[from] = 0
    val q = ArrayDeque<Int>(); q.add(from)
    while (q.isNotEmpty()) { val u = q.removeFirst(); for (vtx in adj[u]) if (hops[vtx] == Int.MAX_VALUE) { hops[vtx] = hops[u] + 1; q.add(vtx) } }
    return hops
}
