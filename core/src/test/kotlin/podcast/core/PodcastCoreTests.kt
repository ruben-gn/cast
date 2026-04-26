package podcast.core

import fakes.TestClock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import podcast.core.models.FeedUrl
import podcast.core.models.PodcastId
import podcast.core.ports.EpisodeInfo
import podcast.core.ports.FeedInfo
import podcast.core.ports.FeedInfoProvider
import podcast.core.usecase.AddFeed
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListEpisodes
import podcast.core.usecase.ListPodcasts
import podcast.core.usecase.UpdateFeed
import podcast.core.usecase.UpdateFeeds
import podcast.fakes.FakePodcastCatalog
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PodcastCoreTests : DescribeSpec({
    val fixedInstant = Instant.parse("2026-04-10T10:00:00Z")
    val fixedClock = TestClock(fixedInstant)

    describe("Podcast Domain Hexagon") {
        lateinit var catalog: FakePodcastCatalog
        lateinit var stubFeedProvider: FeedInfoProvider

        lateinit var updateFeed: UpdateFeed
        lateinit var updateFeeds: UpdateFeeds
        lateinit var addFeed: AddFeed
        lateinit var listPodcasts: ListPodcasts
        lateinit var getPodcast: GetPodcast
        lateinit var listEpisodes: ListEpisodes

        beforeEach {
            catalog = FakePodcastCatalog()
            stubFeedProvider = FeedInfoProvider { url -> FeedInfo(title = "Show for ${url.value}", description = "Desc", image = "img.png", url = url.value) }

            updateFeed = UpdateFeed(catalog, stubFeedProvider, fixedClock)
            updateFeeds = UpdateFeeds(catalog, updateFeed)
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
            listPodcasts = ListPodcasts(catalog)
            getPodcast = GetPodcast(catalog)
            listEpisodes = ListEpisodes(catalog)
        }

        it("should register a new podcast and make it available for listing and retrieval") {
            val url = FeedUrl("https://example.com/rss")

            val created = addFeed(url)
            created.name shouldBe "Show for ${url.value}"
            created.created shouldBe fixedInstant

            val all = listPodcasts()
            all shouldHaveSize 1
            all.first() shouldBe created

            val retrieved = getPodcast(created.id)
            retrieved shouldBe created
        }

        it("should not create duplicate entries for the same feed URL") {
            val url = FeedUrl("https://duplicate.com/rss")

            val first = addFeed(url)
            val second = addFeed(url)

            first.id shouldBe second.id
            listPodcasts() shouldHaveSize 1
        }

        it("should return null when retrieving a non-existent podcast") {
            getPodcast(PodcastId("non-existent-id")) shouldBe null
        }

        it("should map all episodes from the feed") {
            val episodeInfos = listOf(
                EpisodeInfo("episode-1-id", "Ep 1", "Desc 1", "https://cdn/ep1.mp3", 1.hours, Instant.parse("2026-01-01T00:00:00Z")),
                EpisodeInfo("episode-2-id", "Ep 2", "Desc 2", "https://cdn/ep2.mp3", 30.minutes, Instant.parse("2026-01-02T00:00:00Z"))
            )
            val url = "https://example.com/rss"

            stubFeedProvider = FeedInfoProvider { FeedInfo("Show", url, "Desc", "img.png", episodeInfos) }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)

            val podcast = addFeed(FeedUrl(url))
            val episodes = listEpisodes(podcast.id)

            episodes shouldHaveSize 2
            with(episodes[0]) {
                title shouldBe "Ep 1"
                audioUrl shouldBe "https://cdn/ep1.mp3"
                publishedAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
                duration shouldBe 1.hours
            }
            with(episodes[1]) {
                title shouldBe "Ep 2"
                duration shouldBe 30.minutes
            }
            episodes.map { it.id }.toSet() shouldHaveSize 2
        }

        it("should update podcast info and add new episodes when updating feed") {
            val url = FeedUrl("https://example.com/rss")
            val episode1 = EpisodeInfo("ep1", "Ep 1", "Desc 1", "url1", 30.minutes, fixedInstant.minusSeconds(3600))

            stubFeedProvider = FeedInfoProvider { FeedInfo("Initial Name", url.value, "Desc", "old.png", listOf(episode1)) }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
            val podcast = addFeed(url)

            // Advance time for the update
            fixedClock.tick(600.seconds)
            val updateInstant = fixedClock.instant()

            val episode2 = EpisodeInfo("ep2", "Ep 2", "Desc 2", "url2", 45.minutes, updateInstant)
            val newFeedInfo = FeedInfo("Updated Name", url.value, "Desc", "new.png", listOf(episode1, episode2))
            stubFeedProvider = FeedInfoProvider { newFeedInfo }
            updateFeed = UpdateFeed(catalog, stubFeedProvider, fixedClock)

            val updated = updateFeed(podcast)

            updated.name shouldBe "Updated Name"
            updated.image shouldBe "new.png"
            updated.updated shouldBe updateInstant

            val allEpisodes = listEpisodes(podcast.id)
            allEpisodes shouldHaveSize 2
            allEpisodes.map { it.id.value } shouldContainExactlyInAnyOrder listOf("ep1", "ep2")
        }

        it("should update all registered podcasts") {
            val url1 = FeedUrl("https://show1.com/rss")
            val url2 = FeedUrl("https://show2.com/rss")

            stubFeedProvider = FeedInfoProvider { url -> FeedInfo("Old Name ${url.value}", url.value, "Desc", "img.png") }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)

            addFeed(url1)
            addFeed(url2)

            stubFeedProvider = FeedInfoProvider { url -> FeedInfo("New Name ${url.value}", url.value, "Desc", "img.png") }
            updateFeed = UpdateFeed(catalog, stubFeedProvider, fixedClock)
            updateFeeds = UpdateFeeds(catalog, updateFeed)

            updateFeeds()

            listPodcasts().forEach { podcast ->
                podcast.name shouldBe "New Name ${podcast.url.value}"
            }
        }
    }
})
