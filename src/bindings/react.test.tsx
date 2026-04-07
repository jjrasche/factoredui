import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { type ReactNode } from "react";
import {
  ObserveProvider,
  useGovernanceLog,
  useRecentGovernanceLog,
  useExperimentDashboard,
} from "./react.js";

import * as governanceLogModule from "../experiment/governance-log.js";
import type { GovernanceLogRow } from "../experiment/governance-log.js";
import * as dashboardModule from "../experiment/dashboard.js";

vi.mock("../experiment/governance-log.js", () => ({
  queryGovernanceLog: vi.fn(),
  queryRecentGovernanceLog: vi.fn(),
}));

vi.mock("../experiment/dashboard.js", () => ({
  queryExperimentSummaries: vi.fn(),
}));

// initCapture is called in ObserveProvider's useEffect
vi.mock("../capture/index.js", () => ({
  initCapture: vi.fn(() => ({
    stopCapture: vi.fn(),
    flushEvents: vi.fn().mockResolvedValue(undefined),
    getSessionId: vi.fn(),
    trackNavigation: vi.fn(),
    trackImpression: vi.fn(),
  })),
}));

const mockChannelInstance = {
  on: vi.fn().mockReturnThis(),
  subscribe: vi.fn().mockReturnThis(),
};

const mockSupabase = {
  auth: { getUser: vi.fn() },
  channel: vi.fn(() => mockChannelInstance),
  removeChannel: vi.fn(),
} as never;

function createWrapper({ children }: { children: ReactNode }) {
  return (
    <ObserveProvider supabase={mockSupabase}>{children}</ObserveProvider>
  );
}

const GOVERNANCE_LOG_ROWS = [
  {
    id: "log-1",
    experiment_id: "exp-1",
    verdict: "continue" as const,
    winning_variant: null,
    factor_verdicts: [],
    evaluated_at: "2026-04-01T00:00:00Z",
  },
  {
    id: "log-2",
    experiment_id: "exp-1",
    verdict: "conclude" as const,
    winning_variant: "variant-a",
    factor_verdicts: [{ factor_name: "rage_click_rate", best_variant: "variant-a", best_delta: 0.3, control_delta: 0.1, is_significant: true }],
    evaluated_at: "2026-04-02T00:00:00Z",
  },
];

const EXPERIMENT_SUMMARIES = [
  {
    experiment_id: "exp-1",
    name: "button-color",
    component_path: "app/button",
    status: "running",
    winning_variant: null,
    created_at: "2026-03-01T00:00:00Z",
    concluded_at: null,
    variant_key: "control",
    traffic_percentage: 50,
    assigned_users: 100,
    exposed_users: 90,
  },
];

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useGovernanceLog", () => {
  it("returns governance log rows for an experiment", async () => {
    vi.mocked(governanceLogModule.queryGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.log).toEqual(GOVERNANCE_LOG_ROWS);
    expect(governanceLogModule.queryGovernanceLog).toHaveBeenCalledWith(mockSupabase, "exp-1");
  });

  it("returns empty log on query failure", async () => {
    vi.mocked(governanceLogModule.queryGovernanceLog).mockRejectedValue(new Error("db error"));

    const { result } = renderHook(() => useGovernanceLog("exp-bad"), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.log).toEqual([]);
  });
});

