/**
 * Resolves a DOM element to its component path.
 * Traverses upward looking for data-factored attributes:
 *   data-factored-flow, data-factored-page, data-factored-component, data-factored-element
 *
 * Returns a path like "onboarding/review/photo-grid/upload-button"
 */

const PATH_TIERS = [
  "data-factored-flow",
  "data-factored-page",
  "data-factored-component",
  "data-factored-element",
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
