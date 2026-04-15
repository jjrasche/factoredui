-- Platform as a first-class experiment dimension.
-- Sessions record which platform generated them.
-- Experiments can target specific platforms (empty array = all platforms).

ALTER TABLE factoredui.sessions
  ADD COLUMN platform text NOT NULL DEFAULT 'web';

CREATE INDEX idx_sessions_platform
  ON factoredui.sessions (platform);

ALTER TABLE factoredui.experiments
  ADD COLUMN platforms text[] NOT NULL DEFAULT '{}'::text[];

COMMENT ON COLUMN factoredui.sessions.platform IS
  'Platform that generated this session: web, ios, android';

COMMENT ON COLUMN factoredui.experiments.platforms IS
  'Platforms this experiment targets. Empty array = all platforms.';
