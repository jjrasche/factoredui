package ai.factoredui.compose.media

private const val NO_CONFIDENCE = -1.0

@Suppress("UNUSED_PARAMETER")
private fun jsStartVoice(
    capture: String,
    language: String,
    uploadUrl: String,
    onListening: () -> Unit,
    onHeard: (JsString, Double) -> Unit,
    onUploaded: (Double) -> Unit,
    onEnded: (Double) -> Unit,
    onFailed: (JsString) -> Unit,
): JsAny = js(
    """
    (() => {
      const handle = { stopped: false, stop: () => { handle.stopped = true; if (handle.inner) handle.inner(); } };
      let failed = false;
      const fail = (message) => { if (failed) return; failed = true; onFailed(String(message)); };
      if (window.isSecureContext === false) { fail('the microphone needs HTTPS'); return handle; }
      if (capture === 'audio') {
        if (!navigator.mediaDevices || !window.MediaRecorder) { fail('audio capture is not available in this browser'); return handle; }
        navigator.mediaDevices.getUserMedia({ audio: true }).then((stream) => {
          const chunks = [];
          const recorder = new MediaRecorder(stream);
          let began = performance.now();
          recorder.onstart = () => { began = performance.now(); onListening(); };
          recorder.ondataavailable = (event) => { if (event.data && event.data.size > 0) chunks.push(event.data); };
          recorder.onerror = (event) => fail((event.error && event.error.message) || 'recording failed');
          recorder.onstop = async () => {
            stream.getTracks().forEach((track) => track.stop());
            if (failed) return;
            const blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' });
            try {
              const response = await fetch(uploadUrl, { method: 'POST', headers: { 'Content-Type': blob.type }, body: blob });
              if (!response.ok) throw new Error('the upload was refused with status ' + response.status);
              onUploaded(blob.size);
              onEnded(Math.round(performance.now() - began));
            } catch (e) { fail((e && e.message) || e); }
          };
          handle.inner = () => { if (recorder.state !== 'inactive') recorder.stop(); };
          recorder.start();
          if (handle.stopped) handle.inner();
        }).catch((e) => fail(e && e.name === 'NotAllowedError' ? 'the microphone was not allowed' : ((e && e.message) || e)));
        return handle;
      }
      const Recognition = window.factoredUiSpeechRecognition || window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!Recognition) { fail('speech recognition is not available in this browser'); return handle; }
      const recognition = new Recognition();
      recognition.lang = language || '';
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.continuous = false;
      let began = performance.now();
      recognition.onstart = () => { began = performance.now(); onListening(); };
      recognition.onresult = (event) => {
        const result = event.results[event.results.length - 1];
        if (result && result[0]) onHeard(String(result[0].transcript).trim(), typeof result[0].confidence === 'number' ? result[0].confidence : -1);
      };
      recognition.onerror = (event) => fail(event.error === 'not-allowed' ? 'the microphone was not allowed' : ((event && event.error) || 'recognition failed'));
      recognition.onend = () => { if (!failed) onEnded(Math.round(performance.now() - began)); };
      handle.inner = () => { try { recognition.stop(); } catch (e) {} };
      try { recognition.start(); } catch (e) { fail((e && e.message) || e); }
      if (handle.stopped) handle.inner();
      return handle;
    })()
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun jsStopVoice(handle: JsAny): Unit = js("handle.stop()")

internal class WebVoiceEngine : VoiceEngine {
    private var handle: JsAny? = null

    override fun start(request: VoiceRequest, listener: VoiceListener) {
        handle = jsStartVoice(
            request.capture.wire, request.language.orEmpty(), request.uploadUrl.orEmpty(),
            { listener.onListening() },
            { text, confidence -> listener.onHeard(text.toString(), confidence.takeIf { it != NO_CONFIDENCE }) },
            { bytes -> listener.onUploaded(bytes.toLong()) },
            { milliseconds -> listener.onEnded(milliseconds.toLong()) },
            { message -> listener.onFailed(message.toString()) },
        )
    }

    override fun stop() {
        handle?.let(::jsStopVoice)
    }

    override fun close() {
        stop()
        handle = null
    }
}
