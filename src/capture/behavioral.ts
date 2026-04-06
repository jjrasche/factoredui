import type { ObserveEvent } from "../types.js";
import { resolveComponentPath } from "./path.js";

/**
 * Behavioral detectors run client-side and emit first-class event types:
 * - rage_click: 3+ clicks within 500ms on the same target
 * - dead_click: click with no DOM mutation within 1000ms
 * - scroll_reversal: scroll direction change within 300ms
 */

const RAGE_CLICK_THRESHOLD = 3;
const RAGE_CLICK_WINDOW_MS = 500;
const DEAD_CLICK_WAIT_MS = 1000;
const SCROLL_REVERSAL_WINDOW_MS = 300;

export interface BehavioralDetector {
  startDetecting: () => void;
  stopDetecting: () => void;
}

export function createBehavioralDetector(
  onEvent: (event: ObserveEvent) => void,
): BehavioralDetector {
  const rageClickState = createRageClickState();
  const scrollState = createScrollState();

  function handleRageClick(event: MouseEvent): void {
    const target = event.target as Element | null;
    if (!target) return;

    const componentPath = resolveComponentPath(target);
    const isRage = recordClickAndDetectRage(
      rageClickState,
      componentPath,
      Date.now(),
    );

    if (isRage) {
      onEvent({
        event_type: "rage_click",
        component_path: componentPath,
        payload: { click_count: rageClickState.count, x: event.clientX, y: event.clientY },
      });
    }
  }

  function handleDeadClick(event: MouseEvent): void {
    const target = event.target as Element | null;
    if (!target) return;

    const componentPath = resolveComponentPath(target);
    detectDeadClick(target, componentPath, onEvent);
  }

  function handleScroll(): void {
    const scrollY = window.scrollY;
    const now = Date.now();
    const reversal = detectScrollReversal(scrollState, scrollY, now);

    if (reversal) {
      onEvent({
        event_type: "scroll_reversal",
        component_path: resolveComponentPath(document.documentElement),
        payload: { scroll_y: scrollY, direction: reversal },
      });
    }
  }

  function startDetecting(): void {
    document.addEventListener("click", handleRageClick, { capture: true });
    document.addEventListener("click", handleDeadClick, { capture: true });
    window.addEventListener("scroll", handleScroll, { passive: true });
  }

  function stopDetecting(): void {
    document.removeEventListener("click", handleRageClick, { capture: true });
    document.removeEventListener("click", handleDeadClick, { capture: true });
    window.removeEventListener("scroll", handleScroll);
  }

  return { startDetecting, stopDetecting };
}

// --- Rage click detection ---

interface RageClickState {
  targetPath: string | null;
  timestamps: number[];
  count: number;
}

function createRageClickState(): RageClickState {
  return { targetPath: null, timestamps: [], count: 0 };
}

function recordClickAndDetectRage(
  state: RageClickState,
  componentPath: string,
  now: number,
): boolean {
  if (state.targetPath !== componentPath) {
    state.targetPath = componentPath;
    state.timestamps = [now];
    state.count = 1;
    return false;
  }

  state.timestamps.push(now);
  state.timestamps = state.timestamps.filter(
    (ts) => now - ts < RAGE_CLICK_WINDOW_MS,
  );
  state.count = state.timestamps.length;

  return state.count >= RAGE_CLICK_THRESHOLD;
}

// --- Dead click detection ---

function detectDeadClick(
  target: Element,
  componentPath: string,
  onEvent: (event: ObserveEvent) => void,
): void {
  let hasMutation = false;

  const observer = new MutationObserver(() => {
    hasMutation = true;
    observer.disconnect();
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    characterData: true,
  });

  setTimeout(() => {
    observer.disconnect();
    if (!hasMutation) {
      onEvent({
        event_type: "dead_click",
        component_path: componentPath,
        payload: { target_tag: target.tagName.toLowerCase() },
      });
    }
  }, DEAD_CLICK_WAIT_MS);
}

// --- Scroll reversal detection ---

interface ScrollState {
  lastScrollY: number;
  lastDirection: "up" | "down" | null;
  lastDirectionChangeAt: number;
}

function createScrollState(): ScrollState {
  return { lastScrollY: 0, lastDirection: null, lastDirectionChangeAt: 0 };
}

function detectScrollReversal(
  state: ScrollState,
  scrollY: number,
  now: number,
): "up" | "down" | null {
  const direction: "up" | "down" = scrollY > state.lastScrollY ? "down" : "up";
  const previousDirection = state.lastDirection;

  state.lastScrollY = scrollY;

  if (previousDirection === null) {
    state.lastDirection = direction;
    return null;
  }

  if (direction !== previousDirection) {
    const timeSinceLastChange = now - state.lastDirectionChangeAt;
    state.lastDirection = direction;
    state.lastDirectionChangeAt = now;

    if (timeSinceLastChange < SCROLL_REVERSAL_WINDOW_MS) {
      return direction;
    }
  }

  return null;
}
