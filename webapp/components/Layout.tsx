import type {Child, FC} from 'hono/jsx'
import { AddFeedModal } from './AddFeedModal'

export const Layout: FC<{ title: string, children: Child }> = ({ title, children }) => (
  <html lang="en">
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      <link rel="preconnect" href="https://fonts.googleapis.com" />
      <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="anonymous" />
      <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;700&display=swap" />
      <link rel="stylesheet" href="/static/css/style.css" />
      <script src="https://unpkg.com/htmx.org@1.9.10"></script>
      <title>{title}</title>
    </head>
    <body id="main-body">
      <header class="app-header">
        <div class="app-header-left">
          <span class="app-logo">Cast</span>
          <nav class="app-nav">
            <div
              class="nav-link"
              data-path="/"
              hx-get="/"
              hx-target="#content-container"
              hx-swap="outerHTML"
              hx-push-url="true"
              hx-indicator="#nav-spinner"
            >Recent</div>
            <div
              class="nav-link"
              data-path="/podcasts"
              hx-get="/podcasts"
              hx-target="#content-container"
              hx-swap="outerHTML"
              hx-push-url="true"
              hx-indicator="#nav-spinner"
            >Catalog</div>
            <div
              class="nav-link"
              data-path="/queue"
              hx-get="/queue"
              hx-target="#content-container"
              hx-swap="outerHTML"
              hx-push-url="true"
              hx-indicator="#nav-spinner"
            >Queue<span id="queue-badge" class="queue-badge"></span></div>
            <div
              class="nav-link"
              data-path="/settings"
              hx-get="/settings"
              hx-target="#content-container"
              hx-swap="outerHTML"
              hx-push-url="true"
              hx-indicator="#nav-spinner"
            >Settings</div>
          </nav>
        </div>
        <button class="header-add-btn" onclick="document.getElementById('add-feed-modal').showModal()">
          ＋<span class="header-add-label"> Add podcast</span>
        </button>
      </header>
      <AddFeedModal />
      <div id="nav-spinner" class="htmx-indicator nav-spinner"></div>
      <div id="content-container">
        <div class="page-content">
          {children}
        </div>
      </div>
      <div id="player-bar">
        <div class="player-bar-main" hx-get="/now-playing" hx-target="#content-container" hx-swap="outerHTML" hx-push-url="true">
          <img id="player-artwork" class="player-artwork" alt="" />
          <div class="player-info">
            <span id="player-title" class="player-title"></span>
            <span id="player-podcast" class="player-podcast"></span>
          </div>
        </div>
        <div class="player-controls">
          <button class="player-ctrl-btn" onclick="seekBack(event)" aria-label="Back 15 seconds">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/></svg>
          </button>
          <button id="player-playpause" class="player-ctrl-btn player-ctrl-btn--main" onclick="togglePlayPause(event)" aria-label="Play or pause">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
          </button>
          <button class="player-ctrl-btn" onclick="seekForward(event)" aria-label="Forward 30 seconds">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M13 6v12l8.5-6L13 6zm-.5 6L4 6v12l8.5-6z"/></svg>
          </button>
        </div>
        <div class="player-progress"><div id="player-progress-fill" class="player-progress-fill"></div></div>
        <audio id="player-audio"></audio>
      </div>
      <script src="/static/js/player.js"></script>
      <script dangerouslySetInnerHTML={{__html: `
        function updateNavActive() {
          var path = window.location.pathname;
          document.querySelectorAll('.nav-link[data-path]').forEach(function(el) {
            var p = el.dataset.path;
            var active = p === '/' ? path === '/' : path === p || path.startsWith(p + '/');
            el.classList.toggle('is-active', active);
          });
          document.body.classList.toggle('now-playing-active', path === '/now-playing');
        }
        document.addEventListener('DOMContentLoaded', updateNavActive);
        document.addEventListener('htmx:pushUrl', function() { setTimeout(updateNavActive, 0); });
      `}}/>
    </body>
  </html>
)
