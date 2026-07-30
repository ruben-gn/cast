package cast.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSocketUrlTest {

    @Test fun `no configured server means no socket to open`() {
        assertNull(playbackSocketUrl(""))
        assertNull(playbackSocketUrl("   "))
    }

    @Test fun `a bare host still reaches the playback endpoint`() =
        assertEquals(
            "http://100.118.137.8:8100/api/playback",
            playbackSocketUrl("100.118.137.8:8100").toString(),
        )

    @Test fun `a full url keeps its scheme and port`() =
        assertEquals(
            "http://hobby.pig-tilapia.ts.net:8100/api/playback",
            playbackSocketUrl("http://hobby.pig-tilapia.ts.net:8100").toString(),
        )

    @Test fun `a trailing slash does not double up`() =
        assertEquals(
            "https://cast.example.com/api/playback",
            playbackSocketUrl("https://cast.example.com/").toString(),
        )
}
