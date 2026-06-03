import type {FC} from 'hono/jsx'
import type {Episode} from '../types'

function relativeTime(iso: string | null): string | null {
    if (!iso) return null
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
    if (days <= 0) return 'Today'
    if (days === 1) return 'Yesterday'
    if (days < 7) return `${days} days ago`
    const weeks = Math.floor(days / 7)
    if (weeks < 5) return `${weeks} ${weeks === 1 ? 'week' : 'weeks'} ago`
    const months = Math.floor(days / 30)
    if (months < 12) return `${months} ${months === 1 ? 'month' : 'months'} ago`
    const years = Math.floor(days / 365)
    return `${years} ${years === 1 ? 'year' : 'years'} ago`
}

export const EpisodeDetail: FC<{episode: Episode}> = ({episode}) => {
    const showProgress = episode.progressMs > 0 && !!episode.durationMs && episode.durationMs > 0
    const meta = [episode.podcastName, relativeTime(episode.publishedAt), episode.duration].filter(Boolean).join(' · ')
    return (
        <div class="episode-detail-page">
            <a class="back-link" onclick="history.back()" style="cursor:pointer">← Back</a>
            <div class="episode-detail-header">
                {episode.podcastImage && (
                    <img class="episode-detail-cover" src={episode.podcastImage} alt="" loading="lazy"/>
                )}
                <div class="episode-detail-info">
                    <h1 class="episode-detail-title">{episode.title}</h1>
                    {meta && <p class="episode-detail-meta">{meta}</p>}
                </div>
            </div>
            <div class="episode-detail-actions">
                <button
                    class="episode-play-btn"
                    data-id={episode.id}
                    data-audio-url={episode.audioUrl}
                    data-title={episode.title}
                    data-artwork={episode.podcastImage ?? ''}
                    data-podcast={episode.podcastName ?? ''}
                    onclick="playEpisode(this)"
                    title={`Play ${episode.title}`}
                >
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </button>
                <button
                    class="episode-queue-btn"
                    hx-post={`/queue/${encodeURIComponent(episode.id)}`}
                    hx-swap="none"
                    title="Add to queue"
                >
                    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
                        <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/>
                        <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
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
            {showProgress && (
                <div class="episode-progress-bar episode-detail-progress">
                    <div class="episode-progress-fill" style={`width:${Math.round(episode.progressMs / episode.durationMs! * 100)}%`}></div>
                </div>
            )}
            {episode.description.trim() !== '' && (
                <div class="episode-detail-description" dangerouslySetInnerHTML={{__html: episode.description.replace(/<a\s/gi, '<a target="_blank" rel="noopener noreferrer" ')}}/>
            )}
        </div>
    )
}
