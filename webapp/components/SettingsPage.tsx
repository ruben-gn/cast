import type {FC} from 'hono/jsx'

export const SettingsPage: FC<{ hidePlayed: boolean }> = ({hidePlayed}) => (
    <div class="settings-page">
        <h1 class="settings-title">Settings</h1>
        <form class="settings-form">
            <label class="settings-row">
                <span class="settings-label">Hide played episodes</span>
                <input
                    type="checkbox"
                    name="hidePlayed"
                    checked={hidePlayed}
                    hx-post="/settings"
                    hx-trigger="change"
                    hx-include="closest form"
                    hx-swap="none"
                />
            </label>
        </form>
    </div>
)
