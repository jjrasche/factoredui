import { createContext, useContext, useMemo, type ReactNode } from "react";

/**
 * Context-based component path system — the Auxi contract layer.
 * Both web and React Native use these same providers to build
 * hierarchical paths like "onboarding/review/photo-grid/upload-button".
 *
 * On web, components also render data-auxi-* attributes for backward
 * compatibility with DOM-based capture. On RN, children render directly.
 */

const PathContext = createContext<string>("");

export function useComponentPath(): string {
  return useContext(PathContext);
}

// --- Path tier providers ---

interface PathTierProps {
  readonly name: string;
  readonly children: ReactNode;
}

function createPathProvider(tier: string) {
  const dataAttribute = `data-auxi-${tier}`;

  function PathProvider({ name, children }: PathTierProps): ReactNode {
    const parentPath = useContext(PathContext);
    const path = useMemo(
      () => (parentPath ? `${parentPath}/${name}` : name),
      [parentPath, name],
    );

    const isWeb = typeof document !== "undefined";

    if (isWeb) {
      const attrs = { [dataAttribute]: name };
      return (
        <PathContext.Provider value={path}>
          <div {...attrs} style={{ display: "contents" }}>
            {children}
          </div>
        </PathContext.Provider>
      );
    }

    return (
      <PathContext.Provider value={path}>
        {children}
      </PathContext.Provider>
    );
  }

  PathProvider.displayName = `Auxi${tier.charAt(0).toUpperCase() + tier.slice(1)}`;
  return PathProvider;
}

export const AuxiFlow = createPathProvider("flow");
export const AuxiPage = createPathProvider("page");
export const AuxiComponent = createPathProvider("component");
export const AuxiElement = createPathProvider("element");
