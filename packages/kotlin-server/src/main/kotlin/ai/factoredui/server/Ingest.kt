package ai.factoredui.server

import ai.factoredui.compose.capture.CaptureEvent
import ai.factoredui.compose.capture.Session
import java.sql.Connection
import java.sql.Timestamp
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Backend ingest: persist a session + its event batch to Postgres.
 *
 * Idempotent on session id and event id (uses ON CONFLICT DO NOTHING)
 * so frontend retries on transient failure don't double-count. The
 * session row is created on first sight; subsequent batches in the
 * same session touch only the events table.
 *
 * The host owns the connection and the transaction. The function does
 * not commit, rollback, or set autocommit. Wrap a call in your own
 * transactional context (HikariCP `connection.use { … connection.commit() }`,
 * a framework-managed transaction, or whatever you already use).
 *
 * JDBC is blocking. If you call this from a coroutine, dispatch to
 * `Dispatchers.IO` first.
 */
fun ingestEvents(
    connection: Connection,
    session: Session,
    events: List<CaptureEvent>,
    json: Json = defaultJson,
) {
    if (events.isEmpty()) return
    insertSessionIfAbsent(connection, session, json)
    insertEvents(connection, session.id, events, json)
}

private val defaultJson = Json { ignoreUnknownKeys = true }

private fun insertSessionIfAbsent(connection: Connection, session: Session, json: Json) {
    val sql = """
        INSERT INTO factoredui_sessions (id, user_id, started_at, platform, metadata)
        VALUES (?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (id) DO NOTHING
    """.trimIndent()
    connection.prepareStatement(sql).use { stmt ->
        stmt.setString(1, session.id)
        stmt.setString(2, session.userId)
        stmt.setTimestamp(3, Timestamp.from(session.startedAt.toJavaInstant()))
        stmt.setString(4, session.platform.name.lowercase())
        stmt.setString(5, encodeJsonMap(session.metadata, json))
        stmt.executeUpdate()
    }
}

private fun insertEvents(
    connection: Connection,
    sessionId: String,
    events: List<CaptureEvent>,
    json: Json,
) {
    val sql = """
        INSERT INTO factoredui_events (id, session_id, event_type, component_path, payload, occurred_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (id) DO NOTHING
    """.trimIndent()
    connection.prepareStatement(sql).use { stmt ->
        for (event in events) {
            stmt.setString(1, event.id)
            stmt.setString(2, sessionId)
            stmt.setString(3, event.eventType.name.lowercase())
            stmt.setString(4, event.componentPath)
            stmt.setString(5, encodeJsonMap(event.payload, json))
            stmt.setTimestamp(6, Timestamp.from(event.occurredAt.toJavaInstant()))
            stmt.addBatch()
        }
        stmt.executeBatch()
    }
}

private fun encodeJsonMap(map: Map<String, JsonElement>, json: Json): String =
    json.encodeToString(
        MapSerializer(String.serializer(), JsonElement.serializer()),
        map,
    )

private fun kotlinx.datetime.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
