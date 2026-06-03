package cast.android.service

/**
 * Position to seek to *immediately* on media-item transition, from device-local cached progress —
 * a head-start so playback doesn't visibly jump while the authoritative server position is fetched.
 *
 * The server remains the source of truth: the caller MUST still run the WebSocket `get` reconcile,
 * which re-seeks to the server value when it arrives. Returns null when there's nothing useful to
 * seek to (no cached progress, or the episode is already played → start from 0 anyway).
 */
fun localResumePositionMs(cachedProgressMs: Long?, played: Boolean): Long? = when {
    played -> null
    cachedProgressMs == null -> null
    cachedProgressMs <= 0L -> null
    else -> cachedProgressMs
}
