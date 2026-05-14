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
          <span
            class="app-logo"
            hx-get="/podcasts"
            hx-target="#content-container"
            hx-swap="outerHTML"
            hx-push-url="true"
            hx-indicator="#nav-spinner"
            style="cursor:pointer"
          >Cast</span>
          <div
            class="header-queue-btn"
            hx-get="/queue"
            hx-target="#content-container"
            hx-swap="outerHTML"
            hx-push-url="true"
            hx-indicator="#nav-spinner"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
              <line x1="8" y1="6" x2="21" y2="6"/>
              <line x1="8" y1="12" x2="21" y2="12"/>
              <line x1="8" y1="18" x2="21" y2="18"/>
              <line x1="3" y1="6" x2="3.01" y2="6"/>
              <line x1="3" y1="12" x2="3.01" y2="12"/>
              <line x1="3" y1="18" x2="3.01" y2="18"/>
            </svg>
            Queue
            <span id="queue-badge" class="queue-badge"></span>
          </div>
        </div>
        <button class="header-add-btn" onclick="document.getElementById('add-feed-modal').showModal()">
          ＋ Add podcast
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
    </body>
  </html>
)
