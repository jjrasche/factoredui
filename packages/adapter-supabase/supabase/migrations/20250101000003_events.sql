-- factoredui schema: raw interaction events (highest volume table)
-- Immutable append-only. SDK writes, factor engine reads.

CREATE TABLE factoredui.events (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  session_id      uuid NOT NULL REFERENCES factoredui.sessions(id) ON DELETE CASCADE,
  event_type      factoredui.event_type NOT NULL,
  component_path  text NOT NULL,
  payload         jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at      timestamptz NOT NULL DEFAULT now()
);

-- BRIN: events are append-only, naturally time-ordered (~100x smaller than B-tree)
CREATE INDEX idx_events_created_at_brin
  ON factoredui.events USING brin (created_at);

-- SDK queries: events for a session
CREATE INDEX idx_events_session_created
  ON factoredui.events (session_id, created_at);

-- Factor computation: events for a user on a component in a time range
CREATE INDEX idx_events_user_component_time
  ON factoredui.events (user_id, component_path, created_at DESC);

-- Global queries: all events of a type in a time range
CREATE INDEX idx_events_type_created
  ON factoredui.events (event_type, created_at DESC);

ALTER TABLE factoredui.events ENABLE ROW LEVEL SECURITY;
