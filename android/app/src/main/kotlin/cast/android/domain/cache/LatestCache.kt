package cast.android.domain.cache

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the most recent successfully-fetched value for stale-while-revalidate reads.
 * The server remains the source of truth: [latest] is only ever shown while a fresh fetch runs,
 * and [put] overwrites it with the server's response.
 */
class LatestCache<T : Any> {
    private val ref = AtomicReference<T?>(null)

    val latest: T? get() = ref.get()

    fun put(value: T) { ref.set(value) }

    fun clear() { ref.set(null) }
}
