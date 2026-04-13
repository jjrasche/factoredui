// --- Provider and context-aware hooks ---
export {
  Provider,
  useFlag,
  useFactors,
  useGovernanceLog,
  useRecentGovernanceLog,
  useExperimentDashboard,
  usePlatform,
} from "./bindings/provider.js";

// --- Path context ---
export {
  Flow,
  Page,
  Component,
  Element,
  useComponentPath,
} from "./capture/path-context.js";

// --- SDUI renderer ---
export { renderSpec, type RenderContext } from "./sdui/renderer.js";
export { useSourceData, type SourceDataState } from "./sdui/use-source-data.js";

// --- Hook types ---
export type {
  UseFlagResult,
  UseFactorsResult,
  UseGovernanceLogResult,
  UseExperimentDashboardResult,
} from "./bindings/hooks.js";
