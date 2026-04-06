/**
 * Resolves a DOM element to its observe component path.
 * Traverses upward looking for data-observe attributes:
 *   data-observe-flow, data-observe-page, data-observe-component, data-observe-element
 *
 * Returns a path like "onboarding/review/photo-grid/upload-button"
 */

const PATH_TIERS = [
  "data-observe-flow",
  "data-observe-page",
  "data-observe-component",
  "data-observe-element",
] as const;

export function resolveComponentPath(target: Element): string {
  const segments = collectPathSegments(target);
  return segments.join("/") || "unknown";
}

function collectPathSegments(target: Element): string[] {
  const found: Map<string, string> = new Map();
  let current: Element | null = target;

  while (current) {
    for (const attr of PATH_TIERS) {
      if (!found.has(attr) && current.hasAttribute(attr)) {
        found.set(attr, current.getAttribute(attr)!);
      }
    }
    current = current.parentElement;
  }

  return PATH_TIERS.map((attr) => found.get(attr)).filter(
    (segment): segment is string => segment != null,
  );
}
