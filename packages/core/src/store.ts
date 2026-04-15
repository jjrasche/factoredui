import type { Factor, ExperimentAssignment } from "./types.js";
import type { ComponentFactorAggregate } from "./factors/query.js";
import type { FactorSnapshot } from "./factors/snapshots.js";
import type { UserCluster } from "./factors/clustering.js";
import type { TargetingRule } from "./experiment/targeting.js";
import type { Threshold, GovernanceAction, FactorVerdict } from "./experiment/governance.js";
import type { GovernanceLogRow } from "./experiment/governance-log.js";
import type { ExperimentSummaryRow, ExperimentSummaryFilters } from "./experiment/dashboard.js";
import type { CreatedExperiment, VariantDefinition } from "./experiment/lifecycle.js";
import type { SignedSpec } from "./sdui/spec-types.js";

// --- Types introduced by the store contract ---

export interface EventRow {
  user_id: string;
  session_id: string;
  event_type: string;
  component_path: string;
  payload: Record<string, unknown>;
}

export interface RunningExperiment {
  id: string;
  name: string;
  component_path: string;
  targeting_rules: TargetingRule[];
  platforms: string[];
}

export interface VariantWithTraffic {
  variant_key: string;
  config: Record<string, unknown>;
  traffic_percentage: number;
}

export interface ExperimentMeta {
  component_path: string;
  created_at: string;
}

export interface ExperimentInsertRow {
  name: string;
  description: string | null;
  component_path: string;
  targeting_rules: TargetingRule[];
  platforms: string[];
}

export interface BulkDeltaRow {
  factor_name: string;
  avg_before: number;
  avg_after: number;
  avg_delta: number;
}

export type Unsubscribe = () => void;

// --- The persistence interface ---

export interface FactoredStore {
  // Auth
  getCurrentUserId(): Promise<string | null>;

  // Capture
  insertEvents(events: EventRow[]): Promise<void>;
  insertSession(userId: string, metadata: Record<string, unknown>): Promise<{ id: string }>;
  endSession(sessionId: string): Promise<void>;

  // Factors
  queryFactors(userId: string, componentPath?: string): Promise<Factor[]>;
  queryComponentFactors(componentPath: string): Promise<ComponentFactorAggregate[]>;
  queryFactorHistory(
    userId: string,
    componentPath: string,
    factorName: string,
    since: Date,
  ): Promise<FactorSnapshot[]>;
  findClosestSnapshot(
    userId: string,
    componentPath: string,
    factorName: string,
    targetDate: Date,
  ): Promise<FactorSnapshot | null>;

  // Clustering
  queryUserCluster(userId: string): Promise<UserCluster | null>;
  queryClusterMembers(clusterId: number): Promise<UserCluster[]>;

  // Experiments — flag evaluation
  getAssignment(userId: string, experimentName: string): Promise<ExperimentAssignment | null>;
  getRunningExperiment(experimentName: string): Promise<RunningExperiment | null>;
  hasConflictingAssignment(
    userId: string,
    componentPath: string,
    excludeExperimentId: string,
  ): Promise<boolean>;
  getVariants(experimentId: string): Promise<VariantWithTraffic[]>;
  writeAssignment(userId: string, experimentId: string, variantKey: string): Promise<void>;
  recordExposure(userId: string, experimentId: string, variantKey: string): Promise<void>;

  // Experiments — lifecycle
  insertExperiment(row: ExperimentInsertRow): Promise<CreatedExperiment>;
  insertVariants(experimentId: string, variants: VariantDefinition[]): Promise<void>;
  startExperiment(experimentId: string): Promise<void>;

  // Experiments — governance
  getExperimentMeta(experimentId: string): Promise<ExperimentMeta | null>;
  queryThresholds(factorNames: string[], componentPath: string): Promise<Threshold[]>;
  concludeExperiment(experimentId: string, winningVariant: string): Promise<void>;
  insertGovernanceVerdict(
    experimentId: string,
    verdict: GovernanceAction,
    winningVariant: string | null,
    factorVerdicts: FactorVerdict[],
  ): Promise<void>;

  // Experiments — governance log
  queryGovernanceLog(experimentId: string): Promise<GovernanceLogRow[]>;
  queryRecentGovernanceLog(limit: number): Promise<GovernanceLogRow[]>;
  queryGovernanceLogByVerdict(verdict: GovernanceAction): Promise<GovernanceLogRow[]>;

  // Experiments — results
  getAssignmentsByVariant(experimentId: string): Promise<Map<string, string[]>>;
  bulkFactorDeltas(
    userIds: string[],
    componentPath: string,
    factorNames: string[],
    before: string,
    after: string,
  ): Promise<BulkDeltaRow[]>;

  // Dashboard
  queryExperimentSummaries(filters?: ExperimentSummaryFilters): Promise<ExperimentSummaryRow[]>;
  queryExperimentSummary(experimentId: string): Promise<ExperimentSummaryRow[]>;

  // SDUI
  loadActiveSpec(platform: string): Promise<SignedSpec | null>;

  // Realtime (optional — not all backends support it)
  subscribe?(
    channel: string,
    table: string,
    filter: string | null,
    onInsert: (row: unknown) => void,
  ): Unsubscribe;
}
