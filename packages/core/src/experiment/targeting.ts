import type { Factor } from "../types.js";

export type TargetingOperator = "gt" | "gte" | "lt" | "lte" | "eq";

export interface TargetingRule {
  factor: string;
  operator: TargetingOperator;
  threshold: number;
}

export function evaluateTargeting(
  factors: Factor[],
  rules: TargetingRule[],
): boolean {
  if (rules.length === 0) return true;

  const factorsByName = indexFactorsByName(factors);
  return rules.every((rule) => evaluateRule(factorsByName, rule));
}

function indexFactorsByName(factors: Factor[]): Map<string, number> {
  const indexed = new Map<string, number>();
  for (const factor of factors) {
    indexed.set(factor.factor_name, factor.value);
  }
  return indexed;
}

function evaluateRule(
  factorsByName: Map<string, number>,
  rule: TargetingRule,
): boolean {
  const value = factorsByName.get(rule.factor);
  if (value === undefined) return false;

  return compareValue(value, rule.operator, rule.threshold);
}

function compareValue(
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
