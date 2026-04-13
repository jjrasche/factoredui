import { describe, it, expect } from "vitest";
import { buildFactorDashboardSpec } from "./dashboard-spec";
import { validateSpec } from "../sdui/spec-validator";

describe("buildFactorDashboardSpec", () => {
  it("produces a valid spec", () => {
    const spec = buildFactorDashboardSpec("checkout/form");
    const errors = validateSpec(spec);
    expect(errors).toHaveLength(0);
  });

  it("includes the component path in subtitle", () => {
    const spec = buildFactorDashboardSpec("settings/profile");
    const subtitle = findNodeById(spec.root, "subtitle");

    expect(subtitle).not.toBeNull();
    expect(subtitle!.props!.value).toBe("settings/profile");
  });

  it("has a list bound to sources.factors", () => {
    const spec = buildFactorDashboardSpec("checkout/form");
    const list = findNodeById(spec.root, "factor-list");

    expect(list).not.toBeNull();
    expect(list!.type).toBe("list");
    expect(list!.props!.data).toBe("{sources.factors}");
  });

  it("has stat columns for avg, p95, and user count", () => {
    const spec = buildFactorDashboardSpec("checkout/form");
    const avgLabel = findNodeById(spec.root, "stat-avg-label");
    const p95Label = findNodeById(spec.root, "stat-p95-label");
    const usersLabel = findNodeById(spec.root, "stat-users-label");

    expect(avgLabel!.props!.value).toBe("Avg");
    expect(p95Label!.props!.value).toBe("P95");
    expect(usersLabel!.props!.value).toBe("Users");
  });

  it("has a tier badge chip", () => {
    const spec = buildFactorDashboardSpec("checkout/form");
    const badge = findNodeById(spec.root, "tier-badge");

    expect(badge).not.toBeNull();
    expect(badge!.type).toBe("chip");
    expect(badge!.props!.label).toBe("{item.factor_tier}");
  });

  it("uses unique node ids throughout", () => {
    const spec = buildFactorDashboardSpec("checkout/form");
    const ids = collectIds(spec.root);
    const uniqueIds = new Set(ids);

    expect(ids.length).toBe(uniqueIds.size);
  });
});

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function findNodeById(node: any, targetId: string): any | null {
  if (node.id === targetId) return node;

  if (node.children) {
    for (const child of node.children) {
      const found = findNodeById(child, targetId);
      if (found) return found;
    }
  }

  if (node.props?.itemTemplate) {
    const found = findNodeById(node.props.itemTemplate, targetId);
    if (found) return found;
  }

  return null;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function collectIds(node: any): string[] {
  const ids = [node.id as string];

  if (node.children) {
    for (const child of node.children) {
      ids.push(...collectIds(child));
    }
  }

  if (node.props?.itemTemplate) {
    ids.push(...collectIds(node.props.itemTemplate));
  }

  return ids;
}
