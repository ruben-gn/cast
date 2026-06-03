package cast.android.domain.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestCacheTest {

    @Test
    fun `latest is null before anything is cached`() {
        val cache = LatestCache<List<String>>()
        assertNull(cache.latest)
    }

    @Test
    fun `latest returns the most recently put value`() {
        val cache = LatestCache<List<String>>()
        cache.put(listOf("a"))
        cache.put(listOf("a", "b"))
        assertEquals(listOf("a", "b"), cache.latest)
    }

    @Test
    fun `clear resets latest to null`() {
        val cache = LatestCache<List<String>>()
        cache.put(listOf("a"))
        cache.clear()
        assertNull(cache.latest)
    }
}
