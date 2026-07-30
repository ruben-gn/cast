package cast.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaseUrlInterceptorTest {

    @Test fun `an unconfigured server url is not a usable base`() {
        assertNull(normalizeBaseUrl(""))
        assertNull(normalizeBaseUrl("   "))
    }

    @Test fun `a bare host gets an http scheme`() {
        val url = normalizeBaseUrl("100.118.137.8:8100")
        assertEquals("http", url?.scheme)
        assertEquals("100.118.137.8", url?.host)
        assertEquals(8100, url?.port)
    }

    @Test fun `an explicit scheme is kept`() =
        assertEquals("https", normalizeBaseUrl("https://cast.example.com")?.scheme)
}
