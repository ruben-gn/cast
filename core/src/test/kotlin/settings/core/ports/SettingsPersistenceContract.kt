package settings.core.ports

import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.shouldBe
import settings.core.models.Settings

fun settingsPersistenceContract(persistenceProvider: () -> SettingsPersistence): TestFactory = describeSpec {

    describe("get") {
        it("returns defaults when no settings have been saved") {
            val persistence = persistenceProvider()
            persistence.get() shouldBe Settings()
        }
    }

    describe("update") {
        it("persists hidePlayed = true") {
            val persistence = persistenceProvider()
            persistence.update(Settings(hidePlayed = true))

            persistence.get() shouldBe Settings(hidePlayed = true)
        }

        it("persists hidePlayed = false after it was true") {
            val persistence = persistenceProvider()
            persistence.update(Settings(hidePlayed = true))
            persistence.update(Settings(hidePlayed = false))

            persistence.get() shouldBe Settings(hidePlayed = false)
        }

        it("upserts on repeated calls") {
            val persistence = persistenceProvider()
            persistence.update(Settings(hidePlayed = true))
            persistence.update(Settings(hidePlayed = true))

            persistence.get() shouldBe Settings(hidePlayed = true)
        }

        it("persists recentListeningOnly = false") {
            val persistence = persistenceProvider()
            persistence.update(Settings(hidePlayed = false, recentListeningOnly = false))
            persistence.get().recentListeningOnly shouldBe false
        }

        it("persists recentListeningOnly = true after false") {
            val persistence = persistenceProvider()
            persistence.update(Settings(hidePlayed = false, recentListeningOnly = false))
            persistence.update(Settings(hidePlayed = false, recentListeningOnly = true))
            persistence.get().recentListeningOnly shouldBe true
        }
    }
}
