package cast.android

import android.app.Application
import cast.android.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CastApplication : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Eagerly collect settings so BaseUrlInterceptor is initialized from DataStore at startup
        scope.launch { settingsRepository.settings.collect { } }
    }

    override fun onTerminate() {
        super.onTerminate()
        scope.cancel()
    }
}
