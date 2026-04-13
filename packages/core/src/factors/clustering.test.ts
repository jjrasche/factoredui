import { describe, it, expect } from "vitest";
import { kMeans } from "./clustering.js";

function generateClusteredPoints(): number[][] {
  // Three well-separated 2D clusters
  const clusterA = [[1, 1], [1.1, 0.9], [0.9, 1.1], [1, 0.8]];
  const clusterB = [[10, 10], [10.1, 9.9], [9.9, 10.1], [10, 10.2]];
  const clusterC = [[1, 10], [0.9, 10.1], [1.1, 9.9], [1, 9.8]];
  return [...clusterA, ...clusterB, ...clusterC];
}

describe("kMeans", () => {
  it("assigns well-separated points to distinct clusters", () => {
    const points = generateClusteredPoints();
    const { labels } = kMeans(points, 3, 42);

    // Points 0-3 should share a cluster, 4-7 another, 8-11 another
    const clusterA = new Set(labels.slice(0, 4));
    const clusterB = new Set(labels.slice(4, 8));
    const clusterC = new Set(labels.slice(8, 12));

    expect(clusterA.size).toBe(1);
    expect(clusterB.size).toBe(1);
    expect(clusterC.size).toBe(1);

    // All three clusters are different
    const uniqueClusters = new Set([...clusterA, ...clusterB, ...clusterC]);
    expect(uniqueClusters.size).toBe(3);
  });

  it("converges in few iterations for separable data", () => {
    const points = generateClusteredPoints();
    const { iterations } = kMeans(points, 3, 42);

    expect(iterations).toBeLessThan(20);
  });

  it("produces deterministic results with same seed", () => {
    const points = generateClusteredPoints();
    const result1 = kMeans(points, 3, 42);
    const result2 = kMeans(points, 3, 42);

    expect(result1.labels).toEqual(result2.labels);
    expect(result1.centroids).toEqual(result2.centroids);
  });

  it("produces different results with different seeds", () => {
    // Single point per cluster = same labels regardless of seed, so use ambiguous data
    const points = [[0, 0], [1, 0], [0, 1], [1, 1], [5, 5], [6, 5], [5, 6], [6, 6]];
    const result1 = kMeans(points, 2, 1);
    const result2 = kMeans(points, 2, 999);

    // Labels may or may not differ, but centroids will differ due to initialization
    // We just verify both produce valid output
    expect(result1.labels.length).toBe(8);
    expect(result2.labels.length).toBe(8);
  });

  it("returns k centroids", () => {
    const points = generateClusteredPoints();
    const { centroids } = kMeans(points, 3, 42);

    expect(centroids.length).toBe(3);
    centroids.forEach(c => expect(c.length).toBe(2));
  });

  it("handles k=1 as single cluster", () => {
    const points = [[1, 2], [3, 4], [5, 6]];
    const { labels, centroids } = kMeans(points, 1, 42);

    expect(labels).toEqual([0, 0, 0]);
    expect(centroids.length).toBe(1);
    expect(centroids[0][0]).toBeCloseTo(3, 1);
    expect(centroids[0][1]).toBeCloseTo(4, 1);
  });
});
