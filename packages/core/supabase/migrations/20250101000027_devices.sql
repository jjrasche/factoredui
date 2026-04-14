-- factoredui schema: devices table + session FK
-- Replaces opaque jsonb metadata for device info with typed columns.
-- Enables device-targeted experiments ("Android 14+ Samsung devices").

CREATE TABLE factoredui.devices (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  fingerprint   text NOT NULL,
  manufacturer  text,
  model         text,
  os_name       text NOT NULL,
  os_version    text NOT NULL,
  app_version   text,
  app_build     text,
  screen_width  int,
  screen_height int,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT devices_unique_fingerprint UNIQUE (user_id, fingerprint)
);

CREATE INDEX idx_devices_user ON factoredui.devices (user_id);

ALTER TABLE factoredui.devices ENABLE ROW LEVEL SECURITY;

-- RLS: user reads/writes own devices
CREATE POLICY devices_select_own ON factoredui.devices
  FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY devices_insert_own ON factoredui.devices
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY devices_update_own ON factoredui.devices
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

-- Add device_id FK to sessions (nullable for backward compat)
ALTER TABLE factoredui.sessions
  ADD COLUMN device_id uuid REFERENCES factoredui.devices(id);

CREATE INDEX idx_sessions_device ON factoredui.sessions (device_id);

-- Grants
GRANT SELECT, INSERT, UPDATE ON factoredui.devices TO authenticated;
GRANT ALL ON factoredui.devices TO service_role;
