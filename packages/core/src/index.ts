// --- Capture ---
export { initCapture } from "./capture/index.js";
export { resolveComponentPath } from "./capture/path.js";
export { createWebAdapter } from "./capture/web-adapter.js";

// --- Factors ---
export { queryFactors, queryComponentFactors } from "./factors/query.js";
export { queryFactorHistory, queryFactorDelta } from "./factors/snapshots.js";

// --- Experiments ---
export { evaluateFlag } from "./experiment/index.js";
export { evaluateTargeting } from "./experiment/targeting.js";
export { queryExperimentResults } from "./experiment/results.js";
export { evaluateExperimentThresholds, concludeExperiment } from "./experiment/governance.js";

// --- SDUI engine ---
export { validateSpec } from "./sdui/spec-validator.js";
export { resolveAllSources, type DataSourceCache, type ResolvedSources } from "./sdui/data-source.js";
export { dispatchAction } from "./sdui/action-dispatch.js";
export { loadSpec, type SpecStorage, type SignatureVerifier, type LoadedSpec } from "./sdui/spec-loader.js";
export { resolveBinding, resolveTextWithBindings, resolveProps, isBindingRef } from "./sdui/binding.js";
export { createSpecStorage, createDataSourceCache, devSignatureVerifier, type KVStorage } from "./sdui/default-storage.js";

export {
  RENDERER_VERSION,
  type Spec,
  type SpecNode,
  type SpecNodeType,
  type SpecValue,
  type ActionRef,
  type SignedSpec,
  type DataSourceConfig,
  type DataSourceRegistry,
  type ActionHandler,
  type ActionRegistry,
  type ComponentRenderer,
  type ComponentRegistry,
  type ListProps,
  type LayoutProps,
  type TextProps,
  type ButtonProps,
  type TextInputProps,
  type ImageProps,
  type IconProps,
  type CardProps,
  type TabsProps,
  type GridProps,
  type SelectProps,
  type ChipProps,
  type ModalProps,
  type ScrollViewProps,
  type ToggleProps,
  type SliderProps,
  type DividerProps,
  type SpacerProps,
} from "./sdui/spec-types.js";

// --- Types ---
export type { CaptureAdapter } from "./capture/adapter.js";
export type {
  Config,
  CaptureEvent,
  Session,
  CaptureHandle,
  EventType,
  FactorTier,
  Factor,
  ExperimentAssignment,
  Platform,
} from "./types.js";
export type { ComponentFactorAggregate } from "./factors/query.js";
export type { FactorSnapshot, FactorDelta } from "./factors/snapshots.js";
export type { TargetingRule, TargetingOperator } from "./experiment/targeting.js";
export type { VariantResult } from "./experiment/results.js";
export type { Threshold, GovernanceVerdict, GovernanceAction, FactorVerdict } from "./experiment/governance.js";
export { queryGovernanceLog, queryRecentGovernanceLog, type GovernanceLogRow } from "./experiment/governance-log.js";
export { queryExperimentSummaries, type ExperimentSummaryRow, type ExperimentSummaryFilters } from "./experiment/dashboard.js";
