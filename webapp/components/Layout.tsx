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
        <div class="player-info">
          <span class="player-now-playing">Now playing</span>
          <span id="player-title"></span>
        </div>
        <audio id="player-audio" controls></audio>
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
        }
        document.addEventListener('DOMContentLoaded', updateNavActive);
        document.addEventListener('htmx:pushUrl', function() { setTimeout(updateNavActive, 0); });
      `}}/>
    </body>
  </html>
)
