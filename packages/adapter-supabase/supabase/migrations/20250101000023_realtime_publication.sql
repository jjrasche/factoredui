-- Enable Supabase Realtime for tables that hooks subscribe to.
-- Without this, postgres_changes events are never delivered.

alter publication supabase_realtime add table factoredui.governance_log;
alter publication supabase_realtime add table factoredui.experiments;

-- REPLICA IDENTITY FULL required for UPDATE/DELETE events on experiments.
-- The dashboard hook subscribes to event: "*" (INSERT + UPDATE + DELETE).
alter table factoredui.experiments replica identity full;
