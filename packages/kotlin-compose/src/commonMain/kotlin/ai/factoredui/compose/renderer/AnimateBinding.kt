package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.BindingResolver
import kotlinx.coroutines.delay

const val ANIMATE_BINDING_ACTION = "animate_binding"

private const val DEFAULT_INTERVAL_MS = 1000L
private const val FASTEST_INTERVAL_MS = 50L

internal class BindingAnimation(
    val path: String,
    val from: Double,
    val to: Double,
    val step: Double,
    val intervalMs: Long,
    val isLooping: Boolean,
    val playingPath: String?,
)

internal sealed interface AnimationReading {
    class Ready(val animation: BindingAnimation) : AnimationReading
    class Refused(val notice: String) : AnimationReading
}

private fun numberParam(params: Map<String, Any?>, key: String): Double? = (params[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }

internal fun readBindingAnimation(nodeId: String, params: Map<String, Any?>): AnimationReading {
    val refused = "animate_binding on '$nodeId' was refused:"
    val path = params["path"] as? String
    if (path.isNullOrEmpty()) return AnimationReading.Refused("$refused 'path' must be non-empty text, not '${params["path"]}'")
    val from = numberParam(params, "from") ?: return AnimationReading.Refused("$refused 'from' must be a number, not '${params["from"]}'")
    val to = numberParam(params, "to") ?: return AnimationReading.Refused("$refused 'to' must be a number, not '${params["to"]}'")
    if (to <= from) return AnimationReading.Refused("$refused 'to' ($to) must be above 'from' ($from)")
    val step = if ("step" in params) numberParam(params, "step") else 1.0
    if (step == null || step <= 0.0) return AnimationReading.Refused("$refused 'step' must be a number above 0, not '${params["step"]}'")
    val interval = if ("interval_ms" in params) numberParam(params, "interval_ms") else DEFAULT_INTERVAL_MS.toDouble()
    if (interval == null || interval < FASTEST_INTERVAL_MS) return AnimationReading.Refused("$refused 'interval_ms' must be a number of $FASTEST_INTERVAL_MS or more, not '${params["interval_ms"]}'")
    val playingPath = params["playing"] as? String
    return AnimationReading.Ready(BindingAnimation(path, from, to, step, interval.toLong(), params["loop"] == true, playingPath))
}

/** The value after [current], or null when a run that does not loop has reached its end; a value before `from` goes to `from`, and a loop that passes `to` goes back to `from`. */
internal fun nextAnimationValue(animation: BindingAnimation, current: Double): Double? {
    if (current < animation.from) return animation.from
    val next = current + animation.step
    return when {
        next <= animation.to -> next
        animation.isLooping -> animation.from
        else -> null
    }
}

/**
 * Plays a number through `from`..`to` at `interval_ms` per `step`: a first dispatch starts it and a second, while it plays, stops it.
 * `loop: true` wraps from `to` back to `from`; without it the run stops at `to`, and a new start from `to` begins again at `from`.
 * `playing` names a path that holds true while it plays.
 */
internal suspend fun RenderContext.animateBinding(nodeId: String, params: Map<String, Any?>) {
    val animation = when (val reading = readBindingAnimation(nodeId, params)) {
        is AnimationReading.Refused -> return setBinding(RENDERER_ERROR_PATH, reading.notice)
        is AnimationReading.Ready -> reading.animation
    }
    if (!screen.animating.add(animation.path)) {
        screen.animating.remove(animation.path)
        animation.playingPath?.let { setBinding(it, false) }
        return
    }
    animation.playingPath?.let { setBinding(it, true) }
    try {
        val start = BindingResolver.resolveBinding("{${animation.path}}", data) as? Number
        if (start == null || start.toDouble() >= animation.to && !animation.isLooping) setBinding(animation.path, animation.from)
        while (animation.path in screen.animating) {
            delay(animation.intervalMs)
            if (animation.path !in screen.animating) break
            val current = (BindingResolver.resolveBinding("{${animation.path}}", data) as? Number)?.toDouble() ?: animation.from
            setBinding(animation.path, nextAnimationValue(animation, current) ?: break)
        }
    } finally {
        screen.animating.remove(animation.path)
        animation.playingPath?.let { setBinding(it, false) }
    }
}
