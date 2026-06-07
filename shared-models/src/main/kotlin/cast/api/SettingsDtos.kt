package cast.api

import kotlinx.serialization.Serializable

@Serializable
data class SettingsDto(val hidePlayed: Boolean, val recentListeningOnly: Boolean = true)
