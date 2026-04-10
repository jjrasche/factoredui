# auxi

Autonomous User eXperience Improver. TypeScript library + Supabase migrations that captures user interactions, computes standardized behavioral factors, enables LLM-driven experimentation, and supports democratic governance of app changes.

## Stack
- Language: TypeScript (framework-agnostic core, React + React Native bindings)
- Database: Supabase (Postgres with RLS, separate `auxi` schema)
- Factor engine: SQL materialized views
- Flags/experiments: Client-side evaluation from Supabase-stored config

## Commands
```bash
npm run build        # tsup (ESM + CJS + DTS)
npm test             # vitest run (unit + integration)
npm run test:unit    # unit tests only
npm run typecheck    # tsc --noEmit
npx supabase start   # local Supabase (required for integration tests)
```

## Architecture

### Key Directories
- `capture/` -- platform-agnostic event pipeline + web adapter
- `factors/` -- three-tier computation: alarm, diagnostic, structural
- `experiment/` -- feature flags, bucketing, exposure tracking, governance
- `bindings/` -- React provider/hooks, React Native provider/hooks
- `supabase/migrations/` -- schema, factor views, experiment tables

### Data Flow
Capture adapters emit `AuxiEvent` -> batched writer flushes to `auxi.events` -> SQL materialized views compute factors -> experiment system reads factors for targeting and governance.

### Cross-Platform Contract
`CaptureAdapter` interface defines what each platform must implement. `AuxiFlow/AuxiPage/AuxiComponent/AuxiElement` context providers define the shared path hierarchy. Platform-specific adapters live with their platform (e.g. Expo adapter in house-ops), not here.

## Global References

Read these from `~/.claude/references/` when relevant:
- `coding-standards.md` -- read before writing any code
- `supabase-local-dev.md` -- migration workflow, env switching

## Project-Specific Notes
- CONCEPT.md -- full concept brief, design decisions, three audiences
- RESEARCH.md -- landscape analysis, prior art, novelty assessment
