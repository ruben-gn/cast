package podcast.adapters.web.view.components

fun formatDuration(duration: String): String {
    val parts = duration.split(":")
    val totalSeconds = when (parts.size) {
        1 -> duration.toDoubleOrNull()?.toLong() ?: return duration
        2 -> {
            val mins = parts[0].toLongOrNull() ?: 0
            val secs = parts[1].toLongOrNull() ?: 0
            mins * 60 + secs
        }
        3 -> {
            val hrs = parts[0].toLongOrNull() ?: 0
            val mins = parts[1].toLongOrNull() ?: 0
            val secs = parts[2].toLongOrNull() ?: 0
            hrs * 3600 + mins * 60 + secs
        }
        else -> return duration
    }
    
    if (totalSeconds == 0L) return "0m"
    
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val remainingSeconds = totalSeconds % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${remainingSeconds}s"
    }
}