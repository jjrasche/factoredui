/**
 * Behavioral detection: pure logic for rage clicks and scroll reversals.
 * Platform-specific DOM bindings are in web-adapter.ts.
 * Dead click detection (MutationObserver) is web-only, lives in web-adapter.ts.
 */

const RAGE_CLICK_THRESHOLD = 3;
const RAGE_CLICK_WINDOW_MS = 500;
const SCROLL_REVERSAL_WINDOW_MS = 300;

// --- Rage click detection (pure logic) ---

export interface RageClickState {
  targetPath: string | null;
  timestamps: number[];
  count: number;
}

export function createRageClickState(): RageClickState {
  return { targetPath: null, timestamps: [], count: 0 };
}

export function recordClickAndDetectRage(
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

// --- Scroll reversal detection (pure logic) ---

export interface ScrollState {
  lastScrollY: number;
  lastDirection: "up" | "down" | null;
  lastDirectionChangeAt: number;
}

export function createScrollState(): ScrollState {
  return { lastScrollY: 0, lastDirection: null, lastDirectionChangeAt: 0 };
}

export function detectScrollReversal(
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
