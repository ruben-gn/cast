package podcast.core

import fakes.TestClock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import podcast.core.ports.FeedInfo
import podcast.core.ports.FeedInfoProvider
import podcast.core.usecase.AddFeed
import podcast.core.usecase.ImportOpml
import podcast.core.usecase.UpdateFeed
import podcast.fakes.FakePodcastCatalog
import java.time.Instant

class ImportOpmlTest : DescribeSpec({
    val clock = TestClock(Instant.parse("2026-01-01T00:00:00Z"))

    lateinit var catalog: FakePodcastCatalog
    lateinit var importOpml: ImportOpml

    beforeEach {
        catalog = FakePodcastCatalog()
        val feedProvider = FeedInfoProvider { url ->
            FeedInfo(title = "Show: ${url.value}", description = "", image = "", url = url.value)
        }
        val updateFeed = UpdateFeed(catalog, feedProvider, clock)
        val addFeed = AddFeed(catalog, feedProvider, updateFeed, clock)
        importOpml = ImportOpml(addFeed)
    }

    describe("ImportOpml") {
        it("imports all xmlUrl feeds from a flat OPML") {
            val opml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="2.0">
                  <body>
                    <outline type="rss" xmlUrl="https://example.com/feed1.xml"/>
                    <outline type="rss" xmlUrl="https://example.com/feed2.xml"/>
                  </body>
                </opml>
            """.trimIndent().toByteArray()

            val result = importOpml(opml)

            result.imported shouldHaveSize 2
            result.failed shouldHaveSize 0
            result.imported.map { it.url.value } shouldBe listOf("https://example.com/feed1.xml", "https://example.com/feed2.xml")
        }

        it("imports feeds from nested outlines (category folders)") {
            val opml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="2.0">
                  <body>
                    <outline text="Tech">
                      <outline type="rss" xmlUrl="https://example.com/tech.xml"/>
                    </outline>
                    <outline text="Science">
                      <outline type="rss" xmlUrl="https://example.com/science.xml"/>
                    </outline>
                  </body>
                </opml>
            """.trimIndent().toByteArray()

            val result = importOpml(opml)

            result.imported shouldHaveSize 2
            result.failed shouldHaveSize 0
        }

        it("skips outlines without xmlUrl") {
            val opml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="2.0">
                  <body>
                    <outline text="Category"/>
                    <outline type="rss" xmlUrl="https://example.com/feed.xml"/>
                  </body>
                </opml>
            """.trimIndent().toByteArray()

            val result = importOpml(opml)

            result.imported shouldHaveSize 1
            result.failed shouldHaveSize 0
        }

        it("collects failures and continues importing the rest") {
            val failUrl = "https://bad.example.com/feed.xml"
            val catalog2 = FakePodcastCatalog()
            val failingProvider = FeedInfoProvider { url ->
                if (url.value == failUrl) error("Feed unreachable")
                FeedInfo(title = "Show: ${url.value}", description = "", image = "", url = url.value)
            }
            val updateFeed2 = UpdateFeed(catalog2, failingProvider, clock)
            val addFeed2 = AddFeed(catalog2, failingProvider, updateFeed2, clock)
            val importOpml2 = ImportOpml(addFeed2)

            val opml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="2.0">
                  <body>
                    <outline type="rss" xmlUrl="https://good.example.com/feed.xml"/>
                    <outline type="rss" xmlUrl="$failUrl"/>
                  </body>
                </opml>
            """.trimIndent().toByteArray()

            val result = importOpml2(opml)

            result.imported shouldHaveSize 1
            result.failed shouldHaveSize 1
            result.failed.first().url shouldBe failUrl
        }

        it("parses the real-world nested OPML and finds all 25 feeds") {
            val opml = ImportOpmlTest::class.java.getResourceAsStream("/podcasts_opml.xml")!!.readBytes()

            val result = importOpml(opml)

            result.imported shouldHaveSize 25
            result.failed shouldHaveSize 0
        }

        it("handles an empty OPML body") {
            val opml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="2.0">
                  <body/>
                </opml>
            """.trimIndent().toByteArray()

            val result = importOpml(opml)

            result.imported shouldHaveSize 0
            result.failed shouldHaveSize 0
        }
    }
})
