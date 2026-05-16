package podcast.adapters.rss

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import podcast.core.PodcastException
import podcast.core.models.FeedUrl
import podcast.core.ports.EpisodeInfo
import podcast.core.ports.FeedInfo
import podcast.core.ports.FeedInfoProvider
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val pubDateFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss Z", Locale.ENGLISH)
    .withResolverStyle(ResolverStyle.LENIENT)
private val weekdayPrefix = Regex("^[A-Za-z]{3},\\s*")

class RssFeedInfoProvider(
    private val httpClient: HttpClient,
    private val clock: Clock
) : FeedInfoProvider {
    override suspend fun fetch(url: FeedUrl): FeedInfo = try {
        val xml = httpClient.get(url.value).bodyAsText()
        val feed = parseXml(xml)
        toFeedInfo(clock, feed, url)
    } catch (e: Exception) {
        throw PodcastException.FeedFetchFailed(url, e)
    }
}

private fun toFeedInfo(clock: Clock, channel: RssChannel, url: FeedUrl) =
    FeedInfo(
        title = channel.title,
        description = channel.description,
        image = channel.itunesImage?.href ?: channel.image?.url ?: "",
        url = url.value,
        episodes = channel.items.map { item ->
            val audioUrl = item.enclosure?.url ?: ""
            EpisodeInfo(
                id = item.guid ?: audioUrl,
                title = item.title,
                description = item.description,
                audioUrl = audioUrl,
                duration = parseDuration(item.duration),
                publishedAt = item.pubDate.takeIf { it.isNotBlank() }?.trim()?.let {
                    runCatching { ZonedDateTime.parse(weekdayPrefix.replace(it, ""), pubDateFormatter).toInstant() }.getOrNull()
                } ?: clock.instant()
            )
        }
    )

private fun parseDuration(raw: String?): Duration? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val trimmed = raw.trim()
        val totalSeconds = if (":" in trimmed) {
            val parts = trimmed.split(":")
            when (parts.size) {
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> return null
            }
        } else {
            trimmed.toLong()
        }
        totalSeconds.seconds
    }.getOrNull()
}

private fun parseXml(xml: String) = xmlParser.decodeFromString<RssEnvelope>(xml).channel

@OptIn(ExperimentalXmlUtilApi::class)
private val xmlParser = XML {
    defaultPolicy {
        ignoreUnknownChildren()
    }
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
    @XmlSerialName("image", "", "")
    val image: RssImage? = null,
    @XmlSerialName("item", "", "")
    val items: List<RssItem> = emptyList()
)

@Serializable
data class ItunesImage(
    val href: String
)

@Serializable
@XmlSerialName("image", "", "")
data class RssImage(
    @XmlElement(true) val url: String
)

@Serializable
@XmlSerialName("item", "", "")
data class RssItem(
    @XmlElement(true) val title: String = "",
    @XmlElement(true) val description: String = "",
    val enclosure: RssEnclosure? = null,
    @XmlElement(true) val pubDate: String = "",
    @XmlSerialName("duration", "http://www.itunes.com/dtds/podcast-1.0.dtd", "itunes")
    @XmlElement(true) val duration: String? = null,
    @XmlElement(true) val guid: String? = null
)

@Serializable
@XmlSerialName("enclosure", "", "")
data class RssEnclosure(
    val url: String = "",
    val length: String = "",
    val type: String = ""
)
