package ai.factoredui.compose.forcegraph

/**
 * wasmJs actual: uses the browser's native EventSource. Required because
 * ktor-client-js (the wasmJs ktor engine) is fetch-based, and browser
 * fetch buffers SSE responses until the body completes — which never
 * happens for a long-lived SSE connection. EventSource is the canonical
 * browser primitive for this pattern; it dispatches each `data:` frame
 * the moment it arrives.
 *
 * The @JsFun bodies are tiny adapters that wire the JS event listeners
 * back to the Kotlin callbacks. We pass JsAny handles back across the
 * boundary so the Kotlin side can later close the underlying ES.
 */

actual class SseSubscription internal constructor(private val handle: JsAny) {
    actual fun close() {
        jsCloseEventSource(handle)
    }
}

actual fun startSseSubscription(
    url: String,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
): SseSubscription {
    val handle = jsCreateEventSource(url, onMessage, onError)
    return SseSubscription(handle)
}

private fun jsCreateEventSource(
    url: String,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
): JsAny = jsCreateEventSourceImpl(url, { msg: JsString -> onMessage(msg.toString()) }, { err: JsString -> onError(err.toString()) })

@Suppress("UNUSED_PARAMETER")
private fun jsCreateEventSourceImpl(
    url: String,
    onMessage: (JsString) -> Unit,
    onError: (JsString) -> Unit,
): JsAny = js(
    """
    (() => {
      const es = new EventSource(url);
      es.onmessage = (e) => onMessage(String(e.data));
      es.onerror = (e) => onError(String((e && e.message) || 'sse error'));
      return es;
    })()
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun jsCloseEventSource(handle: JsAny): Unit = js("handle.close()")
