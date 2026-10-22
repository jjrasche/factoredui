package ai.factoredui.compose.media

import ai.factoredui.compose.schema.ActionRef
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.schema.actionRefOf

enum class VoiceMode(val wire: String) { HOLD("hold"), TOGGLE("toggle") }

enum class VoiceCapture(val wire: String) { TEXT("text"), AUDIO("audio") }

data class VoiceProps(
    val label: String,
    val mode: VoiceMode,
    val capture: VoiceCapture,
    val uploadUrl: String?,
    val language: String?,
    val onCapture: ActionRef?,
    val notices: List<String>,
)

private fun <T> choiceOf(resolved: Map<String, Any?>, key: String, choices: List<T>, wireOf: (T) -> String, notices: MutableList<String>): T {
    val given = resolved[key] ?: return choices.first()
    val chosen = choices.firstOrNull { wireOf(it) == given }
    if (chosen != null) return chosen
    notices += "voice $key must be ${choices.joinToString(" or ") { wireOf(it) }}, not '$given'; ${wireOf(choices.first())} is used."
    return choices.first()
}

fun readVoiceProps(resolved: Map<String, Any?>, raw: Map<String, SpecValue>): VoiceProps {
    val notices = mutableListOf<String>()
    val mode = choiceOf(resolved, "mode", VoiceMode.entries, { it.wire }, notices)
    val capture = choiceOf(resolved, "capture", VoiceCapture.entries, { it.wire }, notices)
    val uploadUrl = (resolved["upload_url"] as? String)?.takeIf { it.isNotEmpty() }
    if (capture == VoiceCapture.AUDIO && uploadUrl == null) notices += "voice capture 'audio' needs an upload_url; nothing can be sent."
    val onCapture = actionRefOf(raw["on_capture"])
    if (onCapture == null) notices += "voice has no on_capture; nothing is sent when it hears something."
    val label = (resolved["label"] as? String)?.takeIf { it.isNotEmpty() } ?: if (mode == VoiceMode.HOLD) "Hold to talk" else "Tap to talk"
    return VoiceProps(label, mode, capture, uploadUrl, (resolved["lang"] as? String)?.takeIf { it.isNotEmpty() }, onCapture, notices)
}

fun captureEvent(text: String?, confidence: Double?, milliseconds: Long, uploadedBytes: Long?): Map<String, SpecValue> = mapOf(
    "text" to (text?.let { SpecValue.StringValue(it) } ?: SpecValue.NullValue),
    "confidence" to (confidence?.let { SpecValue.NumberValue(it) } ?: SpecValue.NullValue),
    "ms" to SpecValue.NumberValue(milliseconds.toDouble()),
    "uploaded" to SpecValue.BooleanValue(uploadedBytes != null),
    "bytes" to (uploadedBytes?.let { SpecValue.NumberValue(it.toDouble()) } ?: SpecValue.NullValue),
)
