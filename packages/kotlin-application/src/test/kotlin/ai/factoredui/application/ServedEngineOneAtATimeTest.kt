package ai.factoredui.application

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServedEngineOneAtATimeTest {
    private val engine = FakeEngine()

    @AfterTest
    fun stopEngine() = engine.stop()

    private fun askFromThreads(served: ServedEngine, threads: Int) {
        engine.runAnswer = { Thread.sleep(300); "{}" }
        (1..threads).map { Thread { served.answer(listOf("load", "world.json"), 30) }.apply { start() } }.forEach { it.join() }
    }

    @Test
    fun however_many_threads_ask_one_command_is_with_the_engine_at_a_time() {
        askFromThreads(ServedEngine(engine.address), threads = 4)
        assertEquals(1, engine.maxInFlight.get())
        assertEquals(4, engine.posted.size)
    }

    @Test
    fun given_two_turns_two_commands_are_with_the_engine_at_once() {
        askFromThreads(ServedEngine(engine.address, maxConcurrentCalls = 2), threads = 4)
        assertEquals(2, engine.maxInFlight.get())
    }
}