package com.moneytracker.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import com.moneytracker.app.i18n.I18n

/** Half-open time interval [start, end) in epoch milliseconds. */
data class TimeRange(val start: Long, val end: Long) {
    operator fun contains(t: Long): Boolean = t >= start && t < end

    companion object {
        val ALL = TimeRange(Long.MIN_VALUE, Long.MAX_VALUE)
    }
}

object Dates {
    fun zone(): ZoneId = ZoneId.systemDefault()

    fun startOfDay(date: LocalDate, zone: ZoneId = zone()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayRange(date: LocalDate, zone: ZoneId = zone()) =
        TimeRange(startOfDay(date, zone), startOfDay(date.plusDays(1), zone))

    fun monthRange(ym: YearMonth, zone: ZoneId = zone()) =
        TimeRange(startOfDay(ym.atDay(1), zone), startOfDay(ym.plusMonths(1).atDay(1), zone))

    fun yearRange(year: Int, zone: ZoneId = zone()) =
        TimeRange(startOfDay(LocalDate.of(year, 1, 1), zone), startOfDay(LocalDate.of(year + 1, 1, 1), zone))

    fun rangeOf(from: LocalDate, toInclusive: LocalDate, zone: ZoneId = zone()) =
        TimeRange(startOfDay(from, zone), startOfDay(toInclusive.plusDays(1), zone))

    fun localDate(ms: Long, zone: ZoneId = zone()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun localDateTime(ms: Long, zone: ZoneId = zone()): LocalDateTime =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    fun toMillis(dateTime: LocalDateTime, zone: ZoneId = zone()): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    fun weekStart(date: LocalDate, first: DayOfWeek = firstDayOfWeek()): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(first))

    // Formatters follow the app language (month and day names). Digits stay Western (DecimalStyle.STANDARD).
    private fun f(en: String, ar: String = en): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (I18n.isRtl) ar else en, I18n.locale)

    val fullDate: DateTimeFormatter get() = f("EEEE, d MMMM yyyy", "EEEE، d MMMM yyyy")
    val mediumDate: DateTimeFormatter get() = f("d MMM yyyy", "d MMMM yyyy")
    val shortDate: DateTimeFormatter get() = f("d MMM", "d MMMM")
    val dayHeader: DateTimeFormatter get() = f("EEE, d MMM", "EEEE، d MMMM")
    val time: DateTimeFormatter get() = f("HH:mm")
    val monthYear: DateTimeFormatter get() = f("MMMM yyyy")
    val shortMonth: DateTimeFormatter get() = f("MMM", "MMMM")
    val shortMonthYear: DateTimeFormatter get() = f("MMM yy", "MMMM yy")
    val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
