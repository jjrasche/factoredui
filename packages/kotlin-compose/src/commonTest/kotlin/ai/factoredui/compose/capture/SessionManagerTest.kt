package ai.factoredui.compose.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant

class SessionManagerTest {

    @Test
    fun ensureSessionMintsFreshIdOnFirstCall() {
        var clock = Instant.fromEpochSeconds(1000)
        val manager = SessionManager(
            userId = { "user-1" },
            platform = CapturePlatform.WEB,
            now = { clock },
            newId = { "fixed-id" },
        )

        val session = manager.ensureSession()

        assertEquals("fixed-id", session.id)
        assertEquals("user-1", session.userId)
        assertEquals(CapturePlatform.WEB, session.platform)
        assertEquals(Instant.fromEpochSeconds(1000), session.startedAt)
    }

    @Test
    fun ensureSessionReturnsSameIdWithinTimeoutWindow() {
        var clock = Instant.fromEpochSeconds(1000)
        var idCounter = 0
        val manager = SessionManager(
            userId = { "user-1" },
            platform = CapturePlatform.WEB,
            timeout = 30.minutes,
            now = { clock },
            newId = { "id-${idCounter++}" },
        )

        val first = manager.ensureSession()
        clock = Instant.fromEpochSeconds(1000 + 60) // +1 minute
        val second = manager.ensureSession()

        assertEquals(first.id, second.id)
    }

    @Test
    fun ensureSessionRotatesAfterTimeoutOfInactivity() {
        var clock = Instant.fromEpochSeconds(1000)
        var idCounter = 0
        val manager = SessionManager(
            userId = { "user-1" },
            platform = CapturePlatform.WEB,
            timeout = 30.minutes,
            now = { clock },
            newId = { "id-${idCounter++}" },
        )

        val first = manager.ensureSession()
        clock = Instant.fromEpochSeconds(1000 + (31 * 60)) // +31 minutes
        val second = manager.ensureSession()

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun endSessionClearsCurrentId() {
        val manager = SessionManager(
            userId = { "user-1" },
            platform = CapturePlatform.WEB,
        )
        manager.ensureSession()
        manager.endSession()
        assertNull(manager.sessionId)
    }
}
