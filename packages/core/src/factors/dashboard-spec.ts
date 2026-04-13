import type { Spec, SpecNode, ListProps } from "../sdui/spec-types.js";
import { RENDERER_VERSION } from "../sdui/spec-types.js";

/**
 * Generates an SDUI spec for a factor dashboard.
 * The spec expects a data source named "factors" bound to componentFactorSource output.
 * Each factor renders as a card with tier badge, name, and aggregate stats.
 */

export function buildFactorDashboardSpec(componentPath: string): Spec {
  return {
    spec_version: 1,
    renderer_min: RENDERER_VERSION,
    root: buildRoot(componentPath),
  };
}

function buildRoot(componentPath: string): SpecNode {
  return {
    id: "root",
    type: "column",
    props: { gap: 16, padding: 16 },
    children: [
      buildHeader(componentPath),
      buildFactorList(),
    ],
  };
}

function buildHeader(componentPath: string): SpecNode {
  return {
    id: "header",
    type: "column",
    props: { gap: 4 },
    children: [
      {
        id: "title",
        type: "text",
        props: { value: "Factor Dashboard", variant: "heading" },
      },
      {
        id: "subtitle",
        type: "text",
        props: { value: componentPath, variant: "caption", color: "#666" },
      },
    ],
  };
}

function buildFactorList(): SpecNode {
  const listProps: ListProps = {
    data: "{sources.factors}",
    emptyText: "No factors computed yet",
    itemTemplate: buildFactorCard(),
  };

  return {
    id: "factor-list",
    type: "list",
    props: { ...listProps },
  };
}

function buildFactorCard(): SpecNode {
  return {
    id: "factor-card",
    type: "card",
    props: { variant: "outlined" },
    children: [
      {
        id: "card-content",
        type: "column",
        props: { gap: 8, padding: 12 },
        children: [
          buildCardHeader(),
          buildCardStats(),
        ],
      },
    ],
  };
}

function buildCardHeader(): SpecNode {
  return {
    id: "card-header",
    type: "row",
    props: { justify: "between", align: "center" },
    children: [
      {
        id: "factor-name",
        type: "text",
        props: { value: "{item.factor_name}", variant: "subheading", bold: true },
      },
      {
        id: "tier-badge",
        type: "chip",
        props: { label: "{item.factor_tier}", variant: "filled" },
      },
    ],
  };
}

function buildCardStats(): SpecNode {
  return {
    id: "card-stats",
    type: "row",
    props: { gap: 16 },
    children: [
      buildStatColumn("stat-avg", "Avg", "{item.avg_value}"),
      buildStatColumn("stat-p95", "P95", "{item.p95_value}"),
      buildStatColumn("stat-users", "Users", "{item.user_count}"),
    ],
  };
}

function buildStatColumn(id: string, label: string, valueRef: string): SpecNode {
  return {
    id,
    type: "column",
    props: { align: "center", gap: 2 },
    children: [
      {
        id: `${id}-label`,
        type: "text",
        props: { value: label, variant: "caption", color: "#888" },
      },
      {
        id: `${id}-value`,
        type: "text",
        props: { value: valueRef, variant: "body", bold: true },
      },
    ],
  };
}
