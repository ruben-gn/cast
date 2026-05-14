import type {FC} from 'hono/jsx'

export const AddFeedModal: FC = () => (
    <dialog id="add-feed-modal" onclick="if(event.target===this)this.close()" onclose="document.getElementById('rss-url-input').value='';document.getElementById('opml-input').value='';document.getElementById('sub-error').classList.remove('visible')">
        <div class="modal-content">
            <form method="dialog">
                <button class="close-modal" aria-label="Close">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"
                         stroke-linecap="round">
                        <line x1="18" y1="6" x2="6" y2="18"/>
                        <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                </button>
            </form>
            <h3 class="modal-title">Add Podcast</h3>
            <form id="modal-dismiss" method="dialog"></form>
            <form
                class="subscribe-form"
                hx-post="/podcasts"
                hx-target="#content-container"
                hx-swap="outerHTML"
                hx-indicator="#sub-spinner"
                novalidate
                {...{'hx-on::after-request': 'handleSubResult(event)'}}
                {...{'hx-on::before-request': "document.getElementById('sub-error').classList.remove('visible')"}}
            >
                <label class="url-label" for="rss-url-input">RSS Feed URL</label>
                <input
                    type="url"
                    name="url"
                    id="rss-url-input"
                    placeholder="https://example.com/feed.xml"
                    autofocus
                    class="url-input"
                />
                <p class="sub-error" id="sub-error"></p>
                <div class="form-actions">
                    <button type="submit" class="cancel-btn" form="modal-dismiss">Cancel</button>
                    <button type="submit" class="subscribe-btn">Subscribe</button>
                    <span class="htmx-indicator btn-spinner" id="sub-spinner"></span>
                </div>
            </form>
            <div class="modal-divider"><span>or</span></div>
            <form
                class="subscribe-form"
                hx-post="/podcasts/import"
                hx-target="#content-container"
                hx-swap="outerHTML"
                hx-encoding="multipart/form-data"
                hx-indicator="#import-spinner"
                {...{'hx-on::after-request': 'if(event.detail.successful)document.getElementById("add-feed-modal").close()'}}
            >
                <label class="url-label" for="opml-input">OPML File</label>
                <input
                    type="file"
                    name="opml"
                    id="opml-input"
                    accept=".opml,.xml,text/x-opml,application/xml,text/xml"
                    class="opml-input"
                />
                <div class="form-actions">
                    <button type="submit" class="cancel-btn" form="modal-dismiss">Cancel</button>
                    <button type="submit" class="subscribe-btn">Import</button>
                    <span class="htmx-indicator btn-spinner" id="import-spinner"></span>
                </div>
            </form>
        </div>
    </dialog>
)
