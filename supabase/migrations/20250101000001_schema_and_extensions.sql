-- auxi schema: foundation
-- Creates the auxi schema, enables required extensions, and defines enum types.

CREATE SCHEMA IF NOT EXISTS auxi;

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

GRANT USAGE ON SCHEMA auxi TO authenticated, service_role, anon;

CREATE TYPE auxi.event_type AS ENUM (
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

CREATE TYPE auxi.factor_tier AS ENUM (
  'alarm',
  'diagnostic',
  'structural'
);

CREATE TYPE auxi.experiment_status AS ENUM (
  'draft',
  'running',
  'concluded'
);

CREATE TYPE auxi.threshold_operator AS ENUM (
  'gt',
  'lt',
  'gte',
  'lte',
  'eq'
);

CREATE TYPE auxi.threshold_action AS ENUM (
  'alert',
  'experiment'
);
