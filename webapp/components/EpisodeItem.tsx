import type {FC} from 'hono/jsx'
import type {Episode} from '../types'

export const EpisodeItem: FC<{ episode: Episode }> = ({episode}) => {
    const hasDescription = episode.description.trim() !== ''
    const showProgress = episode.progressMs > 0 && !!episode.durationMs && episode.durationMs > 0

    return (
        <div class={`episode-item${episode.played ? ' is-played' : ''}`}>
            {episode.podcastName && (
                <span class="episode-podcast">
                    {episode.podcastImage && (
                        <img class="episode-podcast-img" src={episode.podcastImage} alt="" width="16" height="16"/>
                    )}
                    {episode.podcastName}
                </span>
            )}
            <div class="episode-main">
                <div class="episode-header episode-header--static">
                    <EpisodeRow episode={episode}/>
                </div>
                <div class="episode-actions">
                    <button
                        class="episode-queue-btn"
                        hx-post={`/queue/${episode.id}`}
                        hx-swap="none"
                        title="Add to queue"
                    >
                        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
                            <line x1="8" y1="6" x2="21" y2="6"/>
                            <line x1="8" y1="12" x2="21" y2="12"/>
                            <line x1="8" y1="18" x2="21" y2="18"/>
                            <line x1="3" y1="6" x2="3.01" y2="6"/>
                            <line x1="3" y1="12" x2="3.01" y2="12"/>
                            <line x1="3" y1="18" x2="3.01" y2="18"/>
                        </svg>
                    </button>
                    <button
                        class="episode-play-btn"
                        data-id={episode.id}
                        data-audio-url={episode.audioUrl}
                        data-title={episode.title}
                        onclick="playEpisode(this.dataset.id, this.dataset.audioUrl, this.dataset.title)"
                        title={`Play ${episode.title}`}
                    >
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                            <path d="M8 5v14l11-7z"/>
                        </svg>
                    </button>
                    <button
                        class={`episode-played-btn${episode.played ? ' is-played' : ''}`}
                        data-id={episode.id}
                        data-played={episode.played ? 'true' : 'false'}
                        onclick="togglePlayed(this)"
                        title={episode.played ? 'Mark as unplayed' : 'Mark as played'}
                    >
                        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="20 6 9 17 4 12"/>
                        </svg>
                    </button>
                </div>
            </div>

            {showProgress && (
                <div class="episode-progress-bar">
                    <div class="episode-progress-fill" style={`width:${Math.round(episode.progressMs / episode.durationMs! * 100)}%`}></div>
                </div>
            )}

            {hasDescription && (
                <details class="episode-description-details">
                    <summary class="episode-description-summary">Show more</summary>
                    <div class="episode-description-body" dangerouslySetInnerHTML={{__html: episode.description}}/>
                </details>
            )}
        </div>
    )
}

function relativeTime(iso: string | null): string | null {
    if (!iso) return null
    const diffMs = Date.now() - new Date(iso).getTime()
    const days = Math.floor(diffMs / 86_400_000)
    if (days === 0) return 'Today'
    if (days === 1) return 'Yesterday'
    if (days < 7) return `${days} days ago`
    const weeks = Math.floor(days / 7)
    if (weeks < 5) return `${weeks} ${weeks === 1 ? 'week' : 'weeks'} ago`
    const months = Math.floor(days / 30)
    if (months < 12) return `${months} ${months === 1 ? 'month' : 'months'} ago`
    const years = Math.floor(days / 365)
    return `${years} ${years === 1 ? 'year' : 'years'} ago`
}

const EpisodeRow: FC<{ episode: Episode }> = ({episode}) => {
    const pubDate = relativeTime(episode.publishedAt)
    return (
        <div class="episode-row">
            <span
                class="episode-title"
                title={episode.title}
                hx-get={`/episodes/${episode.id}`}
                hx-target="#content-container"
                hx-swap="outerHTML"
                hx-push-url="true"
                style="cursor:pointer"
            >{episode.title}</span>
            {(pubDate || episode.duration) && (
                <div class="episode-meta">
                    {pubDate && <span>{pubDate}</span>}
                    {pubDate && episode.duration && <span class="episode-meta-sep">·</span>}
                    {episode.duration && <span>{episode.duration}</span>}
                </div>
            )}
        </div>
    )
}
