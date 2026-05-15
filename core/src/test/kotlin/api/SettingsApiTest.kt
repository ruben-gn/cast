package api

import cast.api.SettingsDto
import installCommon
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import settings.installSettingsModule
import settings.fakes.FakeSettingsPersistence

class SettingsApiTest : DescribeSpec({
    describe("Settings API") {
        fun testApp(block: suspend ApplicationTestBuilder.(client: io.ktor.client.HttpClient) -> Unit) {
            testApplication {
                application {
                    installCommon()
                    installSettingsModule(persistence = FakeSettingsPersistence())
                    routing { route("/api/settings") { settingsApi(dependencies) } }
                }
                block(createClient { install(ContentNegotiation) { json() } })
            }
        }

        it("GET returns default settings") {
            testApp { client ->
                val settings = client.get("/api/settings").body<SettingsDto>()
                settings.hidePlayed shouldBe false
            }
        }

        it("PUT updates settings and GET reflects the change") {
            testApp { client ->
                client.put("/api/settings") {
                    contentType(ContentType.Application.Json)
                    setBody(SettingsDto(hidePlayed = true))
                }.status shouldBe HttpStatusCode.NoContent

                val settings = client.get("/api/settings").body<SettingsDto>()
                settings.hidePlayed shouldBe true
            }
        }

        it("PUT can toggle hidePlayed back to false") {
            testApp { client ->
                client.put("/api/settings") {
                    contentType(ContentType.Application.Json)
                    setBody(SettingsDto(hidePlayed = true))
                }
                client.put("/api/settings") {
                    contentType(ContentType.Application.Json)
                    setBody(SettingsDto(hidePlayed = false))
                }
                client.get("/api/settings").body<SettingsDto>().hidePlayed shouldBe false
            }
        }
    }
})
