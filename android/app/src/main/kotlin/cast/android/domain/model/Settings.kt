package cast.android.domain.model

import cast.android.BuildConfig

data class Settings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val hidePlayed: Boolean = false,
) {
    companion object {
        val DEFAULT_SERVER_URL = BuildConfig.DEFAULT_SERVER_URL
    }
}
