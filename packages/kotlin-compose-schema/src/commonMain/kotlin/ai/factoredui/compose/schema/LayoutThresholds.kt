package ai.factoredui.compose.schema

enum class DeviceClass(val wire: String) {
    COMPACT("compact"),
    MEDIUM("medium"),
    EXPANDED("expanded");

    companion object {
        fun fromWire(value: String): DeviceClass = entries.first { it.wire == value }
    }
}

/** What a page must reach on one device class for each layout factor; a theme carries one per class. */
data class LayoutThresholds(
    val minMainShare: Double,
    val maxChromeShare: Double,
    val minTapTargetDp: Double,
    val minTextLineDp: Double,
    val minContrast: Double,
    val maxTapsToControl: Int,
    val minAboveFoldControls: Int,
    val maxPixelDisagreement: Double = 0.05,
)

data class LayoutThresholdSet(
    val compact: LayoutThresholds = LayoutThresholds(0.80, 0.35, 44.0, 14.0, 4.5, 2, 2),
    val medium: LayoutThresholds = LayoutThresholds(0.70, 0.30, 44.0, 14.0, 4.5, 2, 3),
    val expanded: LayoutThresholds = LayoutThresholds(0.55, 0.30, 32.0, 14.0, 4.5, 1, 4),
) {
    fun forDevice(device: DeviceClass): LayoutThresholds = when (device) {
        DeviceClass.COMPACT -> compact
        DeviceClass.MEDIUM -> medium
        DeviceClass.EXPANDED -> expanded
    }
}
