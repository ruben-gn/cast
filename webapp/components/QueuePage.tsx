import type {FC} from 'hono/jsx'
import type {Episode} from '../types'

export const QueuePage: FC<{ episodes: Episode[] }> = ({episodes}) => (
    <div class="queue-page">
        <h1 class="queue-title">Up next</h1>
        {episodes.length === 0 ? (
            <p class="empty-message">Your queue is empty. Add episodes from a podcast.</p>
        ) : (
            <div id="queue-list">
                <QueueList episodes={episodes}/>
            </div>
        )}
    </div>
)

export const QueueList: FC<{ episodes: Episode[] }> = ({episodes}) => (
    <>
        {episodes.map((episode, i) => (
            <QueueRow key={episode.id} episode={episode} position={i + 1}/>
        ))}
        <span id="queue-badge" hx-swap-oob="true" class="queue-badge">{episodes.length || ''}</span>
    </>
)

const QueueRow: FC<{ episode: Episode; position: number }> = ({episode, position}) => (
    <div class="queue-row" id={`queue-row-${episode.id}`}>
        <span class="queue-position">{position}</span>
        <div class="queue-row-info">
            <span class="queue-row-title">{episode.title}</span>
            <div class="queue-row-extras">
                {episode.duration && <span class="episode-duration">{episode.duration}</span>}
            </div>
        </div>
        <div class="queue-row-actions">
            <button
                class="episode-play-btn episode-play-btn--small"
                data-id={episode.id}
                data-audio-url={episode.audioUrl}
                data-title={episode.title}
                onclick="playEpisode(this.dataset.id, this.dataset.audioUrl, this.dataset.title)"
                title={`Play ${episode.title}`}
            >
                <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
                    <path d="M8 5v14l11-7z"/>
                </svg>
            </button>
            <button
                class="queue-remove-btn"
                hx-delete={`/queue/${episode.id}`}
                hx-target="#queue-list"
                hx-swap="innerHTML"
                aria-label="Remove from queue"
            >
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
            </button>
        </div>
    </div>
)
