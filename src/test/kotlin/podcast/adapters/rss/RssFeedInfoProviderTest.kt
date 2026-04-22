package podcast.adapters.rss

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import podcast.core.model.FeedUrl

class RssFeedInfoProviderTest : DescribeSpec({

    fun providerWithResponse(url: String, xml: String): RssFeedInfoProvider {
        val engine = MockEngine { request ->
            if (request.url.toString() == url) {
                respond(
                    content = xml,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml")
                )
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }
        return RssFeedInfoProvider(HttpClient(engine))
    }

    describe("RssFeedInfoProvider") {

        describe("image selection") {
            it("should use itunes:image as primary image source") {
                val url = "https://example.com/itunes.xml"
                val xml = """
                    <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                        <channel>
                            <title>iTunes Show</title>
                            <image><url>https://example.com/standard.png</url></image>
                            <itunes:image href="https://example.com/itunes.png"/>
                        </channel>
                    </rss>
                """.trimIndent()

                providerWithResponse(url, xml).fetch(FeedUrl(url)).image shouldBe "https://example.com/itunes.png"
            }

            it("should fall back to standard rss image if itunes image is missing") {
                val url = "https://example.com/standard.xml"
                val xml = """
                    <rss>
                        <channel>
                            <title>Standard Show</title>
                            <image><url>https://example.com/standard.png</url></image>
                        </channel>
                    </rss>
                """.trimIndent()

                providerWithResponse(url, xml).fetch(FeedUrl(url)).image shouldBe "https://example.com/standard.png"
            }

            it("should use empty string if no image is found") {
                val url = "https://example.com/no-image.xml"
                val xml = """
                    <rss>
                        <channel>
                            <title>No Image Show</title>
                        </channel>
                    </rss>
                """.trimIndent()

                providerWithResponse(url, xml).fetch(FeedUrl(url)).image shouldBe ""
            }
        }

        describe("episode mapping") {
            it("should map episode fields from feed items") {
                val url = "https://example.com/episodes.xml"
                val xml = """
                    <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                        <channel>
                            <title>Show</title>
                            <item>
                                <title>Episode 1</title>
                                <description>About ep 1</description>
                                <enclosure url="https://cdn/ep1.mp3" length="12345" type="audio/mpeg"/>
                                <pubDate>Tue, 01 Jan 2026 10:00:00 +0000</pubDate>
                                <itunes:duration>01:00:00</itunes:duration>
                            </item>
                            <item>
                                <title>Episode 2</title>
                                <description>About ep 2</description>
                                <enclosure url="https://cdn/ep2.mp3" length="6789" type="audio/mpeg"/>
                                <pubDate>Wed, 02 Jan 2026 10:00:00 +0000</pubDate>
                                <itunes:duration>00:30:00</itunes:duration>
                            </item>
                        </channel>
                    </rss>
                """.trimIndent()

                val feedInfo = providerWithResponse(url, xml).fetch(FeedUrl(url))

                feedInfo.episodes shouldHaveSize 2
                with(feedInfo.episodes[0]) {
                    title shouldBe "Episode 1"
                    description shouldBe "About ep 1"
                    audioUrl shouldBe "https://cdn/ep1.mp3"
                    duration shouldBe 1.hours
                    publishedAt shouldBe Instant.parse("2026-01-01T10:00:00Z")
                }
                with(feedInfo.episodes[1]) {
                    title shouldBe "Episode 2"
                    audioUrl shouldBe "https://cdn/ep2.mp3"
                    duration shouldBe 30.minutes
                }
            }

            it("should use empty string for audio url when enclosure is missing") {
                val url = "https://example.com/no-enclosure.xml"
                val xml = """
                    <rss>
                        <channel>
                            <title>Show</title>
                            <item>
                                <title>Episode without audio</title>
                            </item>
                        </channel>
                    </rss>
                """.trimIndent()

                val episode = providerWithResponse(url, xml).fetch(FeedUrl(url)).episodes.first()
                episode.audioUrl shouldBe ""
            }

            it("should set publishedAt to null when pubDate is missing or malformed") {
                val url = "https://example.com/bad-dates.xml"
                val xml = """
                    <rss>
                        <channel>
                            <title>Show</title>
                            <item>
                                <title>No date</title>
                            </item>
                            <item>
                                <title>Bad date</title>
                                <pubDate>not-a-date</pubDate>
                            </item>
                        </channel>
                    </rss>
                """.trimIndent()

                val episodes = providerWithResponse(url, xml).fetch(FeedUrl(url)).episodes
                episodes[0].publishedAt shouldBe null
                episodes[1].publishedAt shouldBe null
            }
        }
    }
})
