import type {FC} from 'hono/jsx'
import type {Episode, Podcast} from '../types'
import {EpisodeItem} from './EpisodeItem'

export const PodcastDetail: FC<{ podcast: Podcast; episodes: Episode[] }> = ({podcast, episodes}) => (
    <div class="podcast-detail">
        <a
            class="back-link"
            hx-get="/podcasts"
            hx-target="#content-container"
            hx-swap="outerHTML"
            hx-push-url="true"
        >
            ← All podcasts
        </a>

        <div class="podcast-header">
            <img src={podcast.image} alt={podcast.name} class="podcast-cover"/>
            <div class="podcast-header-info">
                <h1 class="podcast-title">{podcast.name}</h1>
                <p class="podcast-subtitle">{episodes.length} episodes</p>
                <button
                    class="podcast-action-btn"
                    hx-post={`/api/podcasts/${podcast.id}/played`}
                    hx-swap="none"
                    {...{"hx-on:htmx:after-request": "if(event.detail.successful) markAllEpisodesPlayed()"}}
                    title="Mark all as played"
                >
                    Mark all as played
                </button>
            </div>
        </div>

        {episodes.length === 0 ? (
            <p class="empty-message">No episodes available.</p>
        ) : (
            episodes.map(episode => <EpisodeItem key={episode.id} episode={episode}/>)
        )}
    </div>
)
