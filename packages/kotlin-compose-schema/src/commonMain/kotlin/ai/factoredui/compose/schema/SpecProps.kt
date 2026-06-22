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
    /**
     * Live query resolved by the host's HostDataSource, e.g.
     * "query:automations:approved_at_ms IS NULL". When set (and the host wires
     * a HostDataSource into the RenderContext), the list subscribes to a Flow
     * of result sets and re-renders on every change. When null, the list reads
     * the static [data] key against the render context — behaviour is unchanged.
     * The string is opaque to the renderer; only the host's storage layer
     * interprets it.
     */
    val dataSource: String?,
    val itemTemplate: SpecNode?,
    val emptyText: String = "",
    val maxItems: Int? = null,
)

fun Map<String, SpecValue>.asListProps(): ListProps = ListProps(
    data = string("data") ?: "",
    dataSource = string("data_source"),
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

/**
 * `lazy` opts into LazyVerticalGrid (windowed rendering, supports thousands of
 * items). Off by default because LazyVerticalGrid requires a bounded height —
 * dropping it inside an unbounded scrollview throws at runtime. Spec authors
 * with large item counts should set `lazy: true` and ensure the grid sits in
 * a height-bounded container (e.g. fillMaxSize, or a sized parent).
 */
data class GridProps(
    val columns: Int = 2,
    val gap: Int = 0,
    val lazy: Boolean = false,
)

fun Map<String, SpecValue>.asGridProps(): GridProps = GridProps(
    columns = int("columns") ?: 2,
    gap = int("gap") ?: 0,
    lazy = boolean("lazy") ?: false,
)

// --- ToggleProps ---

data class ToggleProps(
    val label: String = "",
)

fun Map<String, SpecValue>.asToggleProps(): ToggleProps = ToggleProps(
    label = string("label") ?: "",
)

// --- SliderProps ---

data class SliderProps(
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float? = null,
)

fun Map<String, SpecValue>.asSliderProps(): SliderProps = SliderProps(
    min = double("min")?.toFloat() ?: 0f,
    max = double("max")?.toFloat() ?: 1f,
    step = double("step")?.toFloat(),
)

// --- SelectProps ---

data class SelectOption(val label: String, val value: String)

data class SelectProps(
    val options: List<SelectOption> = emptyList(),
    val placeholder: String = "",
)

fun Map<String, SpecValue>.asSelectProps(): SelectProps {
    val rawOptions = (get("options") as? SpecValue.ArrayValue)?.value ?: emptyList()
    val parsed = rawOptions.mapNotNull { item ->
        val obj = (item as? SpecValue.ObjectValue)?.value ?: return@mapNotNull null
        val label = obj.string("label") ?: return@mapNotNull null
        val value = obj.string("value") ?: return@mapNotNull null
        SelectOption(label = label, value = value)
    }
    return SelectProps(
        options = parsed,
        placeholder = string("placeholder") ?: "",
    )
}

// --- TabsProps ---

data class TabsProps(
    val items: List<String> = emptyList(),
)

fun Map<String, SpecValue>.asTabsProps(): TabsProps {
    val raw = (get("items") as? SpecValue.ArrayValue)?.value ?: emptyList()
    return TabsProps(
        items = raw.mapNotNull { (it as? SpecValue.StringValue)?.value },
    )
}

// --- ModalProps ---

data class ModalProps(
    val title: String = "",
    val dismissible: Boolean = true,
)

fun Map<String, SpecValue>.asModalProps(): ModalProps = ModalProps(
    title = string("title") ?: "",
    dismissible = boolean("dismissible") ?: true,
)

// --- ForceGraphProps ---
// First "dense/semantic" primitive. See ForceGraphProps in spec-types.ts
// for the full contract (topology URL, optional event stream, physics).

data class ForceGraphProps(
    val topologyUrl: String,
    val eventStreamUrl: String? = null,
    val historyUrl: String? = null,
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
        historyUrl = string("history_url"),
        physics = ForceGraphPhysics(
            repulsion = physicsMap.double("repulsion") ?: 100.0,
            attraction = physicsMap.double("attraction") ?: 0.05,
            damping = physicsMap.double("damping") ?: 0.9,
            domainAnchoring = physicsMap.boolean("domain_anchoring") ?: true,
        ),
    )
}

data class FieldGraphProps(
    val topologyUrl: String,
    val onRelevanceChangeAction: String? = null,
    val onNodeTapAction: String? = null,
    val reduceMotion: Boolean = false,
)

fun Map<String, SpecValue>.asFieldGraphProps(): FieldGraphProps = FieldGraphProps(
    topologyUrl = string("topology_url") ?: "",
    onRelevanceChangeAction = string("on_relevance_change"),
    onNodeTapAction = string("on_node_tap"),
    reduceMotion = boolean("reduce_motion") ?: false,
)

// --- Scene3dProps ---
// Second "dense/semantic" primitive; full contract in spec-types.ts. Entities,
// camera, and lights live in the fetched world state, not in spec props.

data class Scene3dProps(
    val worldStateUrl: String,
    val worldStreamUrl: String? = null,
    val actionUrl: String? = null,
    val background: String = "neutral-gray",
    val clipUrl: String? = null,
    val clipUrlB: String? = null,
    val clipFrame: Int = 0,
    val clipFrameFraction: Float = 0f,
    val clipImpulse: Float = 0.8f,
    val clipAutoplay: Boolean = false,
    val clipSeverity: Float = 0f,
    val clipEffector: String = "R_Hand",
    val board: String? = null,
    val engineUrl: String? = null,
    val engine: String = "injury",
    val simId: String? = null,
)

fun Map<String, SpecValue>.asScene3dProps(): Scene3dProps = Scene3dProps(
    worldStateUrl = string("world_state_url") ?: "",
    worldStreamUrl = string("world_stream_url"),
    actionUrl = string("action_url"),
    background = string("background") ?: "neutral-gray",
    clipUrl = string("clip_url"),
    clipUrlB = string("clip_url_b"),
    clipFrame = (double("frame") ?: 0.0).toInt(),
    clipFrameFraction = (double("frame") ?: 0.0).toFloat(),
    clipImpulse = (double("impulse") ?: 0.8).toFloat(),
    board = string("board"),
    engineUrl = string("engine_url"),
    engine = string("engine") ?: "injury",
    simId = string("sim_id"),
)