describe("useRecentGovernanceLog", () => {
  it("returns recent governance log entries with default limit", async () => {
    vi.mocked(governanceLogModule.queryRecentGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useRecentGovernanceLog(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.log).toEqual(GOVERNANCE_LOG_ROWS);
    expect(governanceLogModule.queryRecentGovernanceLog).toHaveBeenCalledWith(mockSupabase, 50);
  });

  it("passes custom limit to query function", async () => {
    vi.mocked(governanceLogModule.queryRecentGovernanceLog).mockResolvedValue([GOVERNANCE_LOG_ROWS[0]]);

    const { result } = renderHook(() => useRecentGovernanceLog(10), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(governanceLogModule.queryRecentGovernanceLog).toHaveBeenCalledWith(mockSupabase, 10);
    expect(result.current.log).toHaveLength(1);
  });
});

describe("useRecentGovernanceLog realtime", () => {
  it("subscribes to all governance_log inserts without experiment filter", async () => {
    vi.mocked(governanceLogModule.queryRecentGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useRecentGovernanceLog(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect((mockSupabase as any).channel).toHaveBeenCalledWith("governance-log:all");
    expect(mockChannelInstance.on).toHaveBeenCalledWith(
      "postgres_changes",
      expect.objectContaining({
        event: "INSERT",
        schema: "observe",
        table: "governance_log",
      }),
      expect.any(Function),
    );
    // No filter property — subscribes to all inserts
    const callArgs = mockChannelInstance.on.mock.calls.find(
      (call: unknown[]) => (call[1] as any)?.table === "governance_log" && !(call[1] as any)?.filter,
    );
    expect(callArgs).toBeDefined();
    expect(mockChannelInstance.subscribe).toHaveBeenCalled();
  });

  it("prepends new rows and trims to limit", async () => {
    vi.mocked(governanceLogModule.queryRecentGovernanceLog).mockResolvedValue([GOVERNANCE_LOG_ROWS[0]]);

    const { result } = renderHook(() => useRecentGovernanceLog(2), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.log).toHaveLength(1);

    const onCallback = mockChannelInstance.on.mock.calls.find(
      (call: unknown[]) => (call[1] as any)?.table === "governance_log" && !(call[1] as any)?.filter,
    )?.[2] as (payload: { new: GovernanceLogRow }) => void;

    const realtimeRow1: GovernanceLogRow = {
      id: "log-recent-rt-1",
      experiment_id: "exp-2",
      verdict: "continue",
      winning_variant: null,
      factor_verdicts: [],
      evaluated_at: "2026-04-04T00:00:00Z",
    };

    const realtimeRow2: GovernanceLogRow = {
      id: "log-recent-rt-2",
      experiment_id: "exp-3",
      verdict: "conclude",
      winning_variant: "variant-b",
      factor_verdicts: [],
      evaluated_at: "2026-04-05T00:00:00Z",
    };

    await act(async () => onCallback({ new: realtimeRow1 }));
    expect(result.current.log).toHaveLength(2);
    expect(result.current.log[0].id).toBe("log-recent-rt-1");

    await act(async () => onCallback({ new: realtimeRow2 }));
    // Trimmed to limit of 2
    expect(result.current.log).toHaveLength(2);
    expect(result.current.log[0].id).toBe("log-recent-rt-2");
    expect(result.current.log[1].id).toBe("log-recent-rt-1");
  });

  it("removes channel on unmount", async () => {
    vi.mocked(governanceLogModule.queryRecentGovernanceLog).mockResolvedValue([]);

    const { unmount } = renderHook(() => useRecentGovernanceLog(), { wrapper: createWrapper });

    await waitFor(() => {});
    unmount();

    expect((mockSupabase as any).removeChannel).toHaveBeenCalledWith(mockChannelInstance);
  });
});

describe("useExperimentDashboard", () => {
  it("returns experiment summaries without filters", async () => {
    vi.mocked(dashboardModule.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    const { result } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.summaries).toEqual(EXPERIMENT_SUMMARIES);
    expect(dashboardModule.queryExperimentSummaries).toHaveBeenCalledWith(mockSupabase, undefined);
  });

  it("passes filters to query function", async () => {
    vi.mocked(dashboardModule.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    const filters = { status: "running" };
    const { result } = renderHook(() => useExperimentDashboard(filters), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(dashboardModule.queryExperimentSummaries).toHaveBeenCalledWith(mockSupabase, filters);
  });

  it("returns empty summaries on query failure", async () => {
    vi.mocked(dashboardModule.queryExperimentSummaries).mockRejectedValue(new Error("db error"));

    const { result } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.summaries).toEqual([]);
  });
});

describe("realtime subscriptions", () => {
  it("useGovernanceLog subscribes to governance_log inserts on mount", async () => {
    vi.mocked(governanceLogModule.queryGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect((mockSupabase as any).channel).toHaveBeenCalledWith("governance-log:exp-1");
    expect(mockChannelInstance.on).toHaveBeenCalledWith(
      "postgres_changes",
      expect.objectContaining({
        event: "INSERT",
        schema: "observe",
        table: "governance_log",
        filter: "experiment_id=eq.exp-1",
      }),
      expect.any(Function),
    );
    expect(mockChannelInstance.subscribe).toHaveBeenCalled();
  });

  it("useGovernanceLog prepends new rows from realtime inserts", async () => {
    vi.mocked(governanceLogModule.queryGovernanceLog).mockResolvedValue([GOVERNANCE_LOG_ROWS[0]]);

    const { result } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.log).toHaveLength(1);

    // Simulate a realtime INSERT by invoking the callback passed to .on()
    const onCallback = mockChannelInstance.on.mock.calls.find(
      (call: unknown[]) => call[1]?.table === "governance_log",
    )?.[2] as (payload: { new: GovernanceLogRow }) => void;

    const realtimeRow: GovernanceLogRow = {
      id: "log-realtime",
      experiment_id: "exp-1",
      verdict: "flag_review",
      winning_variant: null,
      factor_verdicts: [],
      evaluated_at: "2026-04-03T00:00:00Z",
    };

    await act(async () => onCallback({ new: realtimeRow }));

    expect(result.current.log).toHaveLength(2);
    expect(result.current.log[0].id).toBe("log-realtime");
  });

  it("useGovernanceLog removes channel on unmount", async () => {
    vi.mocked(governanceLogModule.queryGovernanceLog).mockResolvedValue([]);

    const { unmount } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    await waitFor(() => {});
    unmount();

    expect((mockSupabase as any).removeChannel).toHaveBeenCalledWith(mockChannelInstance);
  });

  it("useExperimentDashboard subscribes to experiments table changes", async () => {
    vi.mocked(dashboardModule.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    const { result } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect((mockSupabase as any).channel).toHaveBeenCalledWith("experiments:changes");
    expect(mockChannelInstance.on).toHaveBeenCalledWith(
      "postgres_changes",
      expect.objectContaining({
        event: "*",
        schema: "observe",
        table: "experiments",
      }),
      expect.any(Function),
    );
  });

  it("useExperimentDashboard refetches on experiment table change", async () => {
    vi.mocked(dashboardModule.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => {
      expect(dashboardModule.queryExperimentSummaries).toHaveBeenCalledTimes(1);
    });

    // Simulate a realtime change on experiments table
    const onCallback = mockChannelInstance.on.mock.calls.find(
      (call: unknown[]) => call[1]?.table === "experiments",
    )?.[2] as () => void;

    const updatedSummaries = [{ ...EXPERIMENT_SUMMARIES[0], status: "concluded", winning_variant: "variant-a" }];
    vi.mocked(dashboardModule.queryExperimentSummaries).mockResolvedValue(updatedSummaries);

    await act(async () => onCallback());

    await waitFor(() => {
      expect(dashboardModule.queryExperimentSummaries).toHaveBeenCalledTimes(2);
    });
  });

  it("useExperimentDashboard removes channel on unmount", async () => {
    vi.mocked(dashboardModule.queryExperimentSummaries).mockResolvedValue([]);

    const { unmount } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => {});
    unmount();

    expect((mockSupabase as any).removeChannel).toHaveBeenCalledWith(mockChannelInstance);
  });
});
