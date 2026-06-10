package playback.fakes

import io.kotest.core.spec.style.DescribeSpec
import playback.core.ports.playbackPersistenceContract

class FakePlaybackPersistenceTest : DescribeSpec({
    lateinit var persistence: FakePlaybackPersistence
    beforeEach { persistence = FakePlaybackPersistence() }
    include(playbackPersistenceContract { persistence })
})
