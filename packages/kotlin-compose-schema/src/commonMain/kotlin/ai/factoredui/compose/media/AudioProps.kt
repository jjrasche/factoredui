package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.actionRefOf

enum class MediaAction(val wire: String) {
    PLAY("play"),
    PAUSE("pause"),
    PREVIOUS_TRACK("previoustrack"),
    NEXT_TRACK("nexttrack"),
    SEEK_BACKWARD("seekbackward"),
    SEEK_FORWARD("seekforward"),
    SEEK_TO("seekto"),
    STOP("stop"),
    ;

    companion object {
        fun named(wire: String): MediaAction? = entries.firstOrNull { it.wire == wire }
    }
}

data class AudioMarker(val at: Double, val label: String)

data class AudioProps(
    val source: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: String?,
    val markers: List<AudioMarker>,
    val seekStepSeconds: Double,
    val reportEverySeconds: Double,
    val hasControls: Boolean,
    val showsTitle: Boolean,
    val mediaActions: Map<MediaAction, ActionRef>,
    val onPosition: ActionRef?,
    val notices: List<String>,
)

private const val DEFAULT_SEEK_STEP_SECONDS = 15.0
private const val DEFAULT_REPORT_EVERY_SECONDS = 5.0

private fun textOf(resolved: Map<String, Any?>, key: String): String? = (resolved[key] as? String)?.takeIf { it.isNotEmpty() }

private fun secondsOf(resolved: Map<String, Any?>, key: String, default: Double, notices: MutableList<String>): Double {
    if (key !in resolved || resolved[key] == null) return default
    val given = (resolved[key] as? Number)?.toDouble()
    if (given != null && given > 0.0) return given
    notices += "audio $key must be a number above zero, not '${resolved[key]}'; ${default.toInt()} seconds is used."
    return default
}

private fun markerOf(entry: Any?): AudioMarker? {
    val fields = entry as? Map<*, *> ?: return null
    val at = (fields["at"] as? Number)?.toDouble()?.takeIf { it >= 0.0 } ?: return null
    return AudioMarker(at, (fields["label"] as? String).orEmpty())
}

private fun markersOf(resolved: Map<String, Any?>, notices: MutableList<String>): List<AudioMarker> {
    val entries = resolved["markers"] as? List<*> ?: return emptyList()
    val read = entries.mapIndexedNotNull { index, entry ->
        markerOf(entry).also { if (it == null) notices += "audio markers[$index] needs an 'at' number of seconds from the start; it is left out." }
    }
    return read.sortedBy { it.at }
}

private fun mediaActionsOf(raw: Map<String, SpecValue>, notices: MutableList<String>): Map<MediaAction, ActionRef> {
    val given = (raw["media_actions"] as? SpecValue.ObjectValue)?.value ?: return emptyMap()
    return given.entries.mapNotNull { (name, value) ->
        val action = MediaAction.named(name)
        val ref = actionRefOf(value)
        when {
            action == null -> null.also { notices += "audio media_actions '$name' is not a lock-screen action; it reads ${MediaAction.entries.joinToString(", ") { it.wire }}." }
            ref == null -> null.also { notices += "audio media_actions '$name' must be an object with an 'action'; it is left out." }
            else -> action to ref
        }
    }.toMap()
}

fun readAudioProps(resolved: Map<String, Any?>, raw: Map<String, SpecValue>): AudioProps {
    val notices = mutableListOf<String>()
    val source = textOf(resolved, "source").orEmpty()
    if (source.isEmpty()) notices += "audio has no source; there is nothing to play."
    return AudioProps(
        source = source,
        title = textOf(resolved, "title"),
        artist = textOf(resolved, "artist"),
        album = textOf(resolved, "album"),
        artwork = textOf(resolved, "artwork"),
        markers = markersOf(resolved, notices),
        seekStepSeconds = secondsOf(resolved, "seek_step", DEFAULT_SEEK_STEP_SECONDS, notices),
        reportEverySeconds = secondsOf(resolved, "report_every", DEFAULT_REPORT_EVERY_SECONDS, notices),
        hasControls = resolved["controls"] as? Boolean ?: true,
        showsTitle = resolved["show_title"] as? Boolean ?: true,
        mediaActions = mediaActionsOf(raw, notices),
        onPosition = actionRefOf(raw["on_position"]),
        notices = notices,
    )
}
