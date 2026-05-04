import type {FC} from 'hono/jsx'
import type {Podcast} from '../types'

export const PodcastList: FC<{ podcasts: Podcast[] }> = ({podcasts}) => (
    <div class="podcast-list">
        {podcasts.length === 0 ? <EmptyState/> : <PodcastGrid podcasts={podcasts}/>}
    </div>
)

const EmptyState: FC = () => (
    <div class="empty-state">
        <div class="empty-state-icon">🎙</div>
        <h2 class="empty-state-title">No podcasts yet</h2>
        <p class="empty-state-body">
            Add your first podcast by clicking <strong>＋ Add podcast</strong> above.
        </p>
    </div>
)

const PodcastGrid: FC<{ podcasts: Podcast[] }> = ({podcasts}) => (
    <div class="podcast-grid">
        {podcasts.map(podcast => <PodcastCard key={podcast.id} podcast={podcast}/>)}
    </div>
)

const PodcastCard: FC<{ podcast: Podcast }> = ({podcast}) => (
    <div
        class="podcast-card-link"
        hx-get={`/podcasts/${podcast.id}`}
        hx-target="#content-container"
        hx-swap="outerHTML"
        hx-push-url="true"
    >
        <div class="podcast-card">
            <img src={podcast.image} alt={podcast.name} class="podcast-card-img"/>
            <div class="podcast-card-info">
                <p class="podcast-card-name">{podcast.name}</p>
            </div>
        </div>
    </div>
)
