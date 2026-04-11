export { renderSpec, type RenderContext } from "./renderer.js";
export { validateSpec } from "./spec-validator.js";
export { resolveAllSources, type DataSourceCache, type ResolvedSources } from "./data-source.js";
export { dispatchAction } from "./action-dispatch.js";
export { loadSpec, type SpecStorage, type SignatureVerifier, type LoadedSpec } from "./spec-loader.js";
export {
  resolveBinding,
  resolveTextWithBindings,
  resolveProps,
  isBindingRef,
} from "./binding.js";

// Default implementations — generic, no app-specific logic
export { useSourceData, type SourceDataState } from "./use-source-data.js";
export {
  createSpecStorage,
  createDataSourceCache,
  devSignatureVerifier,
  type KVStorage,
} from "./default-storage.js";
export {
  RENDERER_VERSION,
  type AuxiSpec,
  type SpecNode,
  type SpecNodeType,
  type SpecValue,
  type ActionRef,
  type SignedSpec,
  type DataSourceConfig,
  type DataSourceRegistry,
  type ActionHandler,
  type ActionRegistry,
  type ComponentRenderer,
  type ComponentRegistry,
  type ListProps,
  type LayoutProps,
  type TextProps,
  type ButtonProps,
  type TextInputProps,
  type ImageProps,
  type IconProps,
  type CardProps,
  type TabsProps,
  type GridProps,
  type SelectProps,
  type ChipProps,
  type ModalProps,
  type ScrollViewProps,
  type ToggleProps,
  type SliderProps,
  type DividerProps,
  type SpacerProps,
} from "./spec-types.js";
