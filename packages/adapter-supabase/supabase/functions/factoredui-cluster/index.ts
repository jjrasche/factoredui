import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const VECTOR_DIMENSIONS = 16;
const DEFAULT_K = 5;
const MAX_ITERATIONS = 50;
const CONVERGENCE_THRESHOLD = 1e-6;

interface VectorRow {
  user_id: string;
  vector: number[];
}

interface ClusterAssignment {
  user_id: string;
  cluster_id: number;
}

const MIN_K = 2;
const MAX_K = 50;

Deno.serve(async (req) => {
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  const authHeader = req.headers.get("Authorization");
  if (authHeader !== `Bearer ${serviceRoleKey}`) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      serviceRoleKey,
      { db: { schema: "factoredui" } },
    );

    const body = await parseRequestBody(req);
    const rawK = body.k ?? DEFAULT_K;

    if (!Number.isInteger(rawK) || rawK < MIN_K || rawK > MAX_K) {
      return jsonResponse({ error: `k must be integer between ${MIN_K} and ${MAX_K}` }, 400);
    }

    const vectors = await fetchVectors(supabase);
    if (vectors.length < rawK) {
      return jsonResponse({ clustered: 0, reason: "fewer vectors than clusters" });
    }

    const assignments = clusterVectors(vectors, rawK);
    const upsertedCount = await writeAssignments(supabase, assignments);

    return jsonResponse({ clustered: upsertedCount, k: rawK });
  } catch (err) {
    console.error("clustering failed:", err);
    return jsonResponse({ error: "internal error" }, 500);
  }
});

async function parseRequestBody(req: Request): Promise<{ k?: number }> {
  try {
    return await req.json();
  } catch {
    return {};
  }
}

async function fetchVectors(supabase: ReturnType<typeof createClient>): Promise<VectorRow[]> {
  const { data, error } = await supabase
    .from("user_factor_vectors")
    .select("user_id, vector");

  if (error) throw new Error(`fetchVectors failed: ${error.message}`);
  return (data ?? []) as VectorRow[];
}

/**
 * K-means clustering over factor vectors.
 * Uses k-means++ initialization for better convergence.
 */
function clusterVectors(vectors: VectorRow[], k: number): ClusterAssignment[] {
  const points = vectors.map(v => v.vector);
  const centroids = initializeCentroids(points, k);
  const labels = new Array<number>(points.length).fill(0);

  for (let iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
    assignPointsToCentroids(points, centroids, labels);
    const maxShift = updateCentroids(points, centroids, labels, k);

    if (maxShift < CONVERGENCE_THRESHOLD) break;
  }

  return vectors.map((v, i) => ({
    user_id: v.user_id,
    cluster_id: labels[i],
  }));
}

/**
 * K-means++ initialization: pick first centroid randomly,
 * subsequent centroids proportional to squared distance from nearest existing centroid.
 */
function initializeCentroids(points: number[][], k: number): number[][] {
  const centroids: number[][] = [];
  const firstIndex = Math.floor(Math.random() * points.length);
  centroids.push([...points[firstIndex]]);

  for (let c = 1; c < k; c++) {
    const distances = points.map(p => nearestCentroidDistance(p, centroids));
    const totalDistance = distances.reduce((sum, d) => sum + d, 0);
    const threshold = Math.random() * totalDistance;

    let cumulative = 0;
    for (let i = 0; i < points.length; i++) {
      cumulative += distances[i];
      if (cumulative >= threshold) {
        centroids.push([...points[i]]);
        break;
      }
    }
  }

  return centroids;
}

function nearestCentroidDistance(point: number[], centroids: number[][]): number {
  let minDist = Infinity;
  for (const centroid of centroids) {
    const dist = squaredEuclidean(point, centroid);
    if (dist < minDist) minDist = dist;
  }
  return minDist;
}

function assignPointsToCentroids(
  points: number[][],
  centroids: number[][],
  labels: number[],
): void {
  for (let i = 0; i < points.length; i++) {
    let minDist = Infinity;
    let bestCluster = 0;

    for (let c = 0; c < centroids.length; c++) {
      const dist = squaredEuclidean(points[i], centroids[c]);
      if (dist < minDist) {
        minDist = dist;
        bestCluster = c;
      }
    }

    labels[i] = bestCluster;
  }
}

function updateCentroids(
  points: number[][],
  centroids: number[][],
  labels: number[],
  k: number,
): number {
  let maxShift = 0;

  for (let c = 0; c < k; c++) {
    const newCentroid = new Array<number>(VECTOR_DIMENSIONS).fill(0);
    let count = 0;

    for (let i = 0; i < points.length; i++) {
      if (labels[i] !== c) continue;
      count++;
      for (let d = 0; d < VECTOR_DIMENSIONS; d++) {
        newCentroid[d] += points[i][d];
      }
    }

    if (count === 0) continue;

    for (let d = 0; d < VECTOR_DIMENSIONS; d++) {
      newCentroid[d] /= count;
    }

    const shift = squaredEuclidean(centroids[c], newCentroid);
    if (shift > maxShift) maxShift = shift;

    centroids[c] = newCentroid;
  }

  return maxShift;
}

function squaredEuclidean(a: number[], b: number[]): number {
  let sum = 0;
  for (let d = 0; d < a.length; d++) {
    const diff = a[d] - b[d];
    sum += diff * diff;
  }
  return sum;
}

async function writeAssignments(
  supabase: ReturnType<typeof createClient>,
  assignments: ClusterAssignment[],
): Promise<number> {
  const { error } = await supabase
    .from("user_clusters")
    .upsert(
      assignments.map(a => ({
        user_id: a.user_id,
        cluster_id: a.cluster_id,
        assigned_at: new Date().toISOString(),
      })),
      { onConflict: "user_id" },
    );

  if (error) throw new Error(`writeAssignments failed: ${error.message}`);
  return assignments.length;
}

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
