import type { Spec, SpecNode, SpecNodeType } from "./spec-types.js";
import { RENDERER_VERSION } from "./spec-types.js";

/**
 * Validates an Spec for structural correctness and renderer compatibility.
 * Returns a list of errors. Empty list = valid spec.
 */

const VALID_TYPES: ReadonlySet<SpecNodeType> = new Set([
  "column", "row", "stack", "scrollview", "grid",
  "text", "image", "icon", "divider", "spacer",
  "textinput", "button", "toggle", "select", "slider",
  "card", "list", "tabs", "modal", "chip",
]);

const MAX_TREE_DEPTH = 20;

export function validateSpec(spec: Spec): string[] {
  const errors: string[] = [];

  validateEnvelope(spec, errors);
  validateNodeTree(spec.root, errors, 0);
  validateUniqueIds(spec.root, errors);

  return errors;
}

function validateEnvelope(spec: Spec, errors: string[]): void {
  if (typeof spec.spec_version !== "number" || spec.spec_version < 1) {
    errors.push("spec_version must be a positive integer");
  }
  if (typeof spec.renderer_min !== "number" || spec.renderer_min < 1) {
    errors.push("renderer_min must be a positive integer");
  }
  if (spec.renderer_min > RENDERER_VERSION) {
    errors.push(
      `spec requires renderer_min ${spec.renderer_min} but installed renderer is ${RENDERER_VERSION}`,
    );
  }
  if (!spec.root) {
    errors.push("spec must have a root node");
  }
}

function validateNodeTree(node: SpecNode, errors: string[], depth: number): void {
  if (depth > MAX_TREE_DEPTH) {
    errors.push(`node "${node.id}" exceeds max tree depth of ${MAX_TREE_DEPTH}`);
    return;
  }

  validateNodeStructure(node, errors);
  validateBindingRefs(node, errors);

  if (node.children) {
    for (const child of node.children) {
      validateNodeTree(child, errors, depth + 1);
    }
  }
}

function validateNodeStructure(node: SpecNode, errors: string[]): void {
  if (!node.id || typeof node.id !== "string") {
    errors.push("every node must have a string id");
  }
  if (!VALID_TYPES.has(node.type)) {
    errors.push(`node "${node.id}" has unknown type "${node.type}"`);
  }
  if (node.action && typeof node.action.action !== "string") {
    errors.push(`node "${node.id}" has action without a name`);
  }
}

function validateBindingRefs(node: SpecNode, errors: string[]): void {
  if (node.visible && !isValidBindingRef(node.visible)) {
    errors.push(`node "${node.id}" visible must be a binding ref like "{flag.name}"`);
  }

  if (node.props) {
    for (const [key, value] of Object.entries(node.props)) {
      validatePropValue(node.id, key, value, errors);
    }
  }
}

function validatePropValue(
  nodeId: string,
  key: string,
  value: unknown,
  errors: string[],
): void {
  if (typeof value === "string" && value.includes("{") && !isValidBindingRef(value)) {
    errors.push(`node "${nodeId}" prop "${key}" has malformed binding ref "${value}"`);
  }
}

function isValidBindingRef(value: string): boolean {
  if (!value.startsWith("{") || !value.endsWith("}")) return false;
  const path = value.slice(1, -1);
  return /^[a-zA-Z_][a-zA-Z0-9_.]*$/.test(path);
}

function validateUniqueIds(root: SpecNode, errors: string[]): void {
  const ids = new Set<string>();
  collectDuplicateIds(root, ids, errors);
}

function collectDuplicateIds(
  node: SpecNode,
  seen: Set<string>,
  errors: string[],
): void {
  if (seen.has(node.id)) {
    errors.push(`duplicate node id "${node.id}"`);
  }
  seen.add(node.id);

  if (node.children) {
    for (const child of node.children) {
      collectDuplicateIds(child, seen, errors);
    }
  }
}
