package ai.factoredui.application

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildQueueTest {
    @Test
    fun builds_run_one_at_a_time_a_visitors_before_a_retry_and_a_retry_before_a_warm() {
        val queue = BuildQueue()
        val holdTheWorker = CountDownLatch(1)
        val started = CountDownLatch(1)
        val order = java.util.concurrent.CopyOnWriteArrayList<String>()
        val done = CountDownLatch(3)
        queue.submit(BuildPriority.WARM) { started.countDown(); holdTheWorker.await(10, TimeUnit.SECONDS) }
        started.await(10, TimeUnit.SECONDS)
        queue.submit(BuildPriority.WARM) { order += "warm"; done.countDown() }
        queue.submit(BuildPriority.RETRY) { order += "retry"; done.countDown() }
        queue.submit(BuildPriority.VISITOR) { order += "visitor"; done.countDown() }
        holdTheWorker.countDown()
        done.await(10, TimeUnit.SECONDS)
        assertEquals(listOf("visitor", "retry", "warm"), order.toList())
    }

    @Test
    fun a_warm_build_a_visitor_then_asks_for_is_moved_ahead_of_the_other_warm_builds() {
        val queue = BuildQueue()
        val holdTheWorker = CountDownLatch(1)
        val started = CountDownLatch(1)
        val order = java.util.concurrent.CopyOnWriteArrayList<String>()
        val done = CountDownLatch(2)
        queue.submit(BuildPriority.WARM) { started.countDown(); holdTheWorker.await(10, TimeUnit.SECONDS) }
        started.await(10, TimeUnit.SECONDS)
        queue.submit(BuildPriority.WARM) { order += "first"; done.countDown() }
        val second = queue.submit(BuildPriority.WARM) { order += "second"; done.countDown() }
        queue.promote(second)
        holdTheWorker.countDown()
        done.await(10, TimeUnit.SECONDS)
        assertEquals(listOf("second", "first"), order.toList())
    }
}