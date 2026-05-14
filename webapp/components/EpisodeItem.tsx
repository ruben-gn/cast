import type {FC} from 'hono/jsx'
import type {Episode} from '../types'

export const EpisodeItem: FC<{ episode: Episode }> = ({episode}) => {
    const hasDescription = episode.description.trim() !== ''
    const showProgress = episode.progressMs > 0 && !!episode.durationMs && episode.durationMs > 0

    return (
        <div class="episode-item">
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

            <div class="episode-header episode-header--static">
                <EpisodeRow episode={episode}/>
            </div>

            {showProgress && (
                <progress
                    class="episode-progress"
                    value={episode.progressMs}
                    max={episode.durationMs!}
                />
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
    if (weeks < 5) return `${weeks}w ago`
    const months = Math.floor(days / 30)
    if (months < 12) return `${months}mo ago`
    return `${Math.floor(days / 365)}y ago`
}

const EpisodeRow: FC<{ episode: Episode }> = ({episode}) => {
    const pubDate = relativeTime(episode.publishedAt)
    return (
        <div class="episode-row">
            <span class="episode-title">{episode.title}</span>
            <div class="episode-extras">
                {pubDate && (
                    <span class="episode-pubdate">{pubDate}</span>
                )}
                {episode.duration && (
                    <span class="episode-duration">{episode.duration}</span>
                )}
                {episode.played && (
                    <span class="episode-played-badge">Played</span>
                )}
            </div>
        </div>
    )
}
