package cast.android.domain.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import cast.android.domain.model.Settings
import cast.android.network.BaseUrlInterceptor
import cast.android.network.FakeCastApiService
import cast.api.SettingsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryImplTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.newFolder(), "settings.preferences_pb")
        }

    @Test
    fun `updateSettings pushes hidePlayed to the server`() = runTest {
        val api = FakeCastApiService()
        val repo = SettingsRepositoryImpl(dataStore(backgroundScope), BaseUrlInterceptor(), api)

        repo.updateSettings(Settings(serverUrl = "http://host", hidePlayed = true))

        assertEquals(SettingsDto(hidePlayed = true), api.updatedSettings)
    }

    @Test
    fun `refresh writes the server hidePlayed into settings`() = runTest {
        val api = FakeCastApiService(settings = SettingsDto(hidePlayed = true))
        val repo = SettingsRepositoryImpl(dataStore(backgroundScope), BaseUrlInterceptor(), api)

        repo.refresh()

        assertEquals(true, repo.settings.first().hidePlayed)
    }
}
