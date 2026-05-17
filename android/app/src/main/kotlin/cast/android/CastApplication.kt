package cast.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cast.android.domain.repository.SettingsRepository
import cast.android.work.RefreshFeedsWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CastApplication : Application(), Configuration.Provider {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Eagerly collect settings so BaseUrlInterceptor is initialized from DataStore at startup
        scope.launch { settingsRepository.settings.collect { } }
        schedulePeriodicRefresh()
    }

    override fun onTerminate() {
        super.onTerminate()
        scope.cancel()
    }

    private fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<RefreshFeedsWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RefreshFeedsWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
