package settings.fakes

import io.kotest.core.spec.style.DescribeSpec
import settings.core.ports.settingsPersistenceContract

class FakeSettingsPersistenceTest : DescribeSpec({
    lateinit var persistence: FakeSettingsPersistence
    beforeEach { persistence = FakeSettingsPersistence() }
    include(settingsPersistenceContract { persistence })
})
