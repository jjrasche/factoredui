export { initCapture } from "./capture/index.js";
export { evaluateFlag } from "./experiment/index.js";
export { resolveComponentPath } from "./capture/path.js";
export { queryFactors, queryComponentFactors } from "./factors/query.js";
export { queryFactorHistory, queryFactorDelta } from "./factors/snapshots.js";
export { evaluateTargeting } from "./experiment/targeting.js";

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
