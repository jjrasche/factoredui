/**
 * Binding resolution: resolves "{path.to.value}" references against a data context.
 * Pure functions. No side effects. No expressions — single-level path lookup only.
 */

const BINDING_PATTERN = /^\{([a-zA-Z_][a-zA-Z0-9_.]*)\}$/;
const INLINE_BINDING_PATTERN = /\{([a-zA-Z_][a-zA-Z0-9_.]*)\}/g;

export function isBindingRef(value: string): boolean {
  return BINDING_PATTERN.test(value);
}

export function resolveBinding(ref: string, context: Record<string, unknown>): unknown {
  const match = ref.match(BINDING_PATTERN);
  if (!match) return ref;
  return resolvePath(match[1], context);
}

export function resolveTextWithBindings(
  text: string,
  context: Record<string, unknown>,
): string {
  return text.replace(INLINE_BINDING_PATTERN, (_, path: string) => {
    const value = resolvePath(path, context);
    return value != null ? String(value) : "";
  });
}

export function resolveProps(
  props: Record<string, unknown>,
  context: Record<string, unknown>,
): Record<string, unknown> {
  const resolved: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(props)) {
    resolved[key] = resolveValue(value, context);
  }
  return resolved;
}

function resolveValue(value: unknown, context: Record<string, unknown>): unknown {
  if (typeof value === "string" && value.includes("{")) {
    if (isBindingRef(value)) return resolveBinding(value, context);
    return resolveTextWithBindings(value, context);
  }
  if (Array.isArray(value)) return value.map((v) => resolveValue(v, context));
  if (typeof value === "object" && value !== null) {
    return resolveProps(value as Record<string, unknown>, context);
  }
  return value;
}

function resolvePath(path: string, context: Record<string, unknown>): unknown {
  const segments = path.split(".");
  let current: unknown = context;

  for (const segment of segments) {
    if (current == null || typeof current !== "object") return undefined;
    current = (current as Record<string, unknown>)[segment];
  }

  return current;
}
