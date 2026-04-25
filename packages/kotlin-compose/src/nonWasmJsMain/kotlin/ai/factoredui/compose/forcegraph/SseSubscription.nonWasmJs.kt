package ai.factoredui.compose.forcegraph

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Non-wasm actual: ktor's bodyAsChannel + readUTF8Line streams correctly
 * on okhttp / darwin engines. Each `data: <line>\n\n` SSE frame is split
 * by `\n`; we forward the post-prefix payload to onMessage.
 */
actual class SseSubscription internal constructor(
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val job: Job,
) {
    actual fun close() {
        job.cancel()
        scope.cancel()
        client.close()
    }
}

actual fun startSseSubscription(
    url: String,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
): SseSubscription {
    val client = HttpClient()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val job = scope.launch {
        runCatching {
            client.get(url).bodyAsChannel().let { channel ->
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isNotEmpty()) onMessage(payload)
                }
            }
        }.onFailure { onError(it.message ?: it::class.simpleName ?: "sse error") }
    }
    return SseSubscription(client, scope, job)
}
