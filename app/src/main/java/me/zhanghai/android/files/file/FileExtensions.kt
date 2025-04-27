/**
 * Format file duration (milliseconds) as a human-readable string (MM:SS)
 */
fun Long.format(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
} 