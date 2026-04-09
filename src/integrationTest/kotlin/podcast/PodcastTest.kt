package podcast

import common
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import podcast.infrastructure.web.AddPodcastRequest
import podcast.infrastructure.web.PodcastDto

class PodcastTest : StringSpec({
    "should save and retrieve podcasts through the full stack" {
        testApplication {
            application {
                common()
                podcastModule()
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val requests = listOf(AddPodcastRequest("my-podcast-url"), AddPodcastRequest("another-podcast-url"))

            requests.forEach { request ->
                client.post("/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }

            val response = client.get("/podcasts") {
                contentType(ContentType.Application.Json)
            }

            val podcasts = response.body<List<PodcastDto>>()
            podcasts.size shouldBe requests.size
            podcasts.map { it.url } shouldBe requests.map { it.url }
        }
    }
})