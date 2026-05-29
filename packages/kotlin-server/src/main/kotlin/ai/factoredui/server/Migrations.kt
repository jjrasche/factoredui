package ai.factoredui.server

import java.sql.Connection

/**
 * Run factoredui's schema migrations against [connection].
 *
 * The migrations use `CREATE ... IF NOT EXISTS` and `ON CONFLICT DO
 * NOTHING`, so calling this on a database that already has the schema
 * is a no-op. Call once at host-app boot, before any `ingestEvents`
 * call.
 *
 * Idempotency assumption: every migration in
 * `src/main/resources/factoredui/migrations/` is CREATE-IF-NOT-EXISTS
 * style. Future migrations that change column types or rename tables
 * will need a real migration manager (Flyway, Liquibase) — at that
 * point this function's contract evolves.
 */
fun runMigrations(connection: Connection) {
    val resourceNames = listOf(
        "factoredui/migrations/0001_init.sql",
        "factoredui/migrations/0002_factors.sql",
    )
    val loader = Migrations::class.java.classLoader
    connection.createStatement().use { stmt ->
        for (name in resourceNames) {
            val sql = loader.getResourceAsStream(name)?.bufferedReader()?.readText()
                ?: error("factoredui migration resource not found: $name")
            stmt.execute(sql)
        }
    }
}

private object Migrations
