import type { AuxiEvent } from "../types.js";
import type { CaptureAdapter } from "./adapter.js";
import { resolveComponentPath } from "./path.js";
import {
  recordClickAndDetectRage,
  createRageClickState,
  detectScrollReversal,
  createScrollState,
  type RageClickState,
  type ScrollState,
} from "./behavioral.js";

const THROTTLE_INTERVAL_MS = 100;
const DEAD_CLICK_WAIT_MS = 1000;
const SESSION_STORAGE_KEY = "auxi:session_id";

/**
 * Web (DOM) implementation of CaptureAdapter.
 * Attaches document/window listeners for clicks, scroll, input, errors, etc.
 * Uses sessionStorage for session persistence and navigator/window for metadata.
 */
export function createWebAdapter(): CaptureAdapter {
  const handlers = new Map<string, EventListener>();
  const rageClickState = createRageClickState();
  const scrollState = createScrollState();
  let emitEvent: ((event: AuxiEvent) => void) | null = null;

  function emit(event: AuxiEvent): void {
    emitEvent?.(event);
  }

  function addHandler(
    key: string,
    target: EventTarget,
    mapFn: (event: Event) => AuxiEvent | null,
    options?: AddEventListenerOptions,
  ): void {
    const eventName = key.split(":")[0];
    const handler = (event: Event) => {
      const mapped = mapFn(event);
      if (mapped) emit(mapped);
    };
    handlers.set(key, handler);
    target.addEventListener(eventName, handler, options);
  }

  function addThrottledHandler(
    key: string,
    target: EventTarget,
    mapFn: (event: Event) => AuxiEvent | null,
    options?: AddEventListenerOptions,
  ): void {
    const eventName = key.split(":")[0];
    let lastFiredAt = 0;
    const handler = (event: Event) => {
      const now = Date.now();
      if (now - lastFiredAt < THROTTLE_INTERVAL_MS) return;
      lastFiredAt = now;
      const mapped = mapFn(event);
      if (mapped) emit(mapped);
    };
    handlers.set(key, handler);
    target.addEventListener(eventName, handler, options);
  }

  function dropHandler(
    key: string,
    target: EventTarget,
    options?: EventListenerOptions,
  ): void {
    const eventName = key.split(":")[0];
    const handler = handlers.get(key);
    if (handler) {
      target.removeEventListener(eventName, handler, options);
      handlers.delete(key);
    }
  }

  function startListening(onEvent: (event: AuxiEvent) => void): void {
    emitEvent = onEvent;

    addHandler("click", document, mapClickEvent, { capture: true });
    addHandler("click:rage", document, (e) => mapRageClick(e, rageClickState), { capture: true });
    addHandler("click:dead", document, (e) => { detectDeadClick(e, emit); return null; }, { capture: true });
    addThrottledHandler("scroll", window, mapScrollEvent);
    addHandler("scroll:reversal", window, () => mapScrollReversal(scrollState));
    addThrottledHandler("input", document, (e) => mapTargetEvent(e, "input"), { capture: true });
    addHandler("focus", document, (e) => mapTargetEvent(e, "focus"), { capture: true });
    addHandler("blur", document, (e) => mapTargetEvent(e, "blur"), { capture: true });
    addHandler("submit", document, mapSubmitEvent, { capture: true });
    addThrottledHandler("resize", window, mapResizeEvent);
    addHandler("error", window, mapErrorEvent);
    addHandler("visibilitychange", document, mapVisibilityEvent);
  }

  function stopListening(): void {
    dropHandler("click", document, { capture: true });
    dropHandler("click:rage", document, { capture: true });
    dropHandler("click:dead", document, { capture: true });
    dropHandler("scroll", window);
    dropHandler("scroll:reversal", window);
    dropHandler("input", document, { capture: true });
    dropHandler("focus", document, { capture: true });
    dropHandler("blur", document, { capture: true });
    dropHandler("submit", document, { capture: true });
    dropHandler("resize", window);
    dropHandler("error", window);
    dropHandler("visibilitychange", document);
    emitEvent = null;
  }

  function registerUnloadHandler(onUnload: () => void): void {
    if (typeof window === "undefined") return;
    window.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") onUnload();
    });
  }

  return {
    startListening,
    stopListening,
    collectSessionMetadata,
    storeSessionId,
    loadSessionId,
    clearSessionId,
    registerUnloadHandler,
  };
}

