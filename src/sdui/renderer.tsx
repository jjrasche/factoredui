import { type ReactNode } from "react";
import { AuxiComponent, AuxiElement } from "../capture/path-context.js";
import type { SpecNode, ComponentRegistry, ActionRegistry } from "./spec-types.js";
import { resolveProps, resolveBinding } from "./binding.js";

/**
 * Recursive spec renderer.
 * Reads a spec node, resolves bindings, looks up the component, wraps in
 * Auxi path context for automatic event capture, and recurses into children.
 *
 * Orchestrator: coordinates binding resolution, component lookup, path wrapping.
 */

export interface RenderContext {
  components: ComponentRegistry;
  actions: ActionRegistry;
  data: Record<string, unknown>;
}

export function renderSpec(root: SpecNode, context: RenderContext): ReactNode {
  return renderNode(root, context);
}

function renderNode(node: SpecNode, context: RenderContext): ReactNode {
  if (!isNodeVisible(node, context.data)) return null;

  const Component = context.components[node.type];
  if (!Component) return null;

  const resolvedProps = resolveNodeProps(node, context);
  const children = renderChildren(node, context);

  return wrapWithAuxiPath(node, Component(resolvedProps, children));
}

function isNodeVisible(node: SpecNode, data: Record<string, unknown>): boolean {
  if (!node.visible) return true;
  const value = resolveBinding(node.visible, data);
  return Boolean(value);
}

function resolveNodeProps(
  node: SpecNode,
  context: RenderContext,
): Record<string, unknown> {
  const baseProps = node.props ? resolveProps(node.props, context.data) : {};

  if (node.action) {
    const handler = context.actions[node.action.action];
    if (handler) {
      const actionParams = node.action.params
        ? resolveProps(node.action.params as Record<string, unknown>, context.data)
        : {};
      baseProps.onAction = () => handler(actionParams);
    }
  }

  baseProps.key = node.id;
  return baseProps;
}

function renderChildren(node: SpecNode, context: RenderContext): ReactNode {
  if (!node.children || node.children.length === 0) return undefined;
  return node.children.map((child) => renderNode(child, context));
}

function wrapWithAuxiPath(node: SpecNode, rendered: ReactNode): ReactNode {
  const isContainer = isContainerType(node.type);

  if (isContainer) {
    return (
      <AuxiComponent name={node.id} key={node.id}>
        {rendered}
      </AuxiComponent>
    );
  }

  return (
    <AuxiElement name={node.id} key={node.id}>
      {rendered}
    </AuxiElement>
  );
}

function isContainerType(type: string): boolean {
  return type === "column" || type === "row" || type === "stack"
    || type === "scrollview" || type === "grid" || type === "card"
    || type === "list" || type === "tabs" || type === "modal";
}
