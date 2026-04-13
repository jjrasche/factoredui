# factoredui

Factored UI. Three npm packages that capture user interactions, compute standardized behavioral factors, enable LLM-driven experimentation, and support democratic governance of app changes.

## Stack
- Language: TypeScript (npm workspaces monorepo)
- Packages: `@factoredui/core` (pure TS), `@factoredui/react` (React bindings), `@factoredui/react-native` (RN primitives)
- Database: Supabase (Postgres with RLS, separate `factoredui` schema)
- Factor engine: SQL materialized views
- Flags/experiments: Client-side evaluation from Supabase-stored config

## Commands
```bash
npm run build        # tsup: core → react → react-native (sequential, DTS depends on prior)
npm test             # vitest run (unit + integration)
npm run test:unit    # unit tests only
npm run typecheck    # tsc --noEmit (root tsconfig with paths)
npx supabase start   # local Supabase (required for integration tests)
```

## Architecture

### Packages
- `packages/core/` -- capture pipeline, factors, experiments, SDUI engine, types, CLI, migrations
- `packages/react/` -- Provider, hooks, path context, SDUI renderer, useSourceData
- `packages/react-native/` -- 20 themed RN component primitives (createComponentRegistry)

### Build Notes
- Build order matters: core must build first (react/react-native DTS resolve `@factoredui/core` via node_modules symlinks → `dist/index.d.ts`)
- Root `tsconfig.json` has `paths` for all three packages → use for typecheck
- Package `tsconfig.json` files have NO `paths` → used by tsup DTS generation, must resolve through node_modules

### Data Flow
Capture adapters emit `CaptureEvent` → batched writer flushes to `factoredui.events` → SQL materialized views compute factors → experiment system reads factors for targeting and governance.

### Cross-Platform Contract
`CaptureAdapter` interface defines what each platform must implement. `Flow/Page/Component/Element` context providers define the shared path hierarchy.

## Global References

Read these from `~/.claude/references/` when relevant:
- `coding-standards.md` -- read before writing any code
- `supabase-local-dev.md` -- migration workflow, env switching

## Project-Specific Notes
- CONCEPT.md -- full concept brief, design decisions, three audiences
- RESEARCH.md -- landscape analysis, prior art, novelty assessment
