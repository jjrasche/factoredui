-- factoredui schema: foundation
-- Creates the factoredui schema, enables required extensions, and defines enum types.

CREATE SCHEMA IF NOT EXISTS factoredui;

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

GRANT USAGE ON SCHEMA factoredui TO authenticated, service_role, anon;

CREATE TYPE factoredui.event_type AS ENUM (
  'click',
  'scroll',
  'error',
  'navigation',
  'impression',
  'input',
  'focus',
  'blur',
  'submit',
  'resize',
  'visibility',
  'rage_click',
  'dead_click',
  'scroll_reversal'
);

CREATE TYPE factoredui.factor_tier AS ENUM (
  'alarm',
  'diagnostic',
  'structural'
);

CREATE TYPE factoredui.experiment_status AS ENUM (
  'draft',
  'running',
  'concluded'
);

CREATE TYPE factoredui.threshold_operator AS ENUM (
  'gt',
  'lt',
  'gte',
  'lte',
  'eq'
);

CREATE TYPE factoredui.threshold_action AS ENUM (
  'alert',
  'experiment'
);
