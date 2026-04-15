import { describe, it, expect, vi } from "vitest";
import { factorSource, componentFactorSource, factorHistorySource } from "./data-sources";
import type { Factor } from "../types";
import type { ComponentFactorAggregate } from "./query";
import type { FactorSnapshot } from "./snapshots";

vi.mock("./query", () => ({
  queryFactors: vi.fn(),
  queryComponentFactors: vi.fn(),
}));

vi.mock("./snapshots", () => ({
  queryFactorHistory: vi.fn(),
}));

import { queryFactors, queryComponentFactors } from "./query";
import { queryFactorHistory } from "./snapshots";

const mockQueryFactors = vi.mocked(queryFactors);
const mockQueryComponentFactors = vi.mocked(queryComponentFactors);
const mockQueryFactorHistory = vi.mocked(queryFactorHistory);

const fakeStore = {} as never;

describe("factorSource", () => {
  it("returns a DataSourceConfig with local cache", () => {
    const config = factorSource(fakeStore, "user-1", "checkout/form");

    expect(config.cache).toBe("local");
    expect(typeof config.fetch).toBe("function");
  });

  it("fetch delegates to queryFactors", async () => {
    const expectedFactors: Factor[] = [{
      user_id: "user-1",
      component_path: "checkout/form",
      factor_name: "error_rate",
      factor_tier: "alarm",
      value: 0.25,
      computed_at: "2026-04-13T00:00:00Z",
    }];
    mockQueryFactors.mockResolvedValueOnce(expectedFactors);

    const config = factorSource(fakeStore, "user-1", "checkout/form");
    const result = await config.fetch();

    expect(mockQueryFactors).toHaveBeenCalledWith(fakeStore, "user-1", "checkout/form");
    expect(result).toEqual(expectedFactors);
  });
});

describe("componentFactorSource", () => {
  it("returns a DataSourceConfig with local cache", () => {
    const config = componentFactorSource(fakeStore, "checkout/form");

    expect(config.cache).toBe("local");
    expect(typeof config.fetch).toBe("function");
  });

  it("fetch delegates to queryComponentFactors", async () => {
    const expectedAggregates: ComponentFactorAggregate[] = [{
      component_path: "checkout/form",
      factor_name: "error_rate",
      factor_tier: "alarm",
      user_count: 230,
      avg_value: 0.12,
      median_value: 0.08,
      p95_value: 0.45,
      min_value: 0.0,
      max_value: 0.9,
      stddev_value: 0.15,
    }];
    mockQueryComponentFactors.mockResolvedValueOnce(expectedAggregates);

    const config = componentFactorSource(fakeStore, "checkout/form");
    const result = await config.fetch();

    expect(mockQueryComponentFactors).toHaveBeenCalledWith(fakeStore, "checkout/form");
    expect(result).toEqual(expectedAggregates);
  });
});

describe("factorHistorySource", () => {
  it("returns a DataSourceConfig with local cache", () => {
    const since = new Date("2026-03-01");
    const config = factorHistorySource(fakeStore, "user-1", "checkout/form", "error_rate", since);

    expect(config.cache).toBe("local");
    expect(typeof config.fetch).toBe("function");
  });

  it("fetch delegates to queryFactorHistory", async () => {
    const since = new Date("2026-03-01");
    const expectedSnapshots: FactorSnapshot[] = [{
      factor_name: "error_rate",
      factor_tier: "alarm",
      value: 0.25,
      snapshot_at: "2026-03-15T02:00:00Z",
    }];
    mockQueryFactorHistory.mockResolvedValueOnce(expectedSnapshots);

    const config = factorHistorySource(fakeStore, "user-1", "checkout/form", "error_rate", since);
    const result = await config.fetch();

    expect(mockQueryFactorHistory).toHaveBeenCalledWith(
      fakeStore, "user-1", "checkout/form", "error_rate", since,
    );
    expect(result).toEqual(expectedSnapshots);
  });
});
