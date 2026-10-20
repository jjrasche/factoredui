package ai.factoredui.application

import java.util.concurrent.ConcurrentLinkedQueue

/** How long each stage of a page build took, as the Server-Timing header says it. Stages that ran side by side overlap, so they do not add up to the total. */
class Timings {
    private val stages = ConcurrentLinkedQueue<Pair<String, Long>>()

    fun <T> timed(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            stages.add(name to (System.nanoTime() - start) / NANOS_PER_MILLI)
        }
    }

    fun header(total: Long): String = (stages.toList() + ("total" to total)).joinToString(", ") { (name, millis) -> "$name;dur=$millis" }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
