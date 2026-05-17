package cast.android.domain.model

data class Settings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val hidePlayed: Boolean = false,
) {
    companion object {
        const val DEFAULT_SERVER_URL = "http://cast.local:8100"
    }
}
