import type { FC } from 'hono/jsx'
import type { Episode } from '../types'

export const EpisodeItem: FC<{ episode: Episode }> = ({ episode }) => {
  const hasDescription = episode.description.trim() !== ''
  const toggleId = `tgl-${episode.id}`

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
          <path d="M8 5v14l11-7z" />
        </svg>
      </button>

      {hasDescription && (
        <input type="checkbox" id={toggleId} class="episode-toggle" />
      )}

      {hasDescription ? (
        <label for={toggleId} class="episode-header">
          <EpisodeRow episode={episode} />
        </label>
      ) : (
        <div class="episode-header episode-header--static">
          <EpisodeRow episode={episode} />
        </div>
      )}

      {hasDescription && (
        <>
          <div class="description-container">
            <div dangerouslySetInnerHTML={{ __html: episode.description }} />
            <div class="description-fade">
              <label class="show-more-btn" for={toggleId}>Show more</label>
            </div>
          </div>
          <label class="show-less-btn" for={toggleId}>Show less</label>
        </>
      )}
    </div>
  )
}

const EpisodeRow: FC<{ episode: Episode }> = ({ episode }) => (
  <div class="episode-row">
    <span class="episode-title">{episode.title}</span>
    <div class="episode-extras">
      {episode.duration && (
        <span class="episode-duration">{episode.duration}</span>
      )}
    </div>
  </div>
)
