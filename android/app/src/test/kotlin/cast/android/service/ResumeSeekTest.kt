package cast.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeSeekTest {

    @Test fun `no cached progress means no local seek`() =
        assertNull(localResumePositionMs(null, played = false))

    @Test fun `zero or negative progress means no local seek`() {
        assertNull(localResumePositionMs(0L, played = false))
        assertNull(localResumePositionMs(-5L, played = false))
    }

    @Test fun `played episode never gets a local seek`() =
        assertNull(localResumePositionMs(60_000L, played = true))

    @Test fun `unfinished episode with cached progress seeks to it`() =
        assertEquals(60_000L, localResumePositionMs(60_000L, played = false))
}
