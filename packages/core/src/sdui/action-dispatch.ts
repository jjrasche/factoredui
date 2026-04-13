import type { ActionRef, ActionRegistry } from "./spec-types.js";
import { resolveProps } from "./binding.js";

/**
 * Dispatches named actions from spec nodes.
 * Actions are defined by the host app, not the spec.
 * The spec references actions by name; this module resolves and invokes them.
 */

export function dispatchAction(
  actionRef: ActionRef,
  registry: ActionRegistry,
  context: Record<string, unknown>,
): void {
  const handler = registry[actionRef.action];
  if (!handler) {
    console.warn(`factoredui: unknown action "${actionRef.action}"`);
    return;
  }

  const resolvedParams = actionRef.params
    ? resolveProps(actionRef.params as Record<string, unknown>, context)
    : {};

  Promise.resolve(handler(resolvedParams)).catch((err) => {
    console.error(`factoredui: action "${actionRef.action}" failed:`, err);
  });
}
