package cast.android.di

import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.domain.repository.QueueRepository
import cast.android.domain.repository.SettingsRepository
import cast.android.domain.repository.impl.EpisodeRepositoryImpl
import cast.android.domain.repository.impl.PodcastRepositoryImpl
import cast.android.domain.repository.impl.QueueRepositoryImpl
import cast.android.domain.repository.impl.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindPodcastRepository(impl: PodcastRepositoryImpl): PodcastRepository

    @Binds @Singleton
    abstract fun bindEpisodeRepository(impl: EpisodeRepositoryImpl): EpisodeRepository

    @Binds @Singleton
    abstract fun bindQueueRepository(impl: QueueRepositoryImpl): QueueRepository
}
