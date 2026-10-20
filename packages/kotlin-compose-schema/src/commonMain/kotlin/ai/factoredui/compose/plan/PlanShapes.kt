package ai.factoredui.compose.plan

import ai.factoredui.compose.stackedscene.RectMm

enum class PlanShapeKind(val wire: String) { RECT("rect"), LINE("line"), MARKER("marker"), LABEL("label") }

enum class PlanLabelAlign(val wire: String) { START("start"), CENTER("center"), END("end") }

data class PlanStyle(
    val fill: String? = null,
    val stroke: String? = null,
    val weight: Float? = null,
    val dash: Boolean = false,
    val material: String? = null,
)

/** A shape in millimetres. A rect spans [widthMm] east by [depthMm] north from its corner; a line runs to ([x2Mm], [y2Mm]); a marker and a label sit at the point, a label nudged by [dxDp] right and [dyDp] down. */
data class PlanShape(
    val id: String,
    val kind: PlanShapeKind,
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double? = null,
    val depthMm: Double? = null,
    val x2Mm: Double? = null,
    val y2Mm: Double? = null,
    val style: String? = null,
    val text: String? = null,
    val align: PlanLabelAlign = PlanLabelAlign.CENTER,
    val dxDp: Float = 0f,
    val dyDp: Float = 0f,
)

data class PlanLegendEntry(val label: String, val style: String)

data class PlanRead(
    val shapes: List<PlanShape>,
    val styles: Map<String, PlanStyle>,
    val legend: List<PlanLegendEntry>,
    val notices: List<String>,
)

private fun numberIn(fields: Map<*, *>, key: String): Double? = (fields[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun fieldsOf(entry: Any?): Map<*, *>? = entry as? Map<*, *>

private fun styleOf(fields: Map<*, *>): PlanStyle = PlanStyle(
    fill = fields["fill"] as? String,
    stroke = fields["stroke"] as? String,
    weight = numberIn(fields, "weight")?.toFloat(),
    dash = fields["dash"] as? Boolean ?: false,
    material = fields["material"] as? String,
)

fun readPlanStyles(resolved: Any?): Map<String, PlanStyle> =
    fieldsOf(resolved).orEmpty().entries.mapNotNull { (name, body) ->
        val fields = fieldsOf(body) ?: return@mapNotNull null
        (name as? String)?.let { it to styleOf(fields) }
    }.toMap()

fun readPlanLegend(resolved: Any?): List<PlanLegendEntry> =
    (resolved as? List<*>).orEmpty().mapNotNull { entry ->
        val fields = fieldsOf(entry) ?: return@mapNotNull null
        val label = fields["label"] as? String ?: return@mapNotNull null
        val style = fields["style"] as? String ?: return@mapNotNull null
        PlanLegendEntry(label, style)
    }

private fun kindOf(wire: Any?): PlanShapeKind? = PlanShapeKind.entries.firstOrNull { it.wire == wire }

private fun alignOf(wire: Any?): PlanLabelAlign = PlanLabelAlign.entries.firstOrNull { it.wire == wire } ?: PlanLabelAlign.CENTER

private fun shapeOf(index: Int, fields: Map<*, *>): Pair<PlanShape?, String?> {
    val id = fields["id"] as? String ?: "shape $index"
    val kind = kindOf(fields["kind"]) ?: return null to "plan shape '$id': kind must be one of ${PlanShapeKind.entries.joinToString { it.wire }}, not '${fields["kind"]}'; it is not drawn."
    val x = numberIn(fields, "x_mm")
    val y = numberIn(fields, "y_mm")
    if (x == null || y == null) return null to "plan shape '$id': x_mm and y_mm must be numbers; it is not drawn."
    val base = PlanShape(id, kind, x, y, style = fields["style"] as? String)
    return when (kind) {
        PlanShapeKind.RECT -> {
            val width = numberIn(fields, "width_mm")
            val depth = numberIn(fields, "depth_mm")
            if (width == null || depth == null) null to "plan shape '$id': a rect needs width_mm and depth_mm as numbers; it is not drawn."
            else base.copy(widthMm = width, depthMm = depth) to null
        }
        PlanShapeKind.LINE -> {
            val x2 = numberIn(fields, "x2_mm")
            val y2 = numberIn(fields, "y2_mm")
            if (x2 == null || y2 == null) null to "plan shape '$id': a line needs x2_mm and y2_mm as numbers; it is not drawn."
            else base.copy(x2Mm = x2, y2Mm = y2) to null
        }
        PlanShapeKind.MARKER -> base to null
        PlanShapeKind.LABEL -> {
            val text = fields["text"] as? String
            if (text == null) null to "plan shape '$id': a label needs text; it is not drawn."
            else base.copy(text = text, align = alignOf(fields["align"]), dxDp = numberIn(fields, "dx_dp")?.toFloat() ?: 0f, dyDp = numberIn(fields, "dy_dp")?.toFloat() ?: 0f) to null
        }
    }
}

fun readPlan(shapes: Any?, styles: Any?, legend: Any?): PlanRead {
    val entries = (shapes as? List<*>).orEmpty().mapIndexed { index, entry ->
        fieldsOf(entry)?.let { shapeOf(index, it) } ?: (null to "plan shape $index is not an object; it is not drawn.")
    }
    val readStyles = readPlanStyles(styles)
    val drawn = entries.mapNotNull { it.first }
    val unknownStyles = drawn.mapNotNull { it.style }.filter { it !in readStyles }.distinct()
        .map { "plan style '$it' is not in styles; its shapes are drawn plain." }
    return PlanRead(drawn, readStyles, readPlanLegend(legend), entries.mapNotNull { it.second } + unknownStyles)
}

fun planWindow(shapes: List<PlanShape>): RectMm? {
    val points = shapes.flatMap { shape ->
        when (shape.kind) {
            PlanShapeKind.RECT -> listOf(shape.xMm to shape.yMm, shape.xMm + shape.widthMm!! to shape.yMm + shape.depthMm!!)
            PlanShapeKind.LINE -> listOf(shape.xMm to shape.yMm, shape.x2Mm!! to shape.y2Mm!!)
            PlanShapeKind.MARKER, PlanShapeKind.LABEL -> listOf(shape.xMm to shape.yMm)
        }
    }
    if (points.isEmpty()) return null
    return RectMm(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
}
