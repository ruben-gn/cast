package podcast.core

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import podcast.core.model.FeedUrl
import podcast.core.model.PodcastId
import podcast.core.port.EpisodeInfo
import podcast.core.port.FeedInfo
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import podcast.fakes.FakeEpisodePersistence
import podcast.fakes.FakePodcastPersistence
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PodcastDomainTest : DescribeSpec({
    val fixedInstant = Instant.parse("2026-04-10T10:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    describe("Podcast Domain Hexagon") {
        lateinit var persistence: PodcastPersistence
        lateinit var episodePersistence: FakeEpisodePersistence
        lateinit var stubFeedProvider: FeedInfoProvider

        lateinit var addFeed: AddFeed
        lateinit var listPodcasts: ListPodcasts
        lateinit var getPodcast: GetPodcast
        lateinit var listEpisodes: ListEpisodes

        beforeEach {
            persistence = FakePodcastPersistence()
            episodePersistence = FakeEpisodePersistence()
            stubFeedProvider = FeedInfoProvider { url -> FeedInfo(title = "Show for ${url.value}", description = "Desc", image = "img.png") }

            addFeed = AddFeed(persistence, episodePersistence, stubFeedProvider, fixedClock)
            listPodcasts = ListPodcasts(persistence)
            getPodcast = GetPodcast(persistence)
            listEpisodes = ListEpisodes(episodePersistence)
        }

        it("should register a new podcast and make it available for listing and retrieval") {
            val url = FeedUrl("https://example.com/rss")

            val created = addFeed(url)
            created.name shouldBe "Show for ${url.value}"
            created.createdAt shouldBe fixedInstant

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
                EpisodeInfo("Ep 1", "Desc 1", "https://cdn/ep1.mp3", 1.hours, Instant.parse("2026-01-01T00:00:00Z")),
                EpisodeInfo("Ep 2", "Desc 2", "https://cdn/ep2.mp3", 30.minutes, Instant.parse("2026-01-02T00:00:00Z"))
            )
            stubFeedProvider = FeedInfoProvider { FeedInfo("Show", "Desc", "img.png", episodeInfos) }
            addFeed = AddFeed(persistence, episodePersistence, stubFeedProvider, fixedClock)

            val podcast = addFeed(FeedUrl("https://example.com/rss"))
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
    }
})
