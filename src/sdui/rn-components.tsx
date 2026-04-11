import { type ReactNode } from "react";
import {
  View,
  Text,
  TextInput,
  Pressable,
  ScrollView,
  Image,
  FlatList,
  Switch,
  Modal,
  StyleSheet,
} from "react-native";
import type { ComponentRegistry, ComponentRenderer } from "./spec-types.js";

/**
 * Theme tokens for the default component registry.
 * Apps pass their own tokens to createComponentRegistry().
 */
export interface ThemeTokens {
  colors: {
    background: string;
    foreground: string;
    card: string;
    primary: string;
    primaryForeground: string;
    secondary: string;
    secondaryForeground: string;
    mutedForeground: string;
    destructive: string;
    destructiveForeground: string;
    border: string;
    input: string;
  };
  spacing: { xs: number; sm: number; md: number; lg: number };
  fontSize: { xs: number; sm: number; base: number; lg: number };
  radius: { sm: number; md: number; lg: number; full: number };
}

/**
 * Creates a ComponentRegistry for all 20 SDUI primitives.
 * Themed via token injection — no hardcoded colors or sizes.
 * Works on iOS, Android, and web (via react-native-web / Expo).
 */
export function createComponentRegistry(theme: ThemeTokens): ComponentRegistry {
  const { colors, spacing, fontSize, radius } = theme;
  const styles = buildStyles(colors, spacing, fontSize, radius);
  const variantStyles = buildVariantStyles(fontSize, colors);
  const buttonVariants = buildButtonVariants(colors);

  const column: ComponentRenderer = (props, children) => (
    <View style={buildLayoutStyle(props, "column")} key={props.key as string}>{children}</View>
  );

  const row: ComponentRenderer = (props, children) => (
    <View style={buildLayoutStyle(props, "row")} key={props.key as string}>{children}</View>
  );

  const stack: ComponentRenderer = (props, children) => (
    <View style={styles.stack} key={props.key as string}>{children}</View>
  );

  const scrollview: ComponentRenderer = (props, children) => (
    <ScrollView horizontal={props.horizontal as boolean} style={styles.scrollView} key={props.key as string}>
      {children}
    </ScrollView>
  );

  const grid: ComponentRenderer = (props, children) => {
    const columns = (props.columns as number) ?? 2;
    const gap = (props.gap as number) ?? spacing.sm;
    const cellBasis = `${100 / columns}%` as `${number}%`;
    return (
      <View style={[styles.grid, { gap }]} key={props.key as string}>
        {Array.isArray(children)
          ? children.map((child, i) => (
              <View key={i} style={{ flexBasis: cellBasis, flexGrow: 0, flexShrink: 0 }}>{child}</View>
            ))
          : children}
      </View>
    );
  };

  const text: ComponentRenderer = (props) => {
    const variant = props.variant as string | undefined;
    const textStyle = variant ? variantStyles[variant] ?? styles.bodyText : styles.bodyText;
    const alignStyle = props.align ? { textAlign: props.align as "left" | "center" | "right" } : undefined;
    const colorStyle = props.color ? { color: props.color as string } : undefined;
    const boldStyle = props.bold ? { fontWeight: "700" as const } : undefined;
    return (
      <Text
        style={[textStyle, alignStyle, colorStyle, boldStyle]}
        numberOfLines={props.numberOfLines as number | undefined}
        key={props.key as string}
      >
        {props.value as string}
      </Text>
    );
  };

  const image: ComponentRenderer = (props) => (
    <Image
      source={{ uri: props.source as string }}
      accessibilityLabel={props.alt as string}
      resizeMode={(props.fit as "cover" | "contain" | "stretch") ?? "cover"}
      style={[styles.image, props.aspectRatio ? { aspectRatio: props.aspectRatio as number } : undefined]}
      key={props.key as string}
    />
  );

  const icon: ComponentRenderer = (props) => (
    <Text
      style={{ fontSize: (props.size as number) ?? 24, color: (props.color as string) ?? colors.foreground }}
      key={props.key as string}
    >
      {props.name as string}
    </Text>
  );

  const divider: ComponentRenderer = (props) => (
    <View
      style={props.orientation === "vertical" ? styles.dividerVertical : styles.dividerHorizontal}
      key={props.key as string}
    />
  );

  const spacer: ComponentRenderer = (props) => {
    const size = props.size as number | undefined;
    const flex = props.flex as number | undefined;
    return (
      <View
        style={[size ? { height: size, width: size } : undefined, flex ? { flex } : undefined]}
        key={props.key as string}
      />
    );
  };

  const textinput: ComponentRenderer = (props) => (
    <TextInput
      placeholder={props.placeholder as string | undefined}
      multiline={props.multiline as boolean | undefined}
      maxLength={props.maxLength as number | undefined}
      onSubmitEditing={props.onAction as (() => void) | undefined}
      style={styles.textInput}
      placeholderTextColor={colors.mutedForeground}
      key={props.key as string}
    />
  );

  const button: ComponentRenderer = (props) => {
    const variant = (props.variant as string) ?? "primary";
    const bv = buttonVariants[variant] ?? buttonVariants.primary;
    return (
      <Pressable
        onPress={props.onAction as (() => void) | undefined}
        disabled={Boolean(props.disabled)}
        style={[styles.buttonBase, bv.container, Boolean(props.disabled) && styles.disabled]}
        key={props.key as string}
      >
        <Text style={[styles.buttonText, bv.text]}>{props.label as string}</Text>
      </Pressable>
    );
  };

  const toggle: ComponentRenderer = (props) => (
    <View style={styles.toggleRow} key={props.key as string}>
      {props.label != null && <Text style={styles.bodyText}>{String(props.label)}</Text>}
      <Switch value={Boolean(props.value)} onValueChange={props.onAction as ((v: boolean) => void) | undefined} />
    </View>
  );

  const select: ComponentRenderer = (props) => {
    const options = (props.options as Array<{ label: string; value: string }>) ?? [];
    const selectedLabel = options.find((o) => o.value === props.selectedValue)?.label
      ?? (props.placeholder as string) ?? "Select...";
    return (
      <Pressable style={styles.selectTrigger} onPress={props.onAction as (() => void) | undefined} key={props.key as string}>
        <Text style={styles.selectText}>{selectedLabel}</Text>
        <Text style={styles.selectChevron}>▾</Text>
      </Pressable>
    );
  };

  const slider: ComponentRenderer = (props) => (
    <View style={styles.sliderPlaceholder} key={props.key as string}>
      <Text style={styles.captionText}>
        Slider: {(props.min as number) ?? 0}–{(props.max as number) ?? 100}
      </Text>
    </View>
  );

  const card: ComponentRenderer = (props, children) => {
    const v = (props.variant as string) ?? "outlined";
    const cardStyle = v === "elevated" ? styles.cardElevated : v === "filled" ? styles.cardFilled : styles.cardOutlined;
    return <View style={[styles.cardBase, cardStyle]} key={props.key as string}>{children}</View>;
  };

  const list: ComponentRenderer = (props) => {
    const data = (props.data as unknown[]) ?? [];
    const renderItem = props.renderItem as ((item: unknown, index: number) => ReactNode) | undefined;
    if (!renderItem) {
      return (
        <View key={props.key as string}>
          <Text style={styles.captionText}>{(props.emptyText as string) ?? "No items"}</Text>
        </View>
      );
    }
    return (
      <FlatList
        data={data}
        renderItem={({ item, index }: { item: unknown; index: number }) => <>{renderItem(item, index)}</>}
        keyExtractor={(_item: unknown, index: number) => String(index)}
        key={props.key as string}
      />
    );
  };

  const tabs: ComponentRenderer = (props, children) => {
    const items = (props.items as string[]) ?? [];
    const selectedIndex = (props.selectedIndex as number) ?? 0;
    return (
      <View key={props.key as string}>
        <ScrollView horizontal style={styles.tabBar}>
          {items.map((label, i) => (
            <Pressable key={label} style={[styles.tab, i === selectedIndex && styles.tabSelected]}>
              <Text style={[styles.tabText, i === selectedIndex && styles.tabTextSelected]}>{label}</Text>
            </Pressable>
          ))}
        </ScrollView>
        {children}
      </View>
    );
  };

  const modal: ComponentRenderer = (props, children) => (
    <Modal transparent animationType="fade" key={props.key as string}>
      <View style={styles.modalOverlay}>
        <View style={styles.modalContent}>
          {props.title != null && <Text style={styles.headingText}>{String(props.title)}</Text>}
          {children}
        </View>
      </View>
    </Modal>
  );

  const chip: ComponentRenderer = (props) => {
    const selected = Boolean(props.selected);
    return (
      <Pressable
        style={[styles.chip, selected && styles.chipSelected]}
        onPress={props.onAction as (() => void) | undefined}
        key={props.key as string}
      >
        <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
          {props.label as string}
        </Text>
      </Pressable>
    );
  };

  return {
    column, row, stack, scrollview, grid,
    text, image, icon, divider, spacer,
    textinput, button, toggle, select, slider,
    card, list, tabs, modal, chip,
  };
}

