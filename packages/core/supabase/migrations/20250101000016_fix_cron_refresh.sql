-- Fix cron refresh job to use refresh_factor_views() instead of inline view list.
-- Migration 011 hardcoded 8 views; migration 015 added 3 structural views
-- but only updated the function, not the cron job.

SELECT cron.unschedule('auxi-refresh-factors');

SELECT cron.schedule(
  'auxi-refresh-factors',
  '0 * * * *',
  $$SELECT auxi.refresh_factor_views();$$
);
