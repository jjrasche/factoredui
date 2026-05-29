package ai.factoredui.server.factors

import ai.factoredui.compose.capture.CaptureEvent
import ai.factoredui.compose.capture.CapturePlatform
import ai.factoredui.compose.capture.EventType
import ai.factoredui.compose.capture.Session
import ai.factoredui.engine.factors.FactorTier
import ai.factoredui.server.ingestEvents
import ai.factoredui.server.runMigrations
import kotlinx.datetime.Instant
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real-Postgres verification of the v1 factor SQL (0002_factors.sql). Seeds
 * events through the actual [ingestEvents] path — so this exercises the whole
 * capture → factor pipeline — then asserts each of the five factors plus the
 * cross-user aggregate.
 *
 * H2's PG-compat mode diverges on TIMESTAMPTZ / JSONB / percentile_cont, so the
 * SQL is only meaningful against real Postgres. Skips (passes vacuously) when
 * Docker is unavailable, per the server-SQL testing decision in DECISIONS.md.
 */
class FactorsIntegrationTest {

    private val base = Instant.parse("2026-01-01T00:00:00Z")
    private fun at(secondsAfter: Long): Instant = Instant.fromEpochSeconds(base.epochSeconds + secondsAfter)

    private fun event(type: EventType, component: String, secondsAfter: Long) =
        CaptureEvent(eventType = type, componentPath = component, occurredAt = at(secondsAfter))

    @Test
    fun computesFiveFactorsAndAggregatesOverIngestedEvents() {
        if (!DockerClientFactory.instance().isDockerAvailable) {
            println("[FactorsIntegrationTest] Docker unavailable — skipping (SQL verifies on real Postgres only).")
            return
        }

        PostgreSQLContainer(DockerImageName.parse("postgres:16.4-alpine")).use { postgres ->
            postgres.start()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                runMigrations(connection)
                seed(connection)

                assertUserFactors(connection)
                assertNullDenominatorExclusion(connection)
                assertComponentAggregate(connection)
                assertSingleUserStddevIsNull(connection)
                assertUnknownUserHasNoFactors(connection)
            }
        }
    }

    private fun seed(connection: Connection) {
        // u1: rich activity on checkout/pay + a single-user component (search/bar).
        ingestEvents(
            connection,
            Session(id = "s1", userId = "u1", startedAt = base, platform = CapturePlatform.WEB),
            listOf(
                event(EventType.IMPRESSION, "checkout/pay", 0),
                event(EventType.CLICK, "checkout/pay", 2), // first interaction → 2000ms
                event(EventType.CLICK, "checkout/pay", 5),
                event(EventType.ERROR, "checkout/pay", 6),
                event(EventType.RAGE_CLICK, "checkout/pay", 7),
                event(EventType.DEAD_CLICK, "checkout/pay", 8),
                event(EventType.SCROLL, "checkout/pay", 9),
                event(EventType.SCROLL, "checkout/pay", 10),
                event(EventType.SCROLL_REVERSAL, "checkout/pay", 11),
                event(EventType.IMPRESSION, "search/bar", 20),
                event(EventType.CLICK, "search/bar", 21),
                event(EventType.ERROR, "search/bar", 22),
            ),
        )
        // u2: cleaner run on checkout/pay, slower first click (4000ms).
        ingestEvents(
            connection,
            Session(id = "s2", userId = "u2", startedAt = base, platform = CapturePlatform.WEB),
            listOf(
                event(EventType.IMPRESSION, "checkout/pay", 0),
                event(EventType.CLICK, "checkout/pay", 4),
                event(EventType.CLICK, "checkout/pay", 6),
                event(EventType.DEAD_CLICK, "checkout/pay", 7),
                event(EventType.SCROLL, "checkout/pay", 8),
            ),
        )
        // u3: impression + scroll only — no clicks, no interaction after impression.
        ingestEvents(
            connection,
            Session(id = "s3", userId = "u3", startedAt = base, platform = CapturePlatform.WEB),
            listOf(
                event(EventType.IMPRESSION, "checkout/pay", 0),
                event(EventType.SCROLL, "checkout/pay", 1),
            ),
        )
    }

    private fun assertUserFactors(connection: Connection) {
        val u1 = queryFactors(connection, "u1", "checkout/pay").associateBy { it.factorName }
        assertEquals(5, u1.size, "u1 should have all five factors on checkout/pay")
        assertClose(1.0 / 9.0, u1.getValue("error_rate").value)
        assertEquals(FactorTier.ALARM, u1.getValue("error_rate").factorTier)
        assertClose(0.5, u1.getValue("rage_click_rate").value)
        assertClose(0.5, u1.getValue("dead_click_rate").value)
        assertClose(0.5, u1.getValue("scroll_reversal_rate").value)
        assertClose(2000.0, u1.getValue("hesitation_time_p50_ms").value)
        assertEquals(FactorTier.DIAGNOSTIC, u1.getValue("hesitation_time_p50_ms").factorTier)
    }

    private fun assertNullDenominatorExclusion(connection: Connection) {
        // u3 has no clicks (rage/dead-click-rate denominators are zero → excluded)
        // and no interaction after impression (no hesitation). Only error_rate and
        // scroll_reversal_rate survive.
        val u3 = queryFactors(connection, "u3", "checkout/pay").map { it.factorName }.toSet()
        assertEquals(setOf("error_rate", "scroll_reversal_rate"), u3)
    }

    private fun assertComponentAggregate(connection: Connection) {
        val byFactor = queryComponentFactors(connection, "checkout/pay").associateBy { it.factorName }
        assertEquals(5, byFactor.size)

        val error = byFactor.getValue("error_rate")
        assertEquals(3, error.userCount) // u1, u2, u3
        assertClose(0.0, error.minValue)
        assertClose(1.0 / 9.0, error.maxValue)

        val dead = byFactor.getValue("dead_click_rate")
        assertEquals(2, dead.userCount) // u1, u2 (u3 excluded — no clicks)
        assertClose(0.5, dead.avgValue)

        val hesitation = byFactor.getValue("hesitation_time_p50_ms")
        assertEquals(2, hesitation.userCount) // u1=2000, u2=4000 (u3 none)
        assertClose(3000.0, hesitation.avgValue)
        assertClose(2000.0, hesitation.minValue)
        assertClose(4000.0, hesitation.maxValue)
        assertNotNull(hesitation.stddevValue, "two samples → stddev defined")
    }

    private fun assertSingleUserStddevIsNull(connection: Connection) {
        // search/bar has only u1, so each factor's stddev is over a single sample.
        val error = queryComponentFactors(connection, "search/bar").first { it.factorName == "error_rate" }
        assertEquals(1, error.userCount)
        assertNull(error.stddevValue, "single sample → stddev is null, not zero")
    }

    private fun assertUnknownUserHasNoFactors(connection: Connection) {
        assertTrue(queryFactors(connection, "nobody", "checkout/pay").isEmpty())
    }

    private fun assertClose(expected: Double, actual: Double, eps: Double = 1e-9) {
        assertTrue(abs(expected - actual) < eps, "expected $expected, got $actual")
    }
}
