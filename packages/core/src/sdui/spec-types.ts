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
  | "chip"
  | "forcegraph";

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
 * A spec value is either a literal, a binding reference, or a nested SpecNode.
 * Binding refs are strings starting with "{" and ending with "}".
 * Single-level path only: "{sources.pipeline.name}" — no expressions, no chaining.
 *
 * SpecNode is included to support props like ListProps.itemTemplate
 * where a prop value is itself a component subtree.
 */
export type SpecValue = string | number | boolean | null | SpecNode | SpecValueArray | SpecValueObject;
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

// --- ForceGraph props ---
// First "dense/semantic" primitive: a force-directed graph of typed
// nodes (with domain-grouping) connected by typed edges, with an
// optional live event stream overlaying firing highlights + moving
// particles. Used for signal-graph, memory-topology, and any view
// where the shape is "nodes + edges with physics."
//
// Unlike container primitives (column/row/list), this takes data
// URLs rather than child nodes. The renderer fetches + subscribes.

export interface ForceGraphProps {
  /**
   * URL to fetch the current topology from. Expected response shape:
   *   { nodes: [{ id, group?, label? }], edges: [{ from, to, kind? }] }
   */
  topology_url: string;

  /**
   * Optional SSE endpoint. Each event's `payload` drives overlays:
   *   - { type: "firing_started",   payload: { function } }  → pulse node
   *   - { type: "firing_completed", payload: { function } }  → fade pulse
   *   - { type: "signal_emitted",   payload: { producer, consumers[], kind } }
   *       → spawn a particle from producer to each consumer
   */
  event_stream_url?: string;

  /**
   * Optional history endpoint for replay/scrubbing. Returns prior events
   * so the user can DVR backwards through time. Expected response shape:
   *   { signals: [{ id, kind, payload, produced_by?, triggered_by[], created_at }, …] }
   * Each signal's `payload` mirrors a live SSE frame's payload (same
   * `type`, `function`, `producer`, `consumers`, `kind` fields). Without
   * this URL, the renderer hides the replay controls and runs live-only.
   */
  history_url?: string;

  physics?: {
    /** Node–node repulsion strength. Higher = more spread. Default 100. */
    repulsion?: number;
    /** Edge contraction strength. Higher = tighter graph. Default 0.05. */
    attraction?: number;
    /** Velocity damping per step. 0..1. Default 0.9. */
    damping?: number;
    /** Anchor nodes toward a center per domain/group. Default true. */
    domain_anchoring?: boolean;
  };
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

// --- Signing ---

export interface SignedSpec {
  spec: Spec;
  signature: string;
  signed_at: string;
  spec_hash: string;
}

// --- Renderer version ---

export const RENDERER_VERSION = 1;
