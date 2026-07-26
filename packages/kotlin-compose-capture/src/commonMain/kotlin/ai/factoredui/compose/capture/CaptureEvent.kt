package ai.factoredui.compose.capture

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A single observed user-interaction event.
 *
 * The component_path identifies the spec node that triggered the event —
 * this is the same identifier the factor engine aggregates by and the
 * experiment system mutates. One identity end-to-end is the project's
 * thesis (see CONCEPT.md).
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CaptureEvent(
    val id: String = Uuid.random().toString(),
    @SerialName("event_type") val eventType: EventType,
    @SerialName("component_path") val componentPath: String,
    val payload: Map<String, JsonElement> = emptyMap(),
    @SerialName("occurred_at") val occurredAt: Instant = Clock.System.now(),
)

/**
 * Closed enum of recorded event types. Mirrors the union in
 * packages/core/src/types.ts. The serialized name is lowercase
 * snake_case to match the TS payload format.
 */
@Serializable
enum class EventType {
    @SerialName("click") CLICK,
    @SerialName("scroll") SCROLL,
    @SerialName("error") ERROR,
    @SerialName("navigation") NAVIGATION,
    @SerialName("impression") IMPRESSION,
    @SerialName("input") INPUT,
    @SerialName("focus") FOCUS,
    @SerialName("blur") BLUR,
    @SerialName("submit") SUBMIT,
    @SerialName("resize") RESIZE,
    @SerialName("visibility") VISIBILITY,
    @SerialName("rage_click") RAGE_CLICK,
    @SerialName("dead_click") DEAD_CLICK,
    @SerialName("scroll_reversal") SCROLL_REVERSAL,
}

/**
 * The platform a session was opened on. Used by the factor engine to
 * slice metrics — e.g. "checkout completion on iOS vs web."
 */
@Serializable
enum class CapturePlatform {
    @SerialName("web") WEB,
    @SerialName("ios") IOS,
    @SerialName("android") ANDROID,
    @SerialName("desktop") DESKTOP,
}
