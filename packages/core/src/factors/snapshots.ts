import type { FactoredStore } from "../store.js";

export interface FactorSnapshot {
  factor_name: string;
  factor_tier: string;
  value: number;
  snapshot_at: string;
}

export interface FactorDelta {
  factor_name: string;
  before: number;
  after: number;
  delta: number;
}

export async function queryFactorHistory(
  store: FactoredStore,
  userId: string,
  componentPath: string,
  factorName: string,
  since: Date,
): Promise<FactorSnapshot[]> {
  return store.queryFactorHistory(userId, componentPath, factorName, since);
}

export async function queryFactorDelta(
  store: FactoredStore,
  userId: string,
  componentPath: string,
  factorName: string,
  before: Date,
  after: Date,
): Promise<FactorDelta | null> {
  const beforeSnapshot = await store.findClosestSnapshot(
    userId, componentPath, factorName, before,
  );
  const afterSnapshot = await store.findClosestSnapshot(
    userId, componentPath, factorName, after,
  );

  if (!beforeSnapshot || !afterSnapshot) return null;

  return {
    factor_name: factorName,
    before: beforeSnapshot.value,
    after: afterSnapshot.value,
    delta: afterSnapshot.value - beforeSnapshot.value,
  };
}
