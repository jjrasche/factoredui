import type { ObserveEvent } from "../types.js";
import { resolveComponentPath } from "./path.js";

/**
 * Attaches DOM event listeners and maps browser events to ObserveEvents.
 * Framework-agnostic: uses raw addEventListener on document/window.
 */

export interface EventListenerHandle {
  startListening: () => void;
  stopListening: () => void;
}

export function createEventListener(
  onEvent: (event: ObserveEvent) => void,
): EventListenerHandle {
  const handlers = new Map<string, EventListener>();

  function registerHandler(
    eventName: string,
    target: EventTarget,
    mapToObserveEvent: (event: Event) => ObserveEvent | null,
    options?: AddEventListenerOptions,
  ): void {
    const handler = (event: Event) => {
      const mapped = mapToObserveEvent(event);
      if (mapped) onEvent(mapped);
    };
    handlers.set(eventName, handler);
    target.addEventListener(eventName, handler, options);
  }

  function startListening(): void {
    registerHandler("click", document, mapClickEvent, { capture: true });
    registerHandler("scroll", window, mapScrollEvent, { passive: true });
    registerHandler("input", document, mapInputEvent, { capture: true });
    registerHandler("focus", document, mapFocusEvent, { capture: true });
    registerHandler("blur", document, mapBlurEvent, { capture: true });
    registerHandler("submit", document, mapSubmitEvent, { capture: true });
    registerHandler("resize", window, mapResizeEvent, { passive: true });
    registerHandler("error", window, mapErrorEvent);
    registerHandler(
      "visibilitychange",
      document,
      mapVisibilityEvent,
    );
  }

  function stopListening(): void {
    removeHandler("click", document, { capture: true });
    removeHandler("scroll", window);
    removeHandler("input", document, { capture: true });
    removeHandler("focus", document, { capture: true });
    removeHandler("blur", document, { capture: true });
    removeHandler("submit", document, { capture: true });
    removeHandler("resize", window);
    removeHandler("error", window);
    removeHandler("visibilitychange", document);
  }

  function removeHandler(
    eventName: string,
    target: EventTarget,
    options?: EventListenerOptions,
  ): void {
    const handler = handlers.get(eventName);
    if (handler) {
      target.removeEventListener(eventName, handler, options);
      handlers.delete(eventName);
    }
  }

  return { startListening, stopListening };
}

// --- Event mappers (leaf functions) ---

function mapClickEvent(event: Event): ObserveEvent | null {
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

function mapScrollEvent(): ObserveEvent {
  return {
    event_type: "scroll",
    component_path: resolveComponentPath(document.documentElement),
    payload: { scroll_x: window.scrollX, scroll_y: window.scrollY },
  };
}

function mapInputEvent(event: Event): ObserveEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;
  return {
    event_type: "input",
    component_path: resolveComponentPath(target),
    payload: { target_tag: target.tagName.toLowerCase() },
  };
}

function mapFocusEvent(event: Event): ObserveEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;
  return {
    event_type: "focus",
    component_path: resolveComponentPath(target),
    payload: { target_tag: target.tagName.toLowerCase() },
  };
}

function mapBlurEvent(event: Event): ObserveEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;
  return {
    event_type: "blur",
    component_path: resolveComponentPath(target),
    payload: { target_tag: target.tagName.toLowerCase() },
  };
}

function mapSubmitEvent(event: Event): ObserveEvent | null {
  const target = event.target as Element | null;
  if (!target) return null;
  return {
    event_type: "submit",
    component_path: resolveComponentPath(target),
    payload: { form_id: (target as HTMLFormElement).id || null },
  };
}

function mapResizeEvent(): ObserveEvent {
  return {
    event_type: "resize",
    component_path: resolveComponentPath(document.documentElement),
    payload: { width: window.innerWidth, height: window.innerHeight },
  };
}

function mapErrorEvent(event: Event): ObserveEvent | null {
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

function mapVisibilityEvent(): ObserveEvent {
  return {
    event_type: "visibility",
    component_path: resolveComponentPath(document.documentElement),
    payload: { visibility_state: document.visibilityState },
  };
}
