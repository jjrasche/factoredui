-- Platform as a first-class experiment dimension.
-- Sessions record which platform generated them.
-- Experiments can target specific platforms (empty array = all platforms).

ALTER TABLE auxi.sessions
  ADD COLUMN platform text NOT NULL DEFAULT 'web';

CREATE INDEX idx_sessions_platform
  ON auxi.sessions (platform);

ALTER TABLE auxi.experiments
  ADD COLUMN platforms text[] NOT NULL DEFAULT '{}'::text[];

COMMENT ON COLUMN auxi.sessions.platform IS
  'Platform that generated this session: web, ios, android';

COMMENT ON COLUMN auxi.experiments.platforms IS
  'Platforms this experiment targets. Empty array = all platforms.';
