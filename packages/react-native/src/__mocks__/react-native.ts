/**
 * Minimal react-native mock for vitest unit tests.
 * Only stubs APIs used by the capture adapter and component registry.
 */

type AppStateListener = (state: string) => void;

const appStateListeners: AppStateListener[] = [];

export const AppState = {
  addEventListener(event: string, handler: AppStateListener) {
    if (event === "change") appStateListeners.push(handler);
    return {
      remove() {
        const idx = appStateListeners.indexOf(handler);
        if (idx >= 0) appStateListeners.splice(idx, 1);
      },
    };
  },
  /** Test helper: simulate state change */
  _fireChange(state: string) {
    appStateListeners.forEach(fn => fn(state));
  },
};

export const Platform = {
  OS: "ios" as string,
  Version: "18.0" as string | number,
};

export const Dimensions = {
  get(type: string) {
    if (type === "window") return { width: 375, height: 812, scale: 2, fontScale: 1 };
    return { width: 375, height: 812, scale: 2, fontScale: 1 };
  },
};

// ErrorUtils global (RN global error handler)
type ErrorHandler = (error: Error, isFatal?: boolean) => void;
let globalErrorHandler: ErrorHandler = () => {};

export const ErrorUtils = {
  getGlobalHandler(): ErrorHandler {
    return globalErrorHandler;
  },
  setGlobalHandler(handler: ErrorHandler): void {
    globalErrorHandler = handler;
  },
  /** Test helper: simulate an unhandled JS error */
  _simulateError(error: Error, isFatal?: boolean): void {
    globalErrorHandler(error, isFatal);
  },
  /** Test helper: reset to default */
  _reset(): void {
    globalErrorHandler = () => {};
  },
};

// Make ErrorUtils available as a global (RN runtime provides it on globalThis)
(globalThis as Record<string, unknown>).ErrorUtils = ErrorUtils;

// Stubs for rn-components.tsx imports
function createStubComponent(_name: string) {
  return function StubComponent() { return null; };
}

export const View = createStubComponent("View");
export const Text = createStubComponent("Text");
export const TextInput = createStubComponent("TextInput");
export const Pressable = createStubComponent("Pressable");
export const ScrollView = createStubComponent("ScrollView");
export const Image = createStubComponent("Image");
export const FlatList = createStubComponent("FlatList");
export const Switch = createStubComponent("Switch");
export const Modal = createStubComponent("Modal");
export const StyleSheet = {
  create<T extends Record<string, unknown>>(styles: T): T { return styles; },
};
