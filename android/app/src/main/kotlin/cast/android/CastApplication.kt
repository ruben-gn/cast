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
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CastApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

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

    // Coil's default OkHttp client sends "User-Agent: okhttp/x.y", which some podcast image CDNs
    // (e.g. buzzsprout storage) reject with a 403 — the art then loads in the browser/webapp but not
    // here. Send a neutral User-Agent so all AsyncImage loads succeed.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .addInterceptor { chain ->
                                    chain.proceed(
                                        chain.request().newBuilder()
                                            .header("User-Agent", "CastPodcast/1.0")
                                            .build(),
                                    )
                                }
                                .build()
                        },
                    ),
                )
            }
            .build()

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
