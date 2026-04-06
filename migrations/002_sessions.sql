-- observe schema: session tracking

CREATE TABLE observe.sessions (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  started_at  timestamptz NOT NULL DEFAULT now(),
  ended_at    timestamptz,
  metadata    jsonb NOT NULL DEFAULT '{}'::jsonb,

  CONSTRAINT sessions_ended_after_started
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_sessions_user_started
  ON observe.sessions (user_id, started_at DESC);

CREATE INDEX idx_sessions_started_at
  ON observe.sessions (started_at);

ALTER TABLE observe.sessions ENABLE ROW LEVEL SECURITY;
