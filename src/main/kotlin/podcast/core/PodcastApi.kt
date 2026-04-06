package grootnibbel.ink.podcast.core

import java.util.*

class PodcastApi(
    private val podcasts: PodcastPersistence
) {
    fun add(url: String): Podcast {
        val podcast = Podcast(
            id = UUID.randomUUID().toString(),
            url = url,
            name = "New Podcast",
            image = ""
        )
        podcasts.save(podcast)
        return podcast
    }

    fun get(): List<Podcast> = podcasts.findAll()
}