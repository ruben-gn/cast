package podcast.infrastructure

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import podcast.core.port.FeedInfo
import podcast.core.port.FeedInfoProvider

class RssFeedInfoProvider(
    private val httpClient: HttpClient
) : FeedInfoProvider {
    override suspend fun fetch(url: String): FeedInfo =
        httpClient
            .get(url).bodyAsText()
            .let(::parseXml)
            .let(::toFeedInfo)
}

private fun toFeedInfo(channel: RssChannel) =
    FeedInfo(
        title = channel.title,
        description = channel.description,
        image = channel.itunesImage?.href ?: channel.image?.url ?: ""
    )

private fun parseXml(xml: String) = xmlParser.decodeFromString<RssEnvelope>(xml).channel

@OptIn(ExperimentalXmlUtilApi::class)
private val xmlParser = XML {
    policy = DefaultXmlSerializationPolicy.Builder().apply {
        ignoreUnknownChildren()
        autoPolymorphic = true
    }.build()

    repairNamespaces = true
}

@Serializable
@XmlSerialName("rss", "", "")
data class RssEnvelope(
    val channel: RssChannel
)

@Serializable
@XmlSerialName("channel", "", "")
data class RssChannel(
    @XmlElement(true) val title: String,
    @XmlElement(true) val description: String = "",
    @XmlSerialName("image", "http://www.itunes.com/dtds/podcast-1.0.dtd", "itunes")
    val itunesImage: ItunesImage? = null,
    val image: RssImage? = null
)

@Serializable
data class ItunesImage(
    @XmlSerialName("href", "", "") val href: String
)

@Serializable
@XmlSerialName("image", "", "")
data class RssImage(
    @XmlElement(true) val url: String
)