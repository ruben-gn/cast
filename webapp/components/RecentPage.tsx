import type {FC} from 'hono/jsx'
import type {Episode} from '../types'
import {EpisodeItem} from './EpisodeItem'

export const RecentPage: FC<{episodes: Episode[]}> = ({episodes}) => (
    <div class="recent-page">
        <h1 class="page-title">Recent</h1>
        {episodes.length === 0 ? (
            <div class="empty-state">
                <div class="empty-state-icon">🎧</div>
                <p class="empty-state-title">All caught up</p>
                <p class="empty-state-body">No new unplayed episodes from the last two weeks.</p>
            </div>
        ) : (
            episodes.map(ep => <EpisodeItem key={ep.id} episode={ep}/>)
        )}
    </div>
)
