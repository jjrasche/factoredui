package ai.factoredui.compose.schema

/**
 * Typed prop accessors for each SpecNodeType.
 *
 * The spec format stores all props as Map<String, SpecValue>. These helpers
 * extract typed values with safe defaults so the renderer doesn't need to cast.
 * Matches the prop interfaces in spec-types.ts.
 */

// --- Shared helpers ---

private fun Map<String, SpecValue>.string(key: String): String? =
    (get(key) as? SpecValue.StringValue)?.value

private fun Map<String, SpecValue>.stringOrDefault(key: String, default: String): String =
    string(key) ?: default

private fun Map<String, SpecValue>.int(key: String): Int? =
    (get(key) as? SpecValue.NumberValue)?.value?.toInt()

private fun Map<String, SpecValue>.double(key: String): Double? =
    (get(key) as? SpecValue.NumberValue)?.value

private fun Map<String, SpecValue>.boolean(key: String): Boolean? =
    (get(key) as? SpecValue.BooleanValue)?.value

// --- TextProps ---

data class TextProps(
    val value: String,
    val variant: TextVariant = TextVariant.BODY,
    val align: TextAlign = TextAlign.LEFT,
    val color: String? = null,
    val bold: Boolean = false,
    val numberOfLines: Int? = null,
)

enum class TextVariant { HEADING, SUBHEADING, BODY, CAPTION, LABEL }
enum class TextAlign { LEFT, CENTER, RIGHT }

fun Map<String, SpecValue>.asTextProps(): TextProps = TextProps(
    value = string("value") ?: "",
    variant = when (string("variant")) {
        "heading" -> TextVariant.HEADING
        "subheading" -> TextVariant.SUBHEADING
        "body" -> TextVariant.BODY
        "caption" -> TextVariant.CAPTION
        "label" -> TextVariant.LABEL
        else -> TextVariant.BODY
    },
    align = when (string("align")) {
        "center" -> TextAlign.CENTER
        "right" -> TextAlign.RIGHT
        else -> TextAlign.LEFT
    },
    color = string("color"),
    bold = boolean("bold") ?: false,
    numberOfLines = int("numberOfLines"),
)

// --- ButtonProps ---

data class ButtonProps(
    val label: String,
    val variant: ButtonVariant = ButtonVariant.PRIMARY,
    val size: ButtonSize = ButtonSize.MD,
    val icon: String? = null,
    val disabled: Boolean = false,
)

enum class ButtonVariant { PRIMARY, SECONDARY, OUTLINE, GHOST, DESTRUCTIVE }
enum class ButtonSize { SM, MD, LG }

fun Map<String, SpecValue>.asButtonProps(): ButtonProps = ButtonProps(
    label = string("label") ?: "",
    variant = when (string("variant")) {
        "secondary" -> ButtonVariant.SECONDARY
        "outline" -> ButtonVariant.OUTLINE
        "ghost" -> ButtonVariant.GHOST
        "destructive" -> ButtonVariant.DESTRUCTIVE
        else -> ButtonVariant.PRIMARY
    },
    size = when (string("size")) {
        "sm" -> ButtonSize.SM
        "lg" -> ButtonSize.LG
        else -> ButtonSize.MD
    },
    icon = string("icon"),
    disabled = boolean("disabled") ?: false,
)

// --- LayoutProps ---

data class LayoutProps(
    val gap: Int = 0,
    val padding: Int = 0,
    val align: LayoutAlign = LayoutAlign.START,
    val justify: LayoutJustify = LayoutJustify.START,
    val flex: Float = 0f,
)

enum class LayoutAlign { START, CENTER, END, STRETCH }
enum class LayoutJustify { START, CENTER, END, BETWEEN, AROUND }

fun Map<String, SpecValue>.asLayoutProps(): LayoutProps = LayoutProps(
    gap = int("gap") ?: 0,
    padding = int("padding") ?: 0,
    align = when (string("align")) {
        "center" -> LayoutAlign.CENTER
        "end" -> LayoutAlign.END
        "stretch" -> LayoutAlign.STRETCH
        else -> LayoutAlign.START
    },
    justify = when (string("justify")) {
        "center" -> LayoutJustify.CENTER
        "end" -> LayoutJustify.END
        "between" -> LayoutJustify.BETWEEN
        "around" -> LayoutJustify.AROUND
        else -> LayoutJustify.START
    },
    flex = double("flex")?.toFloat() ?: 0f,
)

// --- ImageProps ---

data class ImageProps(
    val source: String,
    val alt: String = "",
    val fit: ImageFit = ImageFit.COVER,
    val aspectRatio: Float? = null,
)

enum class ImageFit { COVER, CONTAIN, FILL }

fun Map<String, SpecValue>.asImageProps(): ImageProps = ImageProps(
    source = string("source") ?: "",
    alt = string("alt") ?: "",
    fit = when (string("fit")) {
        "contain" -> ImageFit.CONTAIN
        "fill" -> ImageFit.FILL
        else -> ImageFit.COVER
    },
    aspectRatio = double("aspectRatio")?.toFloat(),
)

// --- IconProps ---

data class IconProps(
    val name: String,
    val size: Int = 24,
    val color: String? = null,
)

fun Map<String, SpecValue>.asIconProps(): IconProps = IconProps(
    name = string("name") ?: "",
    size = int("size") ?: 24,
    color = string("color"),
)

// --- ListProps ---

data class ListProps(
    val data: String,
    val itemTemplate: SpecNode?,
    val emptyText: String = "",
    val maxItems: Int? = null,
)

fun Map<String, SpecValue>.asListProps(): ListProps = ListProps(
    data = string("data") ?: "",
    itemTemplate = (get("itemTemplate") as? SpecValue.NodeValue)?.value,
    emptyText = string("emptyText") ?: "",
    maxItems = int("maxItems"),
)

// --- SpacerProps ---

data class SpacerProps(
    val size: Int = 0,
    val flex: Float = 0f,
)

fun Map<String, SpecValue>.asSpacerProps(): SpacerProps = SpacerProps(
    size = int("size") ?: 0,
    flex = double("flex")?.toFloat() ?: 0f,
)

// --- GridProps ---

data class GridProps(
    val columns: Int = 2,
    val gap: Int = 0,
)

fun Map<String, SpecValue>.asGridProps(): GridProps = GridProps(
    columns = int("columns") ?: 2,
    gap = int("gap") ?: 0,
)

// --- ForceGraphProps ---
// First "dense/semantic" primitive. See ForceGraphProps in spec-types.ts
// for the full contract (topology URL, optional event stream, physics).

data class ForceGraphProps(
    val topologyUrl: String,
    val eventStreamUrl: String? = null,
    val physics: ForceGraphPhysics = ForceGraphPhysics(),
)

data class ForceGraphPhysics(
    val repulsion: Double = 100.0,
    val attraction: Double = 0.05,
    val damping: Double = 0.9,
    val domainAnchoring: Boolean = true,
)

fun Map<String, SpecValue>.asForceGraphProps(): ForceGraphProps {
    val physicsMap = (get("physics") as? SpecValue.ObjectValue)?.value ?: emptyMap()
    return ForceGraphProps(
        topologyUrl = string("topology_url") ?: "",
        eventStreamUrl = string("event_stream_url"),
        physics = ForceGraphPhysics(
            repulsion = physicsMap.double("repulsion") ?: 100.0,
            attraction = physicsMap.double("attraction") ?: 0.05,
            damping = physicsMap.double("damping") ?: 0.9,
            domainAnchoring = physicsMap.boolean("domain_anchoring") ?: true,
        ),
    )
}
