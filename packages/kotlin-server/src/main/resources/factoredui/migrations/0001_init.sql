-- factoredui: initial schema for event capture.
-- Idempotent — safe to run multiple times.
-- Postgres-specific: uses JSONB and ON CONFLICT DO NOTHING.

CREATE TABLE IF NOT EXISTS factoredui_sessions (
    id          TEXT      PRIMARY KEY,
    user_id     TEXT      NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ NULL,
    platform    TEXT      NOT NULL,
    metadata    JSONB     NOT NULL DEFAULT '{}'::JSONB
);

CREATE INDEX IF NOT EXISTS factoredui_sessions_user_started_idx
    ON factoredui_sessions (user_id, started_at DESC);

CREATE TABLE IF NOT EXISTS factoredui_events (
    id              TEXT      PRIMARY KEY,
    session_id      TEXT      NOT NULL REFERENCES factoredui_sessions(id) ON DELETE CASCADE,
    event_type      TEXT      NOT NULL,
    component_path  TEXT      NOT NULL,
    payload         JSONB     NOT NULL DEFAULT '{}'::JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Common access patterns the factor engine will need:
--   per-component aggregations,
--   per-session timelines,
--   per-user histories,
--   recent events for live dashboards.
CREATE INDEX IF NOT EXISTS factoredui_events_component_occurred_idx
    ON factoredui_events (component_path, occurred_at DESC);

CREATE INDEX IF NOT EXISTS factoredui_events_session_occurred_idx
    ON factoredui_events (session_id, occurred_at);

CREATE INDEX IF NOT EXISTS factoredui_events_event_type_idx
    ON factoredui_events (event_type);

CREATE INDEX IF NOT EXISTS factoredui_events_received_at_idx
    ON factoredui_events (received_at DESC);
