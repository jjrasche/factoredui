export { initCapture } from "./capture/index.js";
export { evaluateFlag } from "./experiment/index.js";
export { resolveComponentPath } from "./capture/path.js";
export { queryFactors, queryComponentFactors } from "./factors/query.js";

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
