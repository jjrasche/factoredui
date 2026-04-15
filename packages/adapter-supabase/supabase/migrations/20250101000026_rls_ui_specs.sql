-- Fix: ui_specs and ui_active have RLS enabled but no SELECT policies.
-- Authenticated SDK clients need to read specs to render SDUI.
-- Specs are public content (signed + verified client-side), not user-private data.

CREATE POLICY ui_specs_select_all ON factoredui.ui_specs
  FOR SELECT TO authenticated USING (true);

CREATE POLICY ui_active_select_all ON factoredui.ui_active
  FOR SELECT TO authenticated USING (true);
