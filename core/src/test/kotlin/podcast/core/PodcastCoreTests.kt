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
import podcast.core.usecase.*
import podcast.core.usecase.StartListening
import podcast.core.usecase.StopListening
import shared.model.EpisodeId
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
        lateinit var deletePodcast: DeletePodcast
        lateinit var listPodcasts: ListPodcasts
        lateinit var getPodcast: GetPodcast
        lateinit var findEpisode: FindEpisode
        lateinit var listEpisodes: ListEpisodes

        beforeEach {
            catalog = FakePodcastCatalog()
            stubFeedProvider = FeedInfoProvider { url -> FeedInfo(title = "Show for ${url.value}", description = "Desc", image = "img.png", url = url.value) }

            updateFeed = UpdateFeed(catalog, stubFeedProvider, fixedClock)
            updateFeeds = UpdateFeeds(catalog, updateFeed)
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
            deletePodcast = DeletePodcast(catalog)
            listPodcasts = ListPodcasts(catalog)
            getPodcast = GetPodcast(catalog)
            findEpisode = FindEpisode(catalog)
            listEpisodes = ListEpisodes(catalog)
        }

        it("registers a new podcast and makes it available for listing and retrieval") {
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

        it("does not create duplicate entries for the same feed URL") {
            val url = FeedUrl("https://duplicate.com/rss")

            val first = addFeed(url)
            val second = addFeed(url)

            first.id shouldBe second.id
            listPodcasts() shouldHaveSize 1
        }

        it("returns null when retrieving a non-existent podcast") {
            getPodcast(PodcastId("non-existent-id")) shouldBe null
        }

        it("deletes a podcast and its episodes from the catalog") {
            val episodeInfo = EpisodeInfo("ep-1", "Ep 1", "Desc", "https://cdn/ep1.mp3", null, fixedInstant)
            stubFeedProvider = FeedInfoProvider { url -> FeedInfo("Show", url.value, "Desc", "img.png", listOf(episodeInfo)) }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
            val podcast = addFeed(FeedUrl("https://example.com/rss"))
            val episodeId = listEpisodes(podcast.id).first().id

            deletePodcast(podcast.id) shouldBe true

            listPodcasts() shouldHaveSize 0
            getPodcast(podcast.id) shouldBe null
            findEpisode(episodeId) shouldBe null
        }

        it("returns false when deleting a podcast that does not exist") {
            deletePodcast(PodcastId("non-existent-id")) shouldBe false
        }

        it("finds an episode by id") {
            val episodeInfo = EpisodeInfo("ep-42", "The Answer", "Desc", "https://cdn/ep42.mp3", null, fixedInstant)
            stubFeedProvider = FeedInfoProvider { url -> FeedInfo("Show", url.value, "Desc", "img.png", listOf(episodeInfo)) }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
            val podcast = addFeed(FeedUrl("https://example.com/rss"))
            val episodeId = listEpisodes(podcast.id).first().id

            val found = findEpisode(episodeId)

            found?.title shouldBe "The Answer"
        }

        it("returns null when finding an episode that does not exist") {
            findEpisode(EpisodeId("non-existent-episode")) shouldBe null
        }

        it("maps all episodes from the feed") {
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
            with(episodes.first { it.title == "Ep 1" }) {
                audioUrl shouldBe "https://cdn/ep1.mp3"
                publishedAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
                duration shouldBe 1.hours
            }
            with(episodes.first { it.title == "Ep 2" }) {
                duration shouldBe 30.minutes
            }
            episodes.map { it.id }.toSet() shouldHaveSize 2
        }

        it("lists episodes newest first, including ones added by a later feed refresh") {
            val url = FeedUrl("https://example.com/rss")
            val old = EpisodeInfo("old", "Old", "Desc", "old.mp3", null, Instant.parse("2024-01-01T00:00:00Z"))
            val mid = EpisodeInfo("mid", "Mid", "Desc", "mid.mp3", null, Instant.parse("2025-01-01T00:00:00Z"))

            stubFeedProvider = FeedInfoProvider { FeedInfo("Show", url.value, "Desc", "img.png", listOf(mid, old)) }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
            val podcast = addFeed(url)

            // A later refresh appends a brand-new episode.
            val new = EpisodeInfo("new", "New", "Desc", "new.mp3", null, Instant.parse("2026-01-01T00:00:00Z"))
            stubFeedProvider = FeedInfoProvider { FeedInfo("Show", url.value, "Desc", "img.png", listOf(new, mid, old)) }
            updateFeed = UpdateFeed(catalog, stubFeedProvider, fixedClock)
            updateFeed(podcast).getOrThrow()

            listEpisodes(podcast.id).map { it.title } shouldBe listOf("New", "Mid", "Old")
        }

        it("updates podcast info and adds new episodes when updating the feed") {
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

            val (updated, episodes) = updateFeed(podcast).getOrThrow()

            updated.name shouldBe "Updated Name"
            updated.image shouldBe "new.png"
            updated.updated shouldBe updateInstant

            val allEpisodes = listEpisodes(podcast.id)
            allEpisodes shouldContainExactlyInAnyOrder episodes
        }

        it("updates all registered podcasts") {
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

        it("new podcast defaults to listening") {
            val podcast = addFeed(FeedUrl("https://example.com/rss"))
            podcast.listening shouldBe true
        }

        it("lists listening podcasts before non-listening ones") {
            val url1 = FeedUrl("https://show1.com/rss")
            val url2 = FeedUrl("https://show2.com/rss")
            stubFeedProvider = FeedInfoProvider { url -> FeedInfo(title = "Show for ${url.value}", description = "Desc", image = "img.png", url = url.value) }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
            val p1 = addFeed(url1)
            val p2 = addFeed(url2)

            val startListening = StartListening(catalog)
            val stopListening = StopListening(catalog)
            stopListening(p1.id)
            startListening(p2.id)

            val ordered = listPodcasts()
            ordered.first().id shouldBe p2.id
            ordered.last().id shouldBe p1.id
        }

        it("StartListening returns false for unknown podcast") {
            StartListening(catalog)(PodcastId("nope")) shouldBe false
        }

        it("StopListening returns false for unknown podcast") {
            StopListening(catalog)(PodcastId("nope")) shouldBe false
        }

        it("continues updating other podcasts when one feed update fails") {
            val failingUrl = FeedUrl("https://failing-show.com/rss")
            val successfulUrl = FeedUrl("https://successful-show.com/rss")

            stubFeedProvider = FeedInfoProvider { url -> FeedInfo("Old Name ${url.value}", url.value, "Desc", "img.png") }
            addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)

            addFeed(failingUrl)
            addFeed(successfulUrl)

            stubFeedProvider = FeedInfoProvider { url ->
                if (url == failingUrl) {
                    error("Feed unavailable")
                }

                FeedInfo("New Name ${url.value}", url.value, "Desc", "img.png")
            }
            updateFeed = UpdateFeed(catalog, stubFeedProvider, fixedClock)
            updateFeeds = UpdateFeeds(catalog, updateFeed)

            updateFeeds()

            val podcasts = listPodcasts()

            podcasts.find { it.url == failingUrl }!!.name shouldBe "Old Name ${failingUrl.value}"
            podcasts.find { it.url == successfulUrl }!!.name shouldBe "New Name ${successfulUrl.value}"
        }
    }
})
