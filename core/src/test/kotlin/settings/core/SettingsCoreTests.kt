package settings.core

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.core.usecase.UpdateSettings
import settings.fakes.FakeSettingsPersistence

class SettingsCoreTests : DescribeSpec({
    describe("Settings Domain Hexagon") {
        lateinit var persistence: FakeSettingsPersistence
        lateinit var getSettings: GetSettings
        lateinit var updateSettings: UpdateSettings

        beforeEach {
            persistence = FakeSettingsPersistence()
            getSettings = GetSettings(persistence)
            updateSettings = UpdateSettings(persistence)
        }

        it("returns default settings before any update") {
            getSettings() shouldBe Settings(hidePlayed = false)
        }

        it("persists hidePlayed = true after update") {
            updateSettings(Settings(hidePlayed = true))

            getSettings() shouldBe Settings(hidePlayed = true)
        }

        it("persists hidePlayed = false after being set to true") {
            updateSettings(Settings(hidePlayed = true))
            updateSettings(Settings(hidePlayed = false))

            getSettings() shouldBe Settings(hidePlayed = false)
        }
    }
})
