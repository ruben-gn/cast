package podcast.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.w3c.dom.Element
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import javax.xml.parsers.DocumentBuilderFactory

private val log = KotlinLogging.logger {}

data class ImportResult(val imported: List<Podcast>, val failed: List<FailedFeed>)
data class FailedFeed(val url: String, val reason: String)

class ImportOpml(private val addFeed: AddFeed) {
    suspend operator fun invoke(opmlContent: ByteArray): ImportResult = coroutineScope {
        val urls = parseOpmlUrls(opmlContent)
        log.info { "Importing OPML: ${urls.size} feeds found." }

        val semaphore = Semaphore(4)
        val results = urls.map { url ->
            async {
                semaphore.withPermit { url to runCatching { addFeed(FeedUrl(url)) } }
            }
        }.awaitAll()

        val imported = mutableListOf<Podcast>()
        val failed = mutableListOf<FailedFeed>()
        for ((url, result) in results) {
            result.fold(
                onSuccess = { imported.add(it) },
                onFailure = { failed.add(FailedFeed(url, it.message ?: "Unknown error")) }
            )
        }
        log.info { "OPML import done: ${imported.size} imported, ${failed.size} failed." }
        ImportResult(imported, failed)
    }
}

private fun parseOpmlUrls(content: ByteArray): List<String> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isExpandEntityReferences = false
    }
    val doc = factory.newDocumentBuilder().parse(content.inputStream())
    val outlines = doc.getElementsByTagName("outline")
    return (0 until outlines.length)
        .mapNotNull { (outlines.item(it) as? Element)?.getAttribute("xmlUrl")?.takeIf { it.isNotBlank() } }
}
