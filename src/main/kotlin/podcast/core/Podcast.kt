package grootnibbel.ink.podcast.core

data class Podcast(
    val id: String,
    val url: String,
    val name: String,
    val image: String
)

interface PodcastPersistence {
    fun save(podcast: Podcast)
    fun findAll(): List<Podcast>
}