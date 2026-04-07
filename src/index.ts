export { initCapture } from "./capture/index.js";
export { evaluateFlag } from "./experiment/index.js";
export { resolveComponentPath } from "./capture/path.js";
export { queryFactors, queryComponentFactors } from "./factors/query.js";
export { queryFactorHistory, queryFactorDelta } from "./factors/snapshots.js";
export { evaluateTargeting } from "./experiment/targeting.js";
export { queryExperimentResults } from "./experiment/results.js";
export { evaluateExperimentThresholds, concludeExperiment } from "./experiment/governance.js";

export type {
  ObserveConfig,
  ObserveEvent,
  ObserveSession,
  CaptureHandle,
  EventType,
  FactorTier,
  Factor,
  ExperimentAssignment,
} from "./types.js";

export type { ComponentFactorAggregate } from "./factors/query.js";
export type { FactorSnapshot, FactorDelta } from "./factors/snapshots.js";
export type { TargetingRule, TargetingOperator } from "./experiment/targeting.js";
export type { VariantResult } from "./experiment/results.js";
export type { Threshold, GovernanceVerdict, GovernanceAction, FactorVerdict } from "./experiment/governance.js";
export type { GovernanceLogRow } from "./experiment/governance-log.js";
export type { ExperimentSummaryRow, ExperimentSummaryFilters } from "./experiment/dashboard.js";
