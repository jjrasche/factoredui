package ai.factoredui.compose.render

import ai.factoredui.compose.media.AudioDescription
import ai.factoredui.compose.media.AudioEngine
import ai.factoredui.compose.media.AudioListener
import ai.factoredui.compose.media.VoiceEngine
import ai.factoredui.compose.media.VoiceListener
import ai.factoredui.compose.media.VoiceRequest

internal class FakeAudioEngine : AudioEngine {
    val opened = mutableListOf<String>()
    val seeks = mutableListOf<Double>()
    val rates = mutableListOf<Double>()
    var plays = 0
    var pauses = 0
    var description: AudioDescription? = null
    var isClosed = false
    lateinit var listener: AudioListener

    override fun open(source: String, listener: AudioListener) {
        opened += source
        this.listener = listener
    }

    override fun play() {
        plays += 1
    }

    override fun pause() {
        pauses += 1
    }

    override fun seekTo(seconds: Double) {
        seeks += seconds
    }

    override fun setRate(rate: Double) {
        rates += rate
    }

    override fun describe(description: AudioDescription) {
        this.description = description
    }

    override fun close() {
        isClosed = true
    }
}

internal class FakeVoiceEngine : VoiceEngine {
    val requests = mutableListOf<VoiceRequest>()
    var stops = 0
    lateinit var listener: VoiceListener

    override fun start(request: VoiceRequest, listener: VoiceListener) {
        requests += request
        this.listener = listener
    }

    override fun stop() {
        stops += 1
    }

    override fun close() = Unit
}
