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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CastApplication : Application(), Configuration.Provider {

    // Injected so the singleton is constructed at startup, which primes BaseUrlInterceptor from
    // persisted settings (see SettingsRepositoryImpl.init).
    @Suppress("unused")
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicRefresh()
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
