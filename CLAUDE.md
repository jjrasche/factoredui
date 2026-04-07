# @practice/observe

## Overview
Autonomous app optimization library. Captures user interactions, computes standardized behavioral factors, enables LLM-driven experimentation, supports democratic governance of app changes. TypeScript library + Supabase migrations.

## Stack
- Language: TypeScript (framework-agnostic capture, React bindings)
- Database: Supabase (Postgres with RLS, separate `observe` schema)
- Factor engine: SQL materialized views
- Flags/experiments: Client-side evaluation from Supabase-stored config
- Cross-platform: Dart (Flutter), Swift/Kotlin (native) write same event shape

## Architecture
- `capture/` -- DOM-level interaction, error, and structural data capture
- `factors/` -- three-tier computation: alarm, diagnostic, structural
- `experiment/` -- feature flags, bucketing, exposure tracking
- `bindings/` -- React provider/hooks (future: Flutter, native)
- `supabase/migrations/` -- Supabase schema, factor views, experiment tables

## Key concepts
- CONCEPT.md -- full concept brief, design decisions, three audiences
- RESEARCH.md -- landscape analysis, prior art, novelty assessment

## Commands
- `npm run build` -- tsup (ESM + CJS + DTS)
- `npm test` -- vitest run (76 tests: 42 unit, 34 integration against local Supabase)
- `npm run typecheck` -- tsc --noEmit
- `npx supabase start` -- local Supabase (required for integration tests)

## References
- `~/.claude/references/coding-standards.md` -- read before writing any code
- `~/.claude/references/agent-web-dev.md` -- complementary stack (build/test/ship)
