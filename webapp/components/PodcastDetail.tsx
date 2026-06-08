import type {FC} from 'hono/jsx'
import type {Episode, Podcast} from '../types'
import {EpisodeItem} from './EpisodeItem'

export const PodcastDetail: FC<{ podcast: Podcast; episodes: Episode[] }> = ({podcast, episodes}) => (
    <div class="podcast-detail">
        <a class="back-link" onclick="history.back()" style="cursor:pointer">← Back</a>

        <div class="podcast-header">
            <img src={podcast.image} alt={podcast.name} class="podcast-cover" loading="lazy"/>
            <div class="podcast-header-info">
                <h1 class="podcast-title">{podcast.name}</h1>
                <p class="podcast-subtitle">{episodes.length} episodes</p>
                <div class="podcast-actions">
                    <button
                        class="podcast-action-btn"
                        hx-post={`/api/podcasts/${podcast.id}/played`}
                        hx-swap="none"
                        {...{"hx-on:htmx:after-request": "if(event.detail.successful) markAllEpisodesPlayed()"}}
                        title="Mark all as played"
                    >
                        Mark all as played
                    </button>
                    {podcast.listening ? (
                        <button
                            class="podcast-action-btn"
                            hx-delete={`/podcasts/${podcast.id}/listening`}
                            hx-target="#content-container"
                            hx-swap="outerHTML"
                        >
                            Stop listening
                        </button>
                    ) : (
                        <button
                            class="podcast-action-btn"
                            hx-post={`/podcasts/${podcast.id}/listening`}
                            hx-target="#content-container"
                            hx-swap="outerHTML"
                        >
                            Start listening
                        </button>
                    )}
                </div>
            </div>
        </div>

        {episodes.length === 0 ? (
            <p class="empty-message">No episodes available.</p>
        ) : (
            episodes.map(episode => <EpisodeItem key={episode.id} episode={episode}/>)
        )}
    </div>
)
