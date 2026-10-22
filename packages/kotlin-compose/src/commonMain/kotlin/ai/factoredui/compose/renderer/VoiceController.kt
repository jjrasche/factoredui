package ai.factoredui.compose.renderer

import ai.factoredui.compose.media.VoiceCapture
import ai.factoredui.compose.media.VoiceEngine
import ai.factoredui.compose.media.VoiceListener
import ai.factoredui.compose.media.VoiceProps
import ai.factoredui.compose.media.VoiceRequest
import ai.factoredui.compose.media.captureEvent
import ai.factoredui.compose.schema.ActionRef
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class VoicePaths(val listening: String?, val transcript: String?)

internal data class VoiceSnapshot(val status: String, val isListening: Boolean, val transcript: String?)

internal class VoiceController(
    private val engine: VoiceEngine?,
    private val context: RenderContext,
    private val nodeId: String,
    private val scope: CoroutineScope,
    private val paths: VoicePaths,
    private val publish: (VoiceRecord, List<String>) -> Unit,
) : VoiceListener {
    var snapshot by mutableStateOf(VoiceSnapshot(if (engine == null) "unsupported" else "ready", false, null))
        private set

    private var props: VoiceProps? = null
    private var heard: Pair<String, Double?>? = null
    private var uploadedBytes: Long? = null
    private var failures: List<String> = emptyList()
    private var isActive = false

    val canListen: Boolean get() = engine != null

    fun sync(props: VoiceProps) {
        this.props = props
        announce(if (engine == null) listOf("voice is not available on this target.") else emptyList())
    }

    fun begin() {
        val current = props ?: return
        if (engine == null || isActive) return
        isActive = true
        failures = emptyList()
        heard = null
        uploadedBytes = null
        engine.start(VoiceRequest(current.capture, current.language, current.uploadUrl), this)
    }

    fun end() {
        if (isActive) engine?.stop()
    }

    fun toggle() = if (isActive) end() else begin()

    fun close() {
        engine?.close()
    }

    override fun onListening() {
        snapshot = snapshot.copy(status = "listening", isListening = true)
        write(paths.listening, true)
        announce()
    }

    override fun onHeard(text: String, confidence: Double?) {
        heard = text to confidence
        snapshot = snapshot.copy(transcript = text)
        write(paths.transcript, text)
        announce()
    }

    override fun onUploaded(bytes: Long) {
        uploadedBytes = bytes
    }

    override fun onEnded(milliseconds: Long) {
        isActive = false
        snapshot = snapshot.copy(status = "ready", isListening = false)
        write(paths.listening, false)
        deliver(milliseconds)
        announce()
    }

    override fun onFailed(message: String) {
        isActive = false
        failures = listOf("voice failed: $message")
        snapshot = snapshot.copy(status = "failed", isListening = false)
        write(paths.listening, false)
        announce()
    }

    private fun deliver(milliseconds: Long) {
        val current = props ?: return
        val action = current.onCapture ?: return
        val event = when (current.capture) {
            VoiceCapture.TEXT -> heard?.let { (text, confidence) -> captureEvent(text, confidence, milliseconds, null) }
            VoiceCapture.AUDIO -> uploadedBytes?.let { captureEvent(null, null, milliseconds, it) }
        } ?: return
        scope.launch { context.dispatch(nodeId, ActionRef(action.action, action.params + event)) }
    }

    private fun write(path: String?, value: Any?) {
        if (path != null) context.setBinding(path, value)
    }

    private fun announce(extra: List<String> = emptyList()) {
        val shown = props
        publish(
            VoiceRecord(shown?.label.orEmpty(), shown?.mode?.wire.orEmpty(), shown?.capture?.wire.orEmpty(), snapshot.status, snapshot.isListening, snapshot.transcript),
            shown?.notices.orEmpty() + failures + extra,
        )
    }
}
