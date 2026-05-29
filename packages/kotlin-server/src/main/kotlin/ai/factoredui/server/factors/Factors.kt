package ai.factoredui.server.factors

import ai.factoredui.engine.factors.ComponentFactorAggregate
import ai.factoredui.engine.factors.Factor
import ai.factoredui.engine.factors.FactorTier
import java.sql.Connection
import java.sql.ResultSet

/**
 * Factor query API — reads the v1 factor views (0002_factors.sql).
 *
 * Embedded-library shape, matching Ingest.kt: every function takes a JDBC
 * [Connection] the host owns. JDBC is blocking; dispatch to `Dispatchers.IO`
 * if calling from a coroutine. No factor computation lives here — the SQL views
 * do the work; these functions just read and map rows to typed records.
 */

/** Every computed factor for [userId] on [componentPath]. */
fun queryFactors(connection: Connection, userId: String, componentPath: String): List<Factor> {
    val sql = """
        SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
        FROM factoredui_user_factors
        WHERE user_id = ? AND component_path = ?
        ORDER BY factor_name
    """.trimIndent()
    connection.prepareStatement(sql).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, componentPath)
        stmt.executeQuery().use { rs -> return rs.mapRows { it.toFactor() } }
    }
}

/** Cross-user aggregate of every factor on [componentPath] — the dashboard feed. */
fun queryComponentFactors(connection: Connection, componentPath: String): List<ComponentFactorAggregate> {
    val sql = """
        SELECT component_path, factor_name, factor_tier, user_count,
               avg_value, median_value, p95_value, min_value, max_value, stddev_value
        FROM factoredui_component_factors
        WHERE component_path = ?
        ORDER BY factor_name
    """.trimIndent()
    connection.prepareStatement(sql).use { stmt ->
        stmt.setString(1, componentPath)
        stmt.executeQuery().use { rs -> return rs.mapRows { it.toComponentFactorAggregate() } }
    }
}

private fun <T> ResultSet.mapRows(map: (ResultSet) -> T): List<T> {
    val rows = mutableListOf<T>()
    while (next()) rows.add(map(this))
    return rows
}

private fun ResultSet.toFactor(): Factor = Factor(
    userId = getString("user_id"),
    componentPath = getString("component_path"),
    factorName = getString("factor_name"),
    factorTier = FactorTier.fromWire(getString("factor_tier")),
    value = getDouble("value"),
    computedAt = getTimestamp("computed_at").toInstant().toString(),
)

private fun ResultSet.toComponentFactorAggregate(): ComponentFactorAggregate {
    val stddev = getDouble("stddev_value")
    // stddev is NULL for a single-user factor — preserve that, don't read it as 0.
    val stddevValue = if (wasNull()) null else stddev
    return ComponentFactorAggregate(
        componentPath = getString("component_path"),
        factorName = getString("factor_name"),
        factorTier = FactorTier.fromWire(getString("factor_tier")),
        userCount = getInt("user_count"),
        avgValue = getDouble("avg_value"),
        medianValue = getDouble("median_value"),
        p95Value = getDouble("p95_value"),
        minValue = getDouble("min_value"),
        maxValue = getDouble("max_value"),
        stddevValue = stddevValue,
    )
}
