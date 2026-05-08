package ai.factoredui.compose.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Web (DOM) autocapture: page-level signals not produced by the renderer.
 *
 * What this catches:
 *  - Uncaught JS errors via `window.addEventListener("error", ...)`
 *  - Visibility transitions via `document.addEventListener("visibilitychange", ...)`
 *    Hidden state also triggers an immediate flush so events don't sit
 *    in the local queue when the user backgrounds the tab.
 *
 * What this DOES NOT catch:
 *  - Per-component clicks / inputs / etc. These come from
 *    [CaptureObservability] which bridges the renderer's existing
 *    Observability hook into the capture client. That works on every
 *    platform, not just web.
 *
 * Future port-wave items: rage-click and dead-click detection, scroll
 * reversal, throttled scroll/resize/input. The TS reference in
 * `packages/core/src/capture/web-adapter.ts` has the algorithms; they
 * just need a wasmJs port. Skipped for the observability MVP.
 *
 * Lifecycle: call [start] once on app boot, [stop] on teardown. Safe
 * to call stop() without start (no-op).
 */
class WebAutoCapture(
    private val capture: CaptureClient,
    private val scope: CoroutineScope,
) {
    private var handle: JsAny? = null

    fun start() {
        if (handle != null) return
        handle = jsAttachListeners(
            onError = { message, file, line ->
                capture.track(CaptureEvent(
                    eventType = EventType.ERROR,
                    componentPath = "window",
                    payload = mapOf(
                        "message" to JsonPrimitive(message),
                        "file" to JsonPrimitive(file),
                        "line" to JsonPrimitive(line),
                    ),
                ))
            },
            onVisibility = { state ->
                capture.track(CaptureEvent(
                    eventType = EventType.VISIBILITY,
                    componentPath = "document",
                    payload = mapOf("visibility_state" to JsonPrimitive(state)),
                ))
                if (state == "hidden") {
                    scope.launch { capture.flush() }
                }
            },
        )
    }

    fun stop() {
        handle?.let { jsDetachListeners(it) }
        handle = null
    }
}

@Suppress("UNUSED_PARAMETER")
private fun jsAttachListeners(
    onError: (message: String, file: String, line: Int) -> Unit,
    onVisibility: (state: String) -> Unit,
): JsAny = jsAttachListenersImpl(
    onError = { message: JsString, file: JsString, line: JsNumber ->
        onError(message.toString(), file.toString(), line.toString().toIntOrNull() ?: 0)
    },
    onVisibility = { state: JsString -> onVisibility(state.toString()) },
)

@Suppress("UNUSED_PARAMETER")
private fun jsAttachListenersImpl(
    onError: (JsString, JsString, JsNumber) -> Unit,
    onVisibility: (JsString) -> Unit,
): JsAny = js(
    """
    (() => {
      const errorListener = (e) => {
        onError(String(e.message || 'unknown'), String(e.filename || ''), e.lineno || 0);
      };
      const visibilityListener = () => {
        onVisibility(String(document.visibilityState));
      };
      window.addEventListener('error', errorListener);
      document.addEventListener('visibilitychange', visibilityListener);
      return { errorListener, visibilityListener };
    })()
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun jsDetachListeners(handle: JsAny): Unit = js(
    """
    (() => {
      window.removeEventListener('error', handle.errorListener);
      document.removeEventListener('visibilitychange', handle.visibilityListener);
    })()
    """,
)
