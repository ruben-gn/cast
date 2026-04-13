package podcast.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*

class RssFeedInfoProviderTest : StringSpec({
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

    "should use itunes:image as primary image source" {
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

        providerWithResponse(url, xml).fetch(url).image shouldBe "https://example.com/itunes.png"
    }

    "should fallback to standard rss image if itunes image is missing" {
        val url = "https://example.com/standard.xml"
        val xml = """
            <rss>
                <channel>
                    <title>Standard Show</title>
                    <image><url>https://example.com/standard.png</url></image>
                </channel>
            </rss>
        """.trimIndent()

        providerWithResponse(url, xml).fetch(url).image shouldBe "https://example.com/standard.png"
    }

    "should use empty string if no image is found" {
        val url = "https://example.com/no-image.xml"
        val xml = """
            <rss>
                <channel>
                    <title>No Image Show</title>
                </channel>
            </rss>
        """.trimIndent()

        providerWithResponse(url, xml).fetch(url).image shouldBe ""
    }
})
