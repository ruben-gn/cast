package settings

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import settings.adapters.persistence.SQLiteSettingsPersistence
import settings.core.ports.SettingsPersistence
import settings.core.usecase.GetSettings
import settings.core.usecase.UpdateSettings

fun Application.installSettingsModule(persistence: SettingsPersistence? = null) {
    dependencies {
        provide<SettingsPersistence> { persistence ?: SQLiteSettingsPersistence(resolve()) }
        provide<GetSettings> { GetSettings(resolve()) }
        provide<UpdateSettings> { UpdateSettings(resolve()) }
    }
}
