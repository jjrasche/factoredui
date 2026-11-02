package ai.factoredui.compose.media

interface AudioListener {
    fun onReady(durationSeconds: Double)

    fun onTime(positionSeconds: Double)

    fun onPlaying(isPlaying: Boolean)

    fun onEnded()

    fun onFailed(message: String)

    fun onBlocked()

    fun onMediaAction(action: MediaAction, seekTimeSeconds: Double?)
}

data class AudioDescription(val title: String?, val artist: String?, val album: String?, val artwork: String?, val actions: Set<MediaAction>)

interface AudioEngine {
    fun open(source: String, listener: AudioListener)

    fun play()

    fun pause()

    fun seekTo(seconds: Double)

    fun setRate(rate: Double)

    fun describe(description: AudioDescription)

    fun close()
}

interface VoiceListener {
    fun onListening()

    fun onHeard(text: String, confidence: Double?)

    fun onUploaded(bytes: Long)

    fun onEnded(milliseconds: Long)

    fun onFailed(message: String)
}

data class VoiceRequest(val capture: VoiceCapture, val language: String?, val uploadUrl: String?)

interface VoiceEngine {
    fun start(request: VoiceRequest, listener: VoiceListener)

    fun stop()

    fun close()
}

class MediaHost(val audio: (() -> AudioEngine)?, val voice: (() -> VoiceEngine)?) {
    companion object {
        val NONE = MediaHost(null, null)
    }
}

expect fun platformMediaHost(): MediaHost
