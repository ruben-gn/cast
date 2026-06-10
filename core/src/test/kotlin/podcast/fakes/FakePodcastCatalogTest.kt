package podcast.fakes

import io.kotest.core.spec.style.DescribeSpec
import podcast.core.ports.podcastCatalogContract

class FakePodcastCatalogTest : DescribeSpec({
    lateinit var catalog: FakePodcastCatalog
    beforeEach { catalog = FakePodcastCatalog() }
    include(podcastCatalogContract { catalog })
})
