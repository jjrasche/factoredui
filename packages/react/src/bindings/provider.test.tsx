import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { type ReactNode } from "react";
import {
  Provider,
  useGovernanceLog,
  useRecentGovernanceLog,
  useExperimentDashboard,
} from "./provider.js";

import * as core from "@factoredui/core";
import type { GovernanceLogRow, FactoredStore } from "@factoredui/core";

vi.mock("@factoredui/core", async () => {
  const actual = await vi.importActual("@factoredui/core");
  return {
    ...actual,
    initCapture: vi.fn(() => ({
      stopCapture: vi.fn(),
      flushEvents: vi.fn().mockResolvedValue(undefined),
      getSessionId: vi.fn(),
      trackNavigation: vi.fn(),
      trackImpression: vi.fn(),
    })),
    queryGovernanceLog: vi.fn(),
    queryRecentGovernanceLog: vi.fn(),
    queryExperimentSummaries: vi.fn(),
  };
});

// Track subscribe calls so tests can invoke the onInsert callback
const subscribeCallbacks: Array<{ channel: string; table: string; filter: string | null; onInsert: (row: unknown) => void }> = [];
const unsubscribeMock = vi.fn();

const mockStore = {
  getCurrentUserId: vi.fn().mockResolvedValue("user-1"),
  subscribe: vi.fn((channel: string, table: string, filter: string | null, onInsert: (row: unknown) => void) => {
    subscribeCallbacks.push({ channel, table, filter, onInsert });
    return unsubscribeMock;
  }),
} as unknown as FactoredStore;

const mockAdapter = {
  startListening: vi.fn(),
  stopListening: vi.fn(),
  collectSessionMetadata: vi.fn(() => ({})),
  storeSessionId: vi.fn(),
  loadSessionId: vi.fn(() => null),
  clearSessionId: vi.fn(),
  registerUnloadHandler: vi.fn(),
};

function createWrapper({ children }: { children: ReactNode }) {
  return (
    <Provider store={mockStore} adapter={mockAdapter} platform="web">
      {children}
    </Provider>
  );
}

const GOVERNANCE_LOG_ROWS: GovernanceLogRow[] = [
  {
    id: "log-1",
    experiment_id: "exp-1",
    verdict: "continue",
    winning_variant: null,
    factor_verdicts: [],
    evaluated_at: "2026-04-01T00:00:00Z",
  },
  {
    id: "log-2",
    experiment_id: "exp-1",
    verdict: "conclude",
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
  subscribeCallbacks.length = 0;
});

describe("useGovernanceLog", () => {
  it("returns governance log rows for an experiment", async () => {
    vi.mocked(core.queryGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.log).toEqual(GOVERNANCE_LOG_ROWS);
    expect(core.queryGovernanceLog).toHaveBeenCalledWith(mockStore, "exp-1");
  });

  it("returns empty log on query failure", async () => {
    vi.mocked(core.queryGovernanceLog).mockRejectedValue(new Error("db error"));

    const { result } = renderHook(() => useGovernanceLog("exp-bad"), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.log).toEqual([]);
  });
});

