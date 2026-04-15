import type { FactoredStore } from "../store.js";

export interface UserCluster {
  user_id: string;
  cluster_id: number;
  assigned_at: string;
}

/**
 * Queries the cluster assignment for a specific user.
 */
export async function queryUserCluster(
  store: FactoredStore,
  userId: string,
): Promise<UserCluster | null> {
  return store.queryUserCluster(userId);
}

/**
 * Queries all users in a specific cluster.
 */
export async function queryClusterMembers(
  store: FactoredStore,
  clusterId: number,
): Promise<UserCluster[]> {
  return store.queryClusterMembers(clusterId);
}

// --- Pure k-means algorithm (shared with edge function) ---

const MAX_ITERATIONS = 50;
const CONVERGENCE_THRESHOLD = 1e-6;

export interface KMeansResult {
  labels: number[];
  centroids: number[][];
  iterations: number;
}

/**
 * K-means clustering with k-means++ initialization.
 * Pure function — no I/O, deterministic given a seed.
 */
export function kMeans(
  points: number[][],
  k: number,
  seed?: number,
): KMeansResult {
  const rng = createSeededRng(seed ?? Date.now());
  const centroids = initializeCentroids(points, k, rng);
  const labels = new Array<number>(points.length).fill(0);

  let iteration = 0;
  for (; iteration < MAX_ITERATIONS; iteration++) {
    assignPointsToCentroids(points, centroids, labels);
    const maxShift = updateCentroids(points, centroids, labels, k);

    if (maxShift < CONVERGENCE_THRESHOLD) {
      iteration++;
      break;
    }
  }

  return { labels, centroids, iterations: iteration };
}

function initializeCentroids(
  points: number[][],
  k: number,
  rng: () => number,
): number[][] {
  const centroids: number[][] = [];
  const firstIndex = Math.floor(rng() * points.length);
  centroids.push([...points[firstIndex]]);

  for (let c = 1; c < k; c++) {
    const distances = points.map(p => nearestCentroidDistance(p, centroids));
    const totalDistance = distances.reduce((sum, d) => sum + d, 0);
    const threshold = rng() * totalDistance;

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
  const dimensions = points[0].length;
  let maxShift = 0;

  for (let c = 0; c < k; c++) {
    const newCentroid = new Array<number>(dimensions).fill(0);
    let count = 0;

    for (let i = 0; i < points.length; i++) {
      if (labels[i] !== c) continue;
      count++;
      for (let d = 0; d < dimensions; d++) {
        newCentroid[d] += points[i][d];
      }
    }

    if (count === 0) continue;

    for (let d = 0; d < dimensions; d++) {
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

// Mulberry32 PRNG — deterministic for reproducible tests
function createSeededRng(seed: number): () => number {
  let state = seed | 0;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
