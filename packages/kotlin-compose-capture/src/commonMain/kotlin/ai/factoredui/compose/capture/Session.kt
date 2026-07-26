package ai.factoredui.compose.capture

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A capture session. Frontend-generated id (UUID v4); the backend creates
 * the corresponding row idempotently when it first sees an event. No
 * synchronous round-trip required to start emitting events — capture
 * works offline and reconciles on flush.
 *
 * Wire-format type — lives in `kotlin-compose-schema` so server-side
 * ingest can persist sessions without depending on Compose Multiplatform.
 * The runtime [SessionManager] that mints these stays in the renderer.
 */
@Serializable
data class Session(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("started_at") val startedAt: Instant,
    val platform: CapturePlatform,
    val metadata: Map<String, JsonElement> = emptyMap(),
)
