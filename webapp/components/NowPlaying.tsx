import type {FC} from 'hono/jsx'

export const NowPlaying: FC = () => (
    <div class="now-playing-view" id="now-playing">
        <a class="back-link" onclick="history.back()" style="cursor:pointer">← Back</a>
        <div class="np-empty" id="np-empty">Nothing playing</div>
        <div class="np-body" id="np-bodyx">
            <img id="np-artwork" class="np-artwork" alt="" />
            <div class="np-meta">
                <span id="np-title" class="np-title"></span>
                <span id="np-podcast" class="np-podcast"></span>
            </div>
            <input type="range" id="np-scrubber" class="np-scrubber" min="0" max="1000" value="0" />
            <div class="np-times">
                <span id="np-elapsed">0:00</span>
                <span id="np-duration">0:00</span>
            </div>
            <div class="np-controls">
                <button class="player-ctrl-btn" onclick="seekBack(event)" aria-label="Back 15 seconds">
                    <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor"><path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/></svg>
                </button>
                <button id="np-playpause" class="np-playpause" onclick="togglePlayPause(event)" aria-label="Play or pause">
                    <svg viewBox="0 0 24 24" width="34" height="34" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </button>
                <button class="player-ctrl-btn" onclick="seekForward(event)" aria-label="Forward 30 seconds">
                    <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor"><path d="M13 6v12l8.5-6L13 6zm-.5 6L4 6v12l8.5-6z"/></svg>
                </button>
            </div>
        </div>
    </div>
)
