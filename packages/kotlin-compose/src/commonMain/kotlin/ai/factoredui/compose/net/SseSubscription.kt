package ai.factoredui.compose.net

/**
 * Live event-stream subscription — a host/consumer streams SSE frames in.
 *
 * Why expect/actual rather than raw ktor in commonMain: ktor's wasmJs
 * client is fetch-based, and the browser fetch API doesn't emit body
 * chunks until the response completes for SSE responses (the connection
 * stays open, so the body never "completes" — we'd block forever).
 * Browser-native `EventSource` handles SSE properly, dispatching each
 * event as it arrives.
 *
 * On non-wasm targets (android / desktop / ios) we use ktor's
 * bodyAsChannel + readUTF8Line, which streams correctly on those
 * engines. The intermediate `nonWasmJsMain` source set holds that
 * implementation.
 */
expect class SseSubscription {
    /** Stop receiving events and release any underlying resources. */
    fun close()
}

/**
 * Open an SSE subscription. Each `data: <line>\n\n` frame's payload is
 * passed verbatim to [onMessage] (without the `data: ` prefix). Errors
 * are surfaced via [onError]; the subscription is considered terminal
 * after that — caller can decide whether to retry by re-invoking.
 */
expect fun startSseSubscription(
    url: String,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit = { },
): SseSubscription
