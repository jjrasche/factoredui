package ai.factoredui.compose.capture

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A capture session. Frontend-generated id (UUID v4); the backend creates
 * the corresponding row idempotently when it first sees an event. No
 * synchronous round-trip required to start emitting events — capture
 * works offline and reconciles on flush.
 */
@Serializable
data class Session(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("started_at") val startedAt: Instant,
    val platform: CapturePlatform,
    val metadata: Map<String, JsonElement> = emptyMap(),
)

/**
 * Tracks the active session. Rotates after [timeout] of inactivity.
 *
 * The session id is generated locally (UUID); endSession is purely a
 * client-side state reset. The backend is the source of truth for
 * session boundaries — duplicate session-creation events are
 * idempotent (INSERT ON CONFLICT DO NOTHING).
 */
@OptIn(ExperimentalUuidApi::class)
class SessionManager(
    private val userId: () -> String,
    private val platform: CapturePlatform,
    private val metadata: () -> Map<String, JsonElement> = { emptyMap() },
    private val timeout: Duration = 30.minutes,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private var current: Session? = null
    private var lastActivity: Instant = Instant.DISTANT_PAST

    /** Return the live session, creating a fresh one if expired or absent. */
    fun ensureSession(): Session {
        val tick = now()
        val expired = current == null || (tick - lastActivity) > timeout
        if (expired) {
            current = Session(
                id = newId(),
                userId = userId(),
                startedAt = tick,
                platform = platform,
                metadata = metadata(),
            )
        }
        lastActivity = tick
        return current!!
    }

    /** Drop the active session locally. Backend rotates on next event. */
    fun endSession() {
        current = null
    }

    /** Current session id without ticking activity, or null if no session yet. */
    val sessionId: String? get() = current?.id
}
