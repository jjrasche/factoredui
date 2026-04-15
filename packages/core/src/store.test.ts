import { describe, it, expectTypeOf } from "vitest";
import type { FactoredStore } from "./store.js";

describe("FactoredStore interface", () => {
  it("accepts a conforming implementation", () => {
    const mockStore: FactoredStore = {
      getCurrentUserId: async () => "user-1",
      insertEvents: async () => {},
      insertSession: async () => ({ id: "session-1" }),
      endSession: async () => {},
      queryFactors: async () => [],
      queryComponentFactors: async () => [],
      queryFactorHistory: async () => [],
      findClosestSnapshot: async () => null,
      queryUserCluster: async () => null,
      queryClusterMembers: async () => [],
      getAssignment: async () => null,
      getRunningExperiment: async () => null,
      hasConflictingAssignment: async () => false,
      getVariants: async () => [],
      writeAssignment: async () => {},
      recordExposure: async () => {},
      insertExperiment: async () => ({ id: "exp-1", name: "test", status: "draft", component_path: "a/b" }),
      insertVariants: async () => {},
      startExperiment: async () => {},
      getExperimentMeta: async () => null,
      queryThresholds: async () => [],
      concludeExperiment: async () => {},
      insertGovernanceVerdict: async () => {},
      queryGovernanceLog: async () => [],
      queryRecentGovernanceLog: async () => [],
      queryGovernanceLogByVerdict: async () => [],
      getAssignmentsByVariant: async () => new Map(),
      bulkFactorDeltas: async () => [],
      queryExperimentSummaries: async () => [],
      queryExperimentSummary: async () => [],
      loadActiveSpec: async () => null,
    };

    expectTypeOf(mockStore).toMatchTypeOf<FactoredStore>();
  });

  it("allows optional subscribe method", () => {
    const storeWithRealtime: FactoredStore = {
      getCurrentUserId: async () => null,
      insertEvents: async () => {},
      insertSession: async () => ({ id: "s" }),
      endSession: async () => {},
      queryFactors: async () => [],
      queryComponentFactors: async () => [],
      queryFactorHistory: async () => [],
      findClosestSnapshot: async () => null,
      queryUserCluster: async () => null,
      queryClusterMembers: async () => [],
      getAssignment: async () => null,
      getRunningExperiment: async () => null,
      hasConflictingAssignment: async () => false,
      getVariants: async () => [],
      writeAssignment: async () => {},
      recordExposure: async () => {},
      insertExperiment: async () => ({ id: "e", name: "n", status: "draft", component_path: "c" }),
      insertVariants: async () => {},
      startExperiment: async () => {},
      getExperimentMeta: async () => null,
      queryThresholds: async () => [],
      concludeExperiment: async () => {},
      insertGovernanceVerdict: async () => {},
      queryGovernanceLog: async () => [],
      queryRecentGovernanceLog: async () => [],
      queryGovernanceLogByVerdict: async () => [],
      getAssignmentsByVariant: async () => new Map(),
      bulkFactorDeltas: async () => [],
      queryExperimentSummaries: async () => [],
      queryExperimentSummary: async () => [],
      loadActiveSpec: async () => null,
      subscribe: (_channel, _table, _filter, _onInsert) => () => {},
    };

    expectTypeOf(storeWithRealtime).toMatchTypeOf<FactoredStore>();
    expectTypeOf(storeWithRealtime.subscribe).not.toBeUndefined();
  });
});
