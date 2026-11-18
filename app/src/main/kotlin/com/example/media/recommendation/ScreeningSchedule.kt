package com.example.media.recommendation

import java.time.Instant
import java.time.ZoneId

/** Recompute in local time each day; never add 24h across daylight saving changes. */
object ScreeningSchedule {
    fun next(now: Long, hour: Int, minute: Int, weekends: Boolean, zone: ZoneId = ZoneId.systemDefault(), allowToday: Boolean = true): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        var date = if (allowToday) local.toLocalDate() else local.toLocalDate().plusDays(1)
        repeat(9) {
            val at = date.atTime(hour.coerceIn(5, 11), minute.coerceIn(0, 59)).atZone(zone)
            val allowed = weekends || date.dayOfWeek.value < 6
            if (allowed && ((allowToday && local >= at && local < at.plusHours(3)) || at > local)) return maxOf(now, at.toInstant().toEpochMilli())
            date = date.plusDays(1)
        }
        error("No morning found")
    }
    fun inWindow(now: Long, hour: Int, minute: Int, weekends: Boolean, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val at = local.toLocalDate().atTime(hour.coerceIn(5, 11), minute.coerceIn(0, 59)).atZone(zone)
        return (weekends || local.dayOfWeek.value < 6) && local >= at && local < at.plusHours(3)
    }
    fun day(now: Long, zone: ZoneId = ZoneId.systemDefault()) = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
}
