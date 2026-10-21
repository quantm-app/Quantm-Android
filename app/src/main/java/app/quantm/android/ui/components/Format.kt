package app.quantm.android.ui.components

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** 3:07, 1:02:45 */
fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 2 hr 14 min, 38 min */
fun formatLength(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        minutes > 0 -> "$minutes min"
        else -> "${ms / 1000} sec"
    }
}

/** 1.2M, 34.5K, 812 */
fun formatCount(n: Long?): String {
    val v = n ?: 0
    return when {
        v >= 1_000_000_000 -> trim(v / 1_000_000_000.0) + "B"
        v >= 1_000_000 -> trim(v / 1_000_000.0) + "M"
        v >= 10_000 -> trim(v / 1_000.0, 0) + "K"
        v >= 1_000 -> trim(v / 1_000.0) + "K"
        else -> v.toString()
    }
}

private fun trim(v: Double, decimals: Int = 1): String {
    val s = String.format(Locale.US, "%.${decimals}f", v)
    return if (decimals > 0) s.trimEnd('0').trimEnd('.') else s
}

fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).toInstant()
    } catch (e: DateTimeParseException) {
        // "2024/03/01 12:00:00 +0000" (older api fields)
        runCatching {
            OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z")).toInstant()
        }.getOrNull()
    }
}

/** 5m, 3h, 2d, 6w, 4mo, 2y */
fun timeAgo(value: String?): String {
    val then = parseInstant(value) ?: return ""
    val d = Duration.between(then, Instant.now())
    val minutes = d.toMinutes()
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24 * 7)}w"
        minutes < 60 * 24 * 365 -> "${minutes / (60 * 24 * 30)}mo"
        else -> "${minutes / (60 * 24 * 365)}y"
    }
}

fun formatDate(value: String?): String {
    val then = parseInstant(value) ?: return ""
    return DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()).format(then.atZone(ZoneId.systemDefault()))
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

fun plural(n: Long, one: String, many: String = one + "s") = "${formatCount(n)} ${if (n == 1L) one else many}"
