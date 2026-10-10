package ai.factoredui.compose.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

class ReportsFormatException(message: String) : IllegalArgumentException(message)

data class ReportColumn(val id: String, val label: String, val unit: String?)

/** A null value is a figure the engine does not know; it is never zero. */
data class ReportRow(val id: String, val label: String, val objectIds: List<String>, val values: Map<String, Double?>)

data class Report(val id: String, val label: String, val columns: List<ReportColumn>, val rows: List<ReportRow>)

fun readReports(text: String): List<Report> {
    val root = try {
        Json.parseToJsonElement(text)
    } catch (failure: IllegalArgumentException) {
        throw ReportsFormatException("the reports are not valid JSON")
    } as? JsonObject ?: throw ReportsFormatException("the reports must be a JSON object")
    if (root["reports"] == null) throw ReportsFormatException("the reports object lacks a reports array")
    val reports = objectsAt(root, "reports", "reports").mapIndexed { index, report -> reportOf("reports[$index]", report) }
    refuseSharedIds(reports)
    return reports
}

fun reportRowsState(report: Report): List<Map<String, Any?>> = report.rows.map { row ->
    mapOf("id" to row.id, "label" to row.label, "object_ids" to row.objectIds, "values" to row.values)
}

fun reportsState(reports: List<Report>): Map<String, Map<String, Any?>> = reports.associate { report ->
    report.id to mapOf(
        "id" to report.id,
        "label" to report.label,
        "columns" to report.columns.map { mapOf("id" to it.id, "label" to it.label, "unit" to it.unit) },
        "rows" to reportRowsState(report),
    )
}

private fun refuseSharedIds(reports: List<Report>) {
    val shared = reports.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 } ?: return
    throw ReportsFormatException("two reports share the id '${shared.key}'")
}

private fun wrongType(where: String, expected: String, found: JsonElement) = ReportsFormatException("$where must be $expected, not $found")

private fun present(fields: JsonObject, key: String): JsonElement? = fields[key]?.takeIf { it !is JsonNull }

private fun textAt(fields: JsonObject, key: String, where: String): String? =
    present(fields, key)?.let { textOf(it) ?: throw wrongType("$where.$key", "text", it) }

private fun idAt(fields: JsonObject, where: String): String = textAt(fields, "id", where) ?: throw ReportsFormatException("$where lacks a text id")

private fun arrayAt(fields: JsonObject, key: String, where: String): JsonArray =
    present(fields, key)?.let { it as? JsonArray ?: throw wrongType(where, "an array", it) } ?: JsonArray(emptyList())

private fun objectsAt(fields: JsonObject, key: String, where: String): List<JsonObject> =
    arrayAt(fields, key, where).mapIndexed { index, entry -> entry as? JsonObject ?: throw wrongType("$where[$index]", "an object", entry) }

private fun textsAt(fields: JsonObject, key: String, where: String): List<String> =
    arrayAt(fields, key, where).mapIndexed { index, entry -> textOf(entry) ?: throw wrongType("$where[$index]", "text", entry) }

private fun figureOf(where: String, element: JsonElement): Double? =
    if (element is JsonNull) null else numberOf(element) ?: throw wrongType(where, "a number or null", element)

private fun valuesAt(fields: JsonObject, where: String): Map<String, Double?> {
    val values = present(fields, "values") ?: return emptyMap()
    val entries = values as? JsonObject ?: throw wrongType("$where.values", "an object", values)
    return entries.mapValues { (key, figure) -> figureOf("$where.values.$key", figure) }
}

private fun columnOf(where: String, column: JsonObject): ReportColumn {
    val id = idAt(column, where)
    return ReportColumn(id, textAt(column, "label", where) ?: id, textAt(column, "unit", where))
}

private fun rowOf(where: String, row: JsonObject): ReportRow {
    val id = idAt(row, where)
    return ReportRow(id, textAt(row, "label", where) ?: id, textsAt(row, "object_ids", "$where.object_ids"), valuesAt(row, where))
}

private fun reportOf(where: String, report: JsonObject): Report {
    val id = idAt(report, where)
    return Report(
        id = id,
        label = textAt(report, "label", where) ?: id,
        columns = objectsAt(report, "columns", "$where.columns").mapIndexed { index, column -> columnOf("$where.columns[$index]", column) },
        rows = objectsAt(report, "rows", "$where.rows").mapIndexed { index, row -> rowOf("$where.rows[$index]", row) },
    )
}