// --- Style builders ---

function buildLayoutStyle(
  props: Record<string, unknown>,
  direction: "column" | "row",
) {
  const alignMap: Record<string, "flex-start" | "center" | "flex-end" | "stretch"> = {
    start: "flex-start", center: "center", end: "flex-end", stretch: "stretch",
  };
  const justifyMap: Record<string, "flex-start" | "center" | "flex-end" | "space-between" | "space-around"> = {
    start: "flex-start", center: "center", end: "flex-end", between: "space-between", around: "space-around",
  };

  return {
    flexDirection: direction,
    gap: (props.gap as number) ?? 0,
    padding: typeof props.padding === "number" ? props.padding : undefined,
    alignItems: props.align ? alignMap[props.align as string] : undefined,
    justifyContent: props.justify ? justifyMap[props.justify as string] : undefined,
    flex: props.flex as number | undefined,
  };
}

function buildStyles(
  colors: ThemeTokens["colors"],
  spacing: ThemeTokens["spacing"],
  fontSize: ThemeTokens["fontSize"],
  radius: ThemeTokens["radius"],
) {
  return StyleSheet.create({
    stack: { position: "relative" },
    scrollView: { flex: 1 },
    grid: { flexDirection: "row", flexWrap: "wrap" },
    bodyText: { fontSize: fontSize.sm, color: colors.foreground },
    headingText: { fontSize: fontSize.lg, fontWeight: "700", color: colors.foreground },
    captionText: { fontSize: fontSize.xs, color: colors.mutedForeground },
    image: { width: "100%" },
    dividerHorizontal: { height: 1, backgroundColor: colors.border },
    dividerVertical: { width: 1, height: "100%", backgroundColor: colors.border },
    textInput: {
      height: 36,
      borderRadius: radius.md,
      borderWidth: 1,
      borderColor: colors.input,
      backgroundColor: colors.background,
      paddingHorizontal: spacing.sm + 4,
      fontSize: fontSize.sm,
      color: colors.foreground,
    },
    buttonBase: {
      height: 40,
      paddingHorizontal: spacing.md,
      borderRadius: radius.md,
      alignItems: "center",
      justifyContent: "center",
    },
    buttonText: { fontSize: fontSize.sm, fontWeight: "500" },
    disabled: { opacity: 0.5 },
    toggleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
    selectTrigger: {
      height: 36,
      borderRadius: radius.md,
      borderWidth: 1,
      borderColor: colors.input,
      backgroundColor: colors.background,
      paddingHorizontal: spacing.sm + 4,
      flexDirection: "row",
      alignItems: "center",
      justifyContent: "space-between",
    },
    selectText: { fontSize: fontSize.sm, color: colors.foreground },
    selectChevron: { fontSize: fontSize.xs, color: colors.mutedForeground },
    sliderPlaceholder: { padding: spacing.sm },
    cardBase: { borderRadius: radius.lg, padding: spacing.md },
    cardOutlined: { borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card },
    cardElevated: {
      backgroundColor: colors.card,
      shadowColor: "#000",
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.1,
      shadowRadius: 4,
      elevation: 3,
    },
    cardFilled: { backgroundColor: colors.secondary },
    tabBar: { flexDirection: "row", borderBottomWidth: 1, borderBottomColor: colors.border },
    tab: { paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
    tabSelected: { borderBottomWidth: 2, borderBottomColor: colors.primary },
    tabText: { fontSize: fontSize.sm, color: colors.mutedForeground },
    tabTextSelected: { color: colors.foreground, fontWeight: "600" },
    modalOverlay: {
      flex: 1,
      justifyContent: "center",
      alignItems: "center",
      backgroundColor: "rgba(0,0,0,0.3)",
    },
    modalContent: {
      backgroundColor: colors.background,
      borderRadius: radius.lg,
      padding: spacing.lg,
      width: "85%",
      maxHeight: "80%",
    },
    chip: {
      paddingHorizontal: spacing.sm + 4,
      paddingVertical: spacing.xs,
      borderRadius: radius.full,
      borderWidth: 1,
      borderColor: colors.border,
      backgroundColor: colors.background,
    },
    chipSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
    chipText: { fontSize: fontSize.xs, color: colors.foreground },
    chipTextSelected: { color: colors.primaryForeground },
  });
}

function buildVariantStyles(
  fontSize: ThemeTokens["fontSize"],
  colors: ThemeTokens["colors"],
): Record<string, object> {
  return {
    heading: { fontSize: fontSize.lg, fontWeight: "700", color: colors.foreground },
    subheading: { fontSize: fontSize.base, fontWeight: "600", color: colors.foreground },
    body: { fontSize: fontSize.sm, color: colors.foreground },
    caption: { fontSize: fontSize.xs, color: colors.mutedForeground },
    label: { fontSize: fontSize.xs, fontWeight: "600", color: colors.foreground },
  };
}

function buildButtonVariants(
  colors: ThemeTokens["colors"],
): Record<string, { container: object; text: object }> {
  return {
    primary: {
      container: { backgroundColor: colors.primary },
      text: { color: colors.primaryForeground },
    },
    secondary: {
      container: { backgroundColor: colors.secondary },
      text: { color: colors.secondaryForeground },
    },
    outline: {
      container: { backgroundColor: "transparent", borderWidth: 1, borderColor: colors.input },
      text: { color: colors.foreground },
    },
    ghost: {
      container: { backgroundColor: "transparent" },
      text: { color: colors.foreground },
    },
    destructive: {
      container: { backgroundColor: colors.destructive },
      text: { color: colors.destructiveForeground },
    },
  };
}
