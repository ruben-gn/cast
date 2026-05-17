import type {FC} from 'hono/jsx'
import type {Episode} from '../types'

export const EpisodeDetail: FC<{episode: Episode}> = ({episode}) => (
    <div class="episode-detail">
        <a
            class="back-link"
            hx-get="/"
            hx-target="#content-container"
            hx-swap="outerHTML"
            hx-push-url="true"
        >← Recent</a>

        {episode.podcastImage && (
            <div class="episode-detail-podcast">
                <img src={episode.podcastImage} alt={episode.podcastName ?? ''} class="episode-detail-podcast-img"/>
                <span class="episode-detail-podcast-name">{episode.podcastName}</span>
            </div>
        )}

        <h1 class="episode-detail-title">{episode.title}</h1>

        {(episode.publishedAt || episode.duration) && (
            <div class="episode-detail-meta">
                {episode.publishedAt && <span>{new Date(episode.publishedAt).toLocaleDateString('en', {year:'numeric',month:'long',day:'numeric'})}</span>}
                {episode.publishedAt && episode.duration && <span>·</span>}
                {episode.duration && <span>{episode.duration}</span>}
            </div>
        )}

        <div class="episode-detail-actions">
            <button
                class="episode-play-btn episode-detail-play-btn"
                data-id={episode.id}
                data-audio-url={episode.audioUrl}
                data-title={episode.title}
                onclick="playEpisode(this.dataset.id, this.dataset.audioUrl, this.dataset.title)"
            >
                <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                    <path d="M8 5v14l11-7z"/>
                </svg>
                Play
            </button>
            <button
                class="episode-queue-btn episode-detail-queue-btn"
                hx-post={`/queue/${episode.id}`}
                hx-swap="none"
                title="Add to queue"
            >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
                    <line x1="8" y1="6" x2="21" y2="6"/>
                    <line x1="8" y1="12" x2="21" y2="12"/>
                    <line x1="8" y1="18" x2="21" y2="18"/>
                    <line x1="3" y1="6" x2="3.01" y2="6"/>
                    <line x1="3" y1="12" x2="3.01" y2="12"/>
                    <line x1="3" y1="18" x2="3.01" y2="18"/>
                </svg>
                Add to queue
            </button>
        </div>

        {episode.description.trim() && (
            <div class="episode-detail-description" dangerouslySetInnerHTML={{__html: episode.description}}/>
        )}
    </div>
)
