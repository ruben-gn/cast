package cast.android.ui.viewmodel

import cast.android.network.episode
import cast.android.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RecentViewModelTest {

    // StandardTestDispatcher leaves the init { load() } refresh queued, so uiState.value holds the
    // synchronously-seeded value when we assert.
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `seeds from cache as Success without Loading flash`() {
        val episodes = listOf(episode("1"))
        val vm = RecentViewModel(
            episodeRepository = FakeEpisodeRepository(cachedRecent = episodes),
            queueRepository = FakeQueueRepository(),
        )
        assertEquals(UiState.Success(episodes), vm.uiState.value)
    }

    @Test
    fun `cold start with no cache starts in Loading`() {
        val vm = RecentViewModel(
            episodeRepository = FakeEpisodeRepository(cachedRecent = null),
            queueRepository = FakeQueueRepository(),
        )
        assertEquals(UiState.Loading, vm.uiState.value)
    }
}
