package podcast.fakes

import io.kotest.core.spec.style.DescribeSpec
import podcast.core.ports.seriesRulePersistenceContract

class FakeSeriesRulePersistenceTest : DescribeSpec({
    lateinit var persistence: FakeSeriesRulePersistence
    beforeEach { persistence = FakeSeriesRulePersistence() }
    include(seriesRulePersistenceContract { persistence })
})
