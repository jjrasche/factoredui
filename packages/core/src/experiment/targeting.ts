import type { Factor } from "../types.js";

export type TargetingOperator = "gt" | "gte" | "lt" | "lte" | "eq";

export interface FactorTargetingRule {
  type: "factor";
  factor: string;
  operator: TargetingOperator;
  threshold: number;
}

export type MetadataField =
  | "os_name"
  | "os_version"
  | "manufacturer"
  | "model"
  | "app_version"
  | "app_build"
  | "platform";

export type MetadataOperator = "eq" | "neq" | "contains" | "gte" | "lte";

export interface MetadataTargetingRule {
  type: "metadata";
  field: MetadataField;
  operator: MetadataOperator;
  value: string;
}

/** Legacy format (factor-only) for backward compatibility with existing experiments */
export interface LegacyTargetingRule {
  factor: string;
  operator: TargetingOperator;
  threshold: number;
}

export type TargetingRule = FactorTargetingRule | MetadataTargetingRule | LegacyTargetingRule;

export interface DeviceMetadata {
  os_name?: string;
  os_version?: string;
  manufacturer?: string;
  model?: string;
  app_version?: string;
  app_build?: string;
  platform?: string;
}

export function evaluateTargeting(
  factors: Factor[],
  rules: TargetingRule[],
  deviceMetadata?: DeviceMetadata,
): boolean {
  if (rules.length === 0) return true;

  const factorsByName = indexFactorsByName(factors);
  return rules.every((rule) => evaluateRule(factorsByName, rule, deviceMetadata));
}

function indexFactorsByName(factors: Factor[]): Map<string, number> {
  const indexed = new Map<string, number>();
  for (const factor of factors) {
    indexed.set(factor.factor_name, factor.value);
  }
  return indexed;
}

function isMetadataRule(rule: TargetingRule): rule is MetadataTargetingRule {
  return "type" in rule && rule.type === "metadata";
}

function isFactorRule(rule: TargetingRule): rule is FactorTargetingRule | LegacyTargetingRule {
  return !("type" in rule) || rule.type === "factor";
}

function evaluateRule(
  factorsByName: Map<string, number>,
  rule: TargetingRule,
  deviceMetadata?: DeviceMetadata,
): boolean {
  if (isMetadataRule(rule)) {
    return evaluateMetadataRule(rule, deviceMetadata);
  }

  if (isFactorRule(rule)) {
    const threshold = "threshold" in rule ? rule.threshold : 0;
    const factorName = rule.factor;
    const value = factorsByName.get(factorName);
    if (value === undefined) return false;
    return compareNumeric(value, rule.operator, threshold);
  }

  return false;
}

function evaluateMetadataRule(
  rule: MetadataTargetingRule,
  metadata?: DeviceMetadata,
): boolean {
  if (!metadata) return false;

  const fieldValue = metadata[rule.field];
  if (fieldValue === undefined || fieldValue === null) return false;

  return compareString(fieldValue, rule.operator, rule.value);
}

function compareNumeric(
  value: number,
  operator: TargetingOperator,
  threshold: number,
): boolean {
  switch (operator) {
    case "gt": return value > threshold;
    case "gte": return value >= threshold;
    case "lt": return value < threshold;
    case "lte": return value <= threshold;
    case "eq": return value === threshold;
  }
}

function compareString(
  value: string,
  operator: MetadataOperator,
  target: string,
): boolean {
  const lower = value.toLowerCase();
  const targetLower = target.toLowerCase();
  switch (operator) {
    case "eq": return lower === targetLower;
    case "neq": return lower !== targetLower;
    case "contains": return lower.includes(targetLower);
    case "gte": return lower >= targetLower;
    case "lte": return lower <= targetLower;
  }
}