// --- Session storage (leaf functions, no closure state needed) ---

function collectSessionMetadata(): Record<string, unknown> {
  if (typeof window === "undefined") return {};
  return {
    user_agent: navigator.userAgent,
    screen_width: screen.width,
    screen_height: screen.height,
    viewport_width: window.innerWidth,
    viewport_height: window.innerHeight,
    language: navigator.language,
    referrer: document.referrer || null,
    url: window.location.href,
  };
}

function storeSessionId(id: string): void {
  try {
    sessionStorage.setItem(SESSION_STORAGE_KEY, id);
  } catch {
    // SSR or restricted storage -- session lives in memory only
  }
}

function loadSessionId(): string | null {
  try {
    return sessionStorage.getItem(SESSION_STORAGE_KEY);
  } catch {
    return null;
  }
}

function clearSessionId(): void {
  try {
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // Ignore storage errors
  }
}

// --- Event mappers (pure leaf functions) ---

function mapClickEvent(event: Event): AuxiEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;
  return {
    event_type: "click",
    component_path: resolveComponentPath(target),
    payload: {
      x: (event as MouseEvent).clientX,
      y: (event as MouseEvent).clientY,
      target_tag: target.tagName.toLowerCase(),
    },
  };
}

function mapScrollEvent(): AuxiEvent {
  return {
    event_type: "scroll",
    component_path: resolveComponentPath(document.documentElement),
    payload: { scroll_x: window.scrollX, scroll_y: window.scrollY },
  };
}

function mapTargetEvent(event: Event, eventType: "input" | "focus" | "blur"): AuxiEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;
  return {
    event_type: eventType,
    component_path: resolveComponentPath(target),
    payload: { target_tag: target.tagName.toLowerCase() },
  };
}

function mapSubmitEvent(event: Event): AuxiEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;
  return {
    event_type: "submit",
    component_path: resolveComponentPath(target),
    payload: { form_id: (target as HTMLFormElement).id || null },
  };
}

function mapResizeEvent(): AuxiEvent {
  return {
    event_type: "resize",
    component_path: resolveComponentPath(document.documentElement),
    payload: { width: window.innerWidth, height: window.innerHeight },
  };
}

function mapErrorEvent(event: Event): AuxiEvent {
  const errorEvent = event as ErrorEvent;
  return {
    event_type: "error",
    component_path: resolveComponentPath(document.documentElement),
    payload: {
      message: errorEvent.message,
      filename: errorEvent.filename,
      lineno: errorEvent.lineno,
      colno: errorEvent.colno,
    },
  };
}

function mapVisibilityEvent(): AuxiEvent {
  return {
    event_type: "visibility",
    component_path: resolveComponentPath(document.documentElement),
    payload: { visibility_state: document.visibilityState },
  };
}

// --- Behavioral mappers (delegate to pure logic in behavioral.ts) ---

function mapRageClick(event: Event, state: RageClickState): AuxiEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;

  const componentPath = resolveComponentPath(target);
  const isRage = recordClickAndDetectRage(state, componentPath, Date.now());

  if (isRage) {
    return {
      event_type: "rage_click",
      component_path: componentPath,
      payload: {
        click_count: state.count,
        x: (event as MouseEvent).clientX,
        y: (event as MouseEvent).clientY,
      },
    };
  }
  return null;
}

function detectDeadClick(
  event: Event,
  emit: (e: AuxiEvent) => void,
): void {
  const target = event.target as Element | null;
  if (!target) return;

  const componentPath = resolveComponentPath(target);
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
      emit({
        event_type: "dead_click",
        component_path: componentPath,
        payload: { target_tag: target.tagName.toLowerCase() },
      });
    }
  }, DEAD_CLICK_WAIT_MS);
}

function mapScrollReversal(state: ScrollState): AuxiEvent | null {
  const scrollY = window.scrollY;
  const reversal = detectScrollReversal(state, scrollY, Date.now());

  if (reversal) {
    return {
      event_type: "scroll_reversal",
      component_path: resolveComponentPath(document.documentElement),
      payload: { scroll_y: scrollY, direction: reversal },
    };
  }
  return null;
}