describe("useRecentGovernanceLog", () => {
  it("returns recent governance log entries with default limit", async () => {
    vi.mocked(core.queryRecentGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useRecentGovernanceLog(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.log).toEqual(GOVERNANCE_LOG_ROWS);
    expect(core.queryRecentGovernanceLog).toHaveBeenCalledWith(mockStore, 50);
  });

  it("passes custom limit to query function", async () => {
    vi.mocked(core.queryRecentGovernanceLog).mockResolvedValue([GOVERNANCE_LOG_ROWS[0]]);

    const { result } = renderHook(() => useRecentGovernanceLog(10), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(core.queryRecentGovernanceLog).toHaveBeenCalledWith(mockStore, 10);
    expect(result.current.log).toHaveLength(1);
  });
});

describe("useRecentGovernanceLog realtime", () => {
  it("subscribes to all governance_log inserts without experiment filter", async () => {
    vi.mocked(core.queryRecentGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useRecentGovernanceLog(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    const sub = subscribeCallbacks.find(s => s.channel === "governance-log:all");
    expect(sub).toBeDefined();
    expect(sub!.table).toBe("governance_log");
    expect(sub!.filter).toBeNull();
  });

  it("prepends new rows and trims to limit", async () => {
    vi.mocked(core.queryRecentGovernanceLog).mockResolvedValue([GOVERNANCE_LOG_ROWS[0]]);

    const { result } = renderHook(() => useRecentGovernanceLog(2), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.log).toHaveLength(1);

    const sub = subscribeCallbacks.find(s => s.channel === "governance-log:all");

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

    await act(async () => sub!.onInsert(realtimeRow1));
    expect(result.current.log).toHaveLength(2);
    expect(result.current.log[0].id).toBe("log-recent-rt-1");

    await act(async () => sub!.onInsert(realtimeRow2));
    expect(result.current.log).toHaveLength(2);
    expect(result.current.log[0].id).toBe("log-recent-rt-2");
    expect(result.current.log[1].id).toBe("log-recent-rt-1");
  });

  it("removes channel on unmount", async () => {
    vi.mocked(core.queryRecentGovernanceLog).mockResolvedValue([]);

    const { unmount } = renderHook(() => useRecentGovernanceLog(), { wrapper: createWrapper });

    await waitFor(() => {});
    unmount();

    expect(unsubscribeMock).toHaveBeenCalled();
  });
});

describe("useExperimentDashboard", () => {
  it("returns experiment summaries without filters", async () => {
    vi.mocked(core.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    const { result } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.summaries).toEqual(EXPERIMENT_SUMMARIES);
    expect(core.queryExperimentSummaries).toHaveBeenCalledWith(mockStore, undefined);
  });

  it("passes filters to query function", async () => {
    vi.mocked(core.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    const filters = { status: "running" };
    const { result } = renderHook(() => useExperimentDashboard(filters), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(core.queryExperimentSummaries).toHaveBeenCalledWith(mockStore, filters);
  });

  it("returns empty summaries on query failure", async () => {
    vi.mocked(core.queryExperimentSummaries).mockRejectedValue(new Error("db error"));

    const { result } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.summaries).toEqual([]);
  });
});

describe("realtime subscriptions", () => {
  it("useGovernanceLog subscribes to governance_log inserts on mount", async () => {
    vi.mocked(core.queryGovernanceLog).mockResolvedValue(GOVERNANCE_LOG_ROWS);

    const { result } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    const sub = subscribeCallbacks.find(s => s.channel === "governance-log:exp-1");
    expect(sub).toBeDefined();
    expect(sub!.table).toBe("governance_log");
    expect(sub!.filter).toBe("experiment_id=eq.exp-1");
  });

  it("useGovernanceLog prepends new rows from realtime inserts", async () => {
    vi.mocked(core.queryGovernanceLog).mockResolvedValue([GOVERNANCE_LOG_ROWS[0]]);

    const { result } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.log).toHaveLength(1);

    const sub = subscribeCallbacks.find(s => s.channel === "governance-log:exp-1");

    const realtimeRow: GovernanceLogRow = {
      id: "log-realtime",
      experiment_id: "exp-1",
      verdict: "flag_review",
      winning_variant: null,
      factor_verdicts: [],
      evaluated_at: "2026-04-03T00:00:00Z",
    };

    await act(async () => sub!.onInsert(realtimeRow));

    expect(result.current.log).toHaveLength(2);
    expect(result.current.log[0].id).toBe("log-realtime");
  });

  it("useGovernanceLog removes channel on unmount", async () => {
    vi.mocked(core.queryGovernanceLog).mockResolvedValue([]);

    const { unmount } = renderHook(() => useGovernanceLog("exp-1"), { wrapper: createWrapper });

    await waitFor(() => {});
    unmount();

    expect(unsubscribeMock).toHaveBeenCalled();
  });

  it("useExperimentDashboard subscribes to experiments table changes", async () => {
    vi.mocked(core.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    const { result } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    const sub = subscribeCallbacks.find(s => s.channel === "experiments:changes");
    expect(sub).toBeDefined();
    expect(sub!.table).toBe("experiments");
  });

  it("useExperimentDashboard refetches on experiment table change", async () => {
    vi.mocked(core.queryExperimentSummaries).mockResolvedValue(EXPERIMENT_SUMMARIES);

    renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => {
      expect(core.queryExperimentSummaries).toHaveBeenCalledTimes(1);
    });

    const sub = subscribeCallbacks.find(s => s.channel === "experiments:changes");

    const updatedSummaries = [{ ...EXPERIMENT_SUMMARIES[0], status: "concluded", winning_variant: "variant-a" }];
    vi.mocked(core.queryExperimentSummaries).mockResolvedValue(updatedSummaries);

    await act(async () => sub!.onInsert({}));

    await waitFor(() => {
      expect(core.queryExperimentSummaries).toHaveBeenCalledTimes(2);
    });
  });

  it("useExperimentDashboard removes channel on unmount", async () => {
    vi.mocked(core.queryExperimentSummaries).mockResolvedValue([]);

    const { unmount } = renderHook(() => useExperimentDashboard(), { wrapper: createWrapper });

    await waitFor(() => {});
    unmount();

    expect(unsubscribeMock).toHaveBeenCalled();
  });
});
