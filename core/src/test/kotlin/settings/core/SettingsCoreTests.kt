package settings.core

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.core.usecase.UpdateSettings
import settings.fakes.FakeSettingsPersistence

class SettingsCoreTests : DescribeSpec({
    describe("Settings domain") {
        lateinit var persistence: FakeSettingsPersistence
        lateinit var getSettings: GetSettings
        lateinit var updateSettings: UpdateSettings

        beforeEach {
            persistence = FakeSettingsPersistence()
            getSettings = GetSettings(persistence)
            updateSettings = UpdateSettings(persistence)
        }

        it("returns default settings when nothing has been set") {
            getSettings().hidePlayed shouldBe false
        }

        it("persists updated settings") {
            updateSettings(Settings(hidePlayed = true))
            getSettings().hidePlayed shouldBe true
        }

        it("can toggle hidePlayed back to false") {
            updateSettings(Settings(hidePlayed = true))
            updateSettings(Settings(hidePlayed = false))
            getSettings().hidePlayed shouldBe false
        }
    }
})
