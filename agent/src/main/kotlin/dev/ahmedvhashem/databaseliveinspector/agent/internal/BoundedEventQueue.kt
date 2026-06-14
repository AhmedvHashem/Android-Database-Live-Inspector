package dev.ahmedvhashem.databaseliveinspector.agent.internal

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bounded drop-oldest queue for outbound capture events. Offers never block: when full, the
 * oldest event is dropped and accounted so the writer thread can emit a `dropped_events`
 * notice once capacity is available again.
 */
internal class BoundedEventQueue<T : Any>(private val capacity: Int) {

    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val items = ArrayDeque<T>()
    private var droppedCount = 0

    fun offer(item: T) {
        lock.withLock {
            if (items.size >= capacity) {
                items.removeFirst()
                droppedCount++
            }
            items.addLast(item)
            notEmpty.signalAll()
        }
    }

    /** Blocks up to [timeoutMs] for the next event; null on timeout. */
    fun poll(timeoutMs: Long): T? {
        lock.withLock {
            var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (items.isEmpty()) {
                if (remainingNanos <= 0) {
                    return null
                }
                remainingNanos = notEmpty.awaitNanos(remainingNanos)
            }
            return items.removeFirst()
        }
    }

    /**
     * Accounts an event dropped outside the offer path (e.g. the writer skipped one that
     * could not be framed) so it is still reported via the next `dropped_events` notice.
     */
    fun recordDropped() {
        lock.withLock { droppedCount++ }
    }

    /** Returns the number of events dropped since the last call, resetting the counter. */
    fun takeDroppedCount(): Int {
        lock.withLock {
            val count = droppedCount
            droppedCount = 0
            return count
        }
    }

    fun clear() {
        lock.withLock {
            items.clear()
            droppedCount = 0
        }
    }

    val size: Int
        get() = lock.withLock { items.size }
}
