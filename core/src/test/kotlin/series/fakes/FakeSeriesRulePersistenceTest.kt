package series.fakes

import io.kotest.core.spec.style.DescribeSpec
import series.core.ports.seriesRulePersistenceContract

class FakeSeriesRulePersistenceTest : DescribeSpec({
    lateinit var persistence: FakeSeriesRulePersistence
    beforeEach { persistence = FakeSeriesRulePersistence() }
    include(seriesRulePersistenceContract { persistence })
})
