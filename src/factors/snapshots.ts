import type { SupabaseClient } from "@supabase/supabase-js";

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
  client: SupabaseClient,
  userId: string,
  componentPath: string,
  factorName: string,
  since: Date,
): Promise<FactorSnapshot[]> {
  const { data, error } = await client
    .from("factor_snapshots")
    .select("factor_name, factor_tier, value, snapshot_at")
    .eq("user_id", userId)
    .eq("component_path", componentPath)
    .eq("factor_name", factorName)
    .gte("snapshot_at", since.toISOString())
    .order("snapshot_at", { ascending: true });

  if (error) throw new Error(`queryFactorHistory failed: ${error.message}`);
  return data as FactorSnapshot[];
}

export async function queryFactorDelta(
  client: SupabaseClient,
  userId: string,
  componentPath: string,
  factorName: string,
  before: Date,
  after: Date,
): Promise<FactorDelta | null> {
  const beforeSnapshot = await findClosestSnapshot(
    client, userId, componentPath, factorName, before,
  );
  const afterSnapshot = await findClosestSnapshot(
    client, userId, componentPath, factorName, after,
  );

  if (!beforeSnapshot || !afterSnapshot) return null;

  return {
    factor_name: factorName,
    before: beforeSnapshot.value,
    after: afterSnapshot.value,
    delta: afterSnapshot.value - beforeSnapshot.value,
  };
}

async function findClosestSnapshot(
  client: SupabaseClient,
  userId: string,
  componentPath: string,
  factorName: string,
  targetDate: Date,
): Promise<FactorSnapshot | null> {
  const { data, error } = await client
    .from("factor_snapshots")
    .select("factor_name, factor_tier, value, snapshot_at")
    .eq("user_id", userId)
    .eq("component_path", componentPath)
    .eq("factor_name", factorName)
    .lte("snapshot_at", targetDate.toISOString())
    .order("snapshot_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) throw new Error(`findClosestSnapshot failed: ${error.message}`);
  return data as FactorSnapshot | null;
}
