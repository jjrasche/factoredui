/**
 * SDUI Spec Types
 *
 * Declarative UI spec format. Pure data, no expressions.
 * Inspired by A2UI's flat structure, constrained for LLM generation.
 *
 * Rules:
 * - No expression language. Values are literals or single-level path refs "{path.to.value}".
 * - No state management in the spec. Host provides context.
 * - No data fetching. Spec declares source refs, host resolves them.
 * - No complex conditionals. Single boolean visibility via "{flag}" refs.
 * - Actions are named references, never inline code.
 */

// --- Spec envelope ---

export interface Spec {
  spec_version: number;
  renderer_min: number;
  root: SpecNode;
}

// --- Node types ---

export type SpecNodeType =
  | "column"
  | "row"
  | "stack"
  | "scrollview"
  | "grid"
  | "text"
  | "image"
  | "icon"
  | "divider"
  | "spacer"
  | "textinput"
  | "button"
  | "toggle"
  | "select"
  | "slider"
  | "card"
  | "list"
  | "tabs"
  | "modal"
  | "chip";

export interface SpecNode {
  id: string;
  type: SpecNodeType;
  props?: Record<string, SpecValue>;
  children?: SpecNode[];
  visible?: string;
  action?: ActionRef;
}

// --- Values ---

/**
 * A spec value is either a literal or a binding reference.
 * Binding refs are strings starting with "{" and ending with "}".
 * Single-level path only: "{sources.pipeline.name}" — no expressions, no chaining.
 */
export type SpecValue = string | number | boolean | null | SpecValueArray | SpecValueObject;
interface SpecValueArray extends Array<SpecValue> {}
interface SpecValueObject { [key: string]: SpecValue; }

// --- Actions ---

export interface ActionRef {
  action: string;
  params?: Record<string, SpecValue>;
}

// --- List-specific props ---

export interface ListProps {
  data: string;
  itemTemplate: SpecNode;
  emptyText?: string;
  maxItems?: number;
}

// --- Layout props ---

export interface LayoutProps {
  direction?: "column" | "row";
  gap?: number;
  padding?: number | [number, number, number, number];
  align?: "start" | "center" | "end" | "stretch";
  justify?: "start" | "center" | "end" | "between" | "around";
  flex?: number;
}

// --- Text props ---

export interface TextProps {
  value: string;
  variant?: "heading" | "subheading" | "body" | "caption" | "label";
  align?: "left" | "center" | "right";
  color?: string;
  bold?: boolean;
  numberOfLines?: number;
}

// --- Button props ---

export interface ButtonProps {
  label: string;
  variant?: "primary" | "secondary" | "outline" | "ghost" | "destructive";
  size?: "sm" | "md" | "lg";
  icon?: string;
  disabled?: string;
}

// --- TextInput props ---

export interface TextInputProps {
  placeholder?: string;
  multiline?: boolean;
  maxLength?: number;
  submitAction?: ActionRef;
}

// --- Image props ---

export interface ImageProps {
  source: string;
  alt: string;
  fit?: "cover" | "contain" | "fill";
  aspectRatio?: number;
}

// --- Icon props ---

export interface IconProps {
  name: string;
  size?: number;
  color?: string;
}

// --- Card props ---

export interface CardProps {
  variant?: "elevated" | "outlined" | "filled";
}

// --- Tabs props ---

export interface TabsProps {
  items: string[];
  selectedIndex?: number;
}

// --- Grid props ---

export interface GridProps {
  columns: number;
  gap?: number;
}

// --- Select props ---

export interface SelectProps {
  options: Array<{ label: string; value: string }>;
  placeholder?: string;
  selectedValue?: string;
}

// --- Chip props ---

export interface ChipProps {
  label: string;
  selected?: boolean;
  variant?: "filled" | "outlined";
}

// --- Modal props ---

export interface ModalProps {
  title?: string;
  dismissible?: boolean;
}

// --- ScrollView props ---

export interface ScrollViewProps {
  horizontal?: boolean;
}

// --- Toggle props ---

export interface ToggleProps {
  label?: string;
  value?: string;
}

// --- Slider props ---

export interface SliderProps {
  min?: number;
  max?: number;
  step?: number;
  value?: string;
}

// --- Divider props ---

export interface DividerProps {
  orientation?: "horizontal" | "vertical";
}

// --- Spacer props ---

export interface SpacerProps {
  size?: number;
  flex?: number;
}

// --- Data source contract (implemented by the host app) ---

export interface DataSourceConfig {
  fetch: () => Promise<unknown>;
  cache?: "none" | "local";
  realtime?: boolean;
  maxItems?: number;
}

export type DataSourceRegistry = Record<string, DataSourceConfig>;

// --- Action handler contract (implemented by the host app) ---

export type ActionHandler = (params: Record<string, unknown>) => void | Promise<void>;
export type ActionRegistry = Record<string, ActionHandler>;

// --- Component contract (implemented by the host app) ---

export type ComponentRenderer = (props: Record<string, unknown>, children?: React.ReactNode) => React.ReactNode;
export type ComponentRegistry = Record<SpecNodeType, ComponentRenderer>;

// --- Signing ---

export interface SignedSpec {
  spec: Spec;
  signature: string;
  signed_at: string;
  spec_hash: string;
}

// --- Renderer version ---

export const RENDERER_VERSION = 1;
