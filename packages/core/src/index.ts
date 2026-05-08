// --- Store interface ---
export type {
  FactoredStore,
  EventRow,
  RunningExperiment,
  VariantWithTraffic,
  ExperimentMeta,
  ExperimentInsertRow,
  BulkDeltaRow,
  Unsubscribe,
} from "./store.js";

// --- Capture ---
export { initCapture } from "./capture/index.js";
export { createEventWriter } from "./capture/writer.js";
export type { EventWriter } from "./capture/writer.js";
export { createSessionManager } from "./capture/session.js";
export type { SessionManager } from "./capture/session.js";
export { resolveComponentPath } from "./capture/path.js";
export { createWebAdapter } from "./capture/web-adapter.js";

// --- Factors ---
export { queryFactors, queryComponentFactors } from "./factors/query.js";
export { queryFactorHistory, queryFactorDelta } from "./factors/snapshots.js";
export { factorSource, componentFactorSource, factorHistorySource } from "./factors/data-sources.js";
export { buildFactorDashboardSpec } from "./factors/dashboard-spec.js";
export { queryUserCluster, queryClusterMembers, kMeans } from "./factors/clustering.js";
export type { UserCluster, KMeansResult } from "./factors/clustering.js";

// --- Experiments ---
export { evaluateFlag } from "./experiment/index.js";
export { createExperiment, startExperiment } from "./experiment/index.js";
export type { ExperimentDefinition, VariantDefinition, CreatedExperiment } from "./experiment/index.js";
export { evaluateTargeting } from "./experiment/targeting.js";
export { queryExperimentResults } from "./experiment/results.js";
export { evaluateExperimentThresholds, concludeExperiment } from "./experiment/governance.js";
export { logGovernanceVerdict, runGovernanceCheck } from "./experiment/index.js";

// --- SDUI engine ---
export { validateSpec } from "./sdui/spec-validator.js";
export { resolveAllSources, type DataSourceCache, type ResolvedSources } from "./sdui/data-source.js";
export { dispatchAction } from "./sdui/action-dispatch.js";
export { loadSpec, type SpecStorage, type SignatureVerifier, type LoadedSpec } from "./sdui/spec-loader.js";
export { resolveBinding, resolveTextWithBindings, resolveProps, isBindingRef } from "./sdui/binding.js";
export { createSpecStorage, createDataSourceCache, devSignatureVerifier, type KVStorage } from "./sdui/default-storage.js";
export { createEd25519Verifier, createEd25519Signer, generateEd25519Keypair, type SpecSigner } from "./sdui/ed25519.js";

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
export type { TargetingRule, TargetingOperator, DeviceMetadata, MetadataTargetingRule, MetadataField, MetadataOperator, FactorTargetingRule } from "./experiment/targeting.js";
export type { VariantResult } from "./experiment/results.js";
export type { Threshold, GovernanceVerdict, GovernanceAction, FactorVerdict } from "./experiment/governance.js";
export { queryGovernanceLog, queryRecentGovernanceLog, queryGovernanceLogByVerdict, type GovernanceLogRow } from "./experiment/governance-log.js";
export { queryExperimentSummaries, queryExperimentSummary, type ExperimentSummaryRow, type ExperimentSummaryFilters } from "./experiment/dashboard.js";
