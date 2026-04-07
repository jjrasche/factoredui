/**
 * Resolves a DOM element to its auxi component path.
 * Traverses upward looking for data-auxi attributes:
 *   data-auxi-flow, data-auxi-page, data-auxi-component, data-auxi-element
 *
 * Returns a path like "onboarding/review/photo-grid/upload-button"
 */

const PATH_TIERS = [
  "data-auxi-flow",
  "data-auxi-page",
  "data-auxi-component",
  "data-auxi-element",
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
