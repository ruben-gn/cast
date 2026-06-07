package api

import cast.api.SettingsDto
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.core.usecase.UpdateSettings

fun Route.settingsApi(dependencies: DependencyRegistry) {
    val getSettings: GetSettings by dependencies
    val updateSettings: UpdateSettings by dependencies

    get {
        val settings = getSettings()
        call.respond(SettingsDto(hidePlayed = settings.hidePlayed, recentListeningOnly = settings.recentListeningOnly))
    }

    put {
        val dto = call.receive<SettingsDto>()
        updateSettings(Settings(hidePlayed = dto.hidePlayed, recentListeningOnly = dto.recentListeningOnly))
        call.respond(HttpStatusCode.NoContent)
    }
}
