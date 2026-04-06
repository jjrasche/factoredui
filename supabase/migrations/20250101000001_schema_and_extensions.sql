-- observe schema: foundation
-- Creates the observe schema, enables required extensions, and defines enum types.

CREATE SCHEMA IF NOT EXISTS observe;

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

GRANT USAGE ON SCHEMA observe TO authenticated, service_role, anon;

CREATE TYPE observe.event_type AS ENUM (
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

CREATE TYPE observe.factor_tier AS ENUM (
  'alarm',
  'diagnostic',
  'structural'
);

CREATE TYPE observe.experiment_status AS ENUM (
  'draft',
  'running',
  'concluded'
);

CREATE TYPE observe.threshold_operator AS ENUM (
  'gt',
  'lt',
  'gte',
  'lte',
  'eq'
);

CREATE TYPE observe.threshold_action AS ENUM (
  'alert',
  'experiment'
);
