package cast.android.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object PlaybackCacheModule {

    private const val MAX_STREAM_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    // LRU cache for streamed audio; safe to evict because the origin still has the bytes.
    @Provides
    @Singleton
    @StreamingCache
    fun provideStreamingCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_STREAM_CACHE_BYTES),
            databaseProvider,
        )

    // Pinned cache for downloaded episodes: never evicts, and lives in filesDir because the OS
    // may clear cacheDir under storage pressure — exactly when the offline copy matters most.
    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache =
        SimpleCache(
            File(context.filesDir, "downloads"),
            NoOpCacheEvictor(),
            databaseProvider,
        )

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        @DownloadCache downloadCache: SimpleCache,
    ): DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            DefaultDataSource.Factory(context),
            Executors.newFixedThreadPool(2),
        )

    // Playback reads the download cache first but never writes to it (only DownloadManager fills
    // it), then falls through to the streaming LRU cache, then the network.
    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        @ApplicationContext context: Context,
        @StreamingCache streamingCache: SimpleCache,
        @DownloadCache downloadCache: SimpleCache,
    ): CacheDataSource.Factory {
        val streamingFactory = CacheDataSource.Factory()
            .setCache(streamingCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streamingFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
