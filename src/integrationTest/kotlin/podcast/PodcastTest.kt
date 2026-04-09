package podcast

import grootnibbel.ink.podcast.podcastModule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*

class PodcastTest : StringSpec({
    "should save and retrieve podcasts through the full stack" {
        testApplication {
            application {
                podcastModule()
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val response = client.get("/podcasts/")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})