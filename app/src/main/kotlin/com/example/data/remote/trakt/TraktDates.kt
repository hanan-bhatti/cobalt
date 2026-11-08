package com.example.data.remote.trakt

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** ISO timestamps without java.time, which is unavailable on the app's Android 7 minimum SDK. */
internal object TraktDates {
    private val iso = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:?\\d{2})")
    fun parse(value: String?): Long? {
        val match = value?.let(iso::matchEntire) ?: return null
        val fraction = match.groupValues[2].padEnd(3, '0').take(3)
        val zone = match.groupValues[3].let { if (it == "Z") "+0000" else it.replace(":", "") }
        val normalized = "${match.groupValues[1]}.$fraction$zone"
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        return formatter.parse(normalized, position)?.time?.takeIf { position.index == normalized.length }
    }
    fun format(timestamp: Long): String = SimpleDateFormat(
        if (timestamp % 1000 == 0L) "yyyy-MM-dd'T'HH:mm:ss'Z'" else "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(timestamp))

    fun displayDate(value: String?, locale: Locale = Locale.getDefault()): String? {
        val date = value?.take(10) ?: return null
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) return null
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
        val parsed = parser.parse(date, ParsePosition(0)) ?: return null
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsed)
    }
}
