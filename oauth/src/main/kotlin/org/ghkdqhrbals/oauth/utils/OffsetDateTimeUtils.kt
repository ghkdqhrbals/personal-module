package com.ghkdqhrbals.mod.utils

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

val DEFAULT_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
const val BASIC_FORMAT = "yyyy-MM-dd HH:mm:ss"
const val DEFAULT_OFFSET: String = "+09:00"

fun maxOffsetDateTime(): OffsetDateTime = OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC)

fun OffsetDateTime.minusMonthsFirstDay(months: Int, offset: String = DEFAULT_OFFSET): OffsetDateTime {
    if (months < 0) {
        throw IllegalArgumentException("months must be positive value")
    }
        return this
            .withOffsetSameInstant(ZoneOffset.of(offset))
            .minusMonths(months.toLong())
            .with(TemporalAdjusters.firstDayOfMonth())
            .with(LocalTime.MIN).withOffsetSameInstant(ZoneOffset.UTC)
    }


fun thisMonthStartTime(today: OffsetDateTime, offset: String = DEFAULT_OFFSET): OffsetDateTime {
    val zone = ZoneOffset.of(offset)
    val kstDateTime = today.withOffsetSameInstant(zone)
    val startOfMonth = kstDateTime.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).withNano(0)
    return startOfMonth.withOffsetSameInstant(ZoneOffset.UTC)
}

fun nextMonthStartTime(today: OffsetDateTime, offset: String = DEFAULT_OFFSET): OffsetDateTime =
    today.with(TemporalAdjusters.firstDayOfNextMonth())
        .withHour(0).withMinute(0).withSecond(0).withNano(0)
        .withOffsetSameLocal(ZoneOffset.of(offset))
        .withOffsetSameInstant(ZoneOffset.UTC)

fun OffsetDateTime.toLocalDateTimeString(offset: String = DEFAULT_OFFSET): String {
    val instant = this.toInstant()
    return instant.atZone(ZoneOffset.of(offset)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

fun OffsetDateTime.toLocalDateAtSameZone(offset: String = DEFAULT_OFFSET): LocalDate {
    val instant = this.toInstant()
    return instant.atZone(ZoneOffset.of(offset)).toLocalDate()
}

fun OffsetDateTime.toDateTimeString(pattern: String = "yyyy-MM-dd HH:mm:ss", zoneId: ZoneId = DEFAULT_ZONE): String {
    val instant = this.toInstant()
    val zonedDateTime = instant.atZone(zoneId)
    return zonedDateTime.format(DateTimeFormatter.ofPattern(pattern))
}

fun OffsetDateTime?.isSameTime(other: OffsetDateTime?, offset: String = DEFAULT_OFFSET): Boolean {
    val zoneId = ZoneId.of(ZoneOffset.of(offset).id)
    return this?.atZoneSameInstant(zoneId) == other?.atZoneSameInstant(zoneId)
}

fun OffsetDateTime.endOfMonth(): OffsetDateTime = this.plusMonths(1).withDayOfMonth(1).minusDays(1)

private val flexibleMilliSecondPatterns = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss.SS",
    "yyyy-MM-dd HH:mm:ss.SSSSSS",

    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss.SS",
    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",

    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSS",
    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS",
    "yyyy-MM-dd'T'HH:mm:ss.SSSSS",
    "yyyy-MM-dd'T'HH:mm:ss.SSSS",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss.S",

    "yyyy-MM-dd HH:mm:ss.SSSSSSSS",
    "yyyy-MM-dd HH:mm:ss.SSSSSSS",
    "yyyy-MM-dd HH:mm:ss.SSSSS",
    "yyyy-MM-dd HH:mm:ss.SSSS",
    "yyyy-MM-dd HH:mm:ss.SSS",
    "yyyy-MM-dd HH:mm:ss.S",

    )

fun String.toOffsetDateTime(localeOffset: String = DEFAULT_OFFSET): OffsetDateTime {
    val offset = ZoneOffset.of(localeOffset)
    var trimString = this.trim().replace("\"", "")

//    trimString = trimString.replace(
//        Regex("""(\.\d{6})\d+(?=[+-Z])"""),
//        "$1"
//    )

    return runCatching {
        OffsetDateTime.parse(trimString, DateTimeFormatter.ISO_DATE_TIME).withOffsetSameInstant(offset)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")).withOffsetSameInstant(offset)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX")).withOffsetSameInstant(offset)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:sssXXX")).withOffsetSameInstant(offset)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")).withOffsetSameInstant(offset)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(trimString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX")).withOffsetSameInstant(offset)
    }.getOrNull() ?: flexibleMilliSecondPatterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            LocalDateTime.parse(trimString, DateTimeFormatter.ofPattern(pattern)).atOffset(offset)
        }.getOrNull()
    } ?: runCatching {
        LocalDate.parse(trimString, DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay(offset).toOffsetDateTime()
    }.getOrNull() ?: throw IllegalArgumentException("Invalid date format: $trimString")
}

fun String.toLocalDateTime(pattern: String = BASIC_FORMAT): LocalDateTime =
    LocalDateTime.parse(this.trim(), DateTimeFormatter.ofPattern(pattern))

fun LocalDateTime.toOffsetDateTime(localOffset: String = DEFAULT_OFFSET): OffsetDateTime {
    val offset = ZoneOffset.of(localOffset)
    return this.atOffset(offset)
}

fun String.toOffsetDateTime(localeOffset: String = DEFAULT_OFFSET, pattern: String): OffsetDateTime {
    val offset = ZoneOffset.of(localeOffset)
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return try {
        OffsetDateTime.parse(this, formatter).toInstant().atZone(offset).toOffsetDateTime()
    } catch (e: DateTimeParseException) {
        LocalDateTime.parse(this, formatter).toInstant(offset).atZone(offset).toOffsetDateTime()
    }
}

fun String.toOffsetDateTimeWithoutTime(localeOffset: String = DEFAULT_OFFSET, pattern: String): OffsetDateTime {
    val offset = ZoneOffset.of(localeOffset)
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return LocalDate.parse(this, formatter).atStartOfDay().atOffset(offset)
}

fun OffsetDateTime.toBirthForSearchFormat(): String {
    val utcDateTime = this.withOffsetSameInstant(ZoneOffset.UTC)
    return utcDateTime.format(DateTimeFormatter.ofPattern("MMdd:HHmm"))
}

fun OffsetDateTime.daysAgoWithStartOfDay(ago: Long, offset: String = DEFAULT_OFFSET): OffsetDateTime =
    this.withOffsetSameInstant(ZoneOffset.of(offset))
        .minusDays(ago)
        .withHour(0).withMinute(0).withSecond(0).withNano(0)

fun OffsetDateTime.endOfDay(offset: String = DEFAULT_OFFSET): OffsetDateTime =
    this.withOffsetSameInstant(ZoneOffset.of(offset))
        .withHour(23).withMinute(59).withSecond(59).withNano(0)

operator fun ClosedRange<LocalDate>.iterator(): Iterator<LocalDate> = object : Iterator<LocalDate> {
    private var current = start

    override fun hasNext(): Boolean = current <= endInclusive

    override fun next(): LocalDate {
        if (!hasNext()) throw NoSuchElementException()
        val next = current
        current = current.plusDays(1)
        return next
    }
}

fun Long.toOffsetDateTime(offset: String = DEFAULT_OFFSET): OffsetDateTime =
    Instant.ofEpochMilli(this).atOffset(ZoneOffset.of(offset))

fun Int.toOffsetDateTime(offset: String = DEFAULT_OFFSET): OffsetDateTime =
    this.toLong().toOffsetDateTime(offset)

fun Long.toOffsetDateTimeWithNano(offset: String = DEFAULT_OFFSET): OffsetDateTime = when {
    this >= 1_000_000_000_000_000_000L -> {
        val seconds = this / 1_000_000_000
        val nanos = (this % 1_000_000_000).toInt()
        Instant.ofEpochSecond(seconds, nanos.toLong()).atOffset(ZoneOffset.of(offset))
    }
    this >= 1_000_000_000_000L -> {
        val seconds = this / 1_000_000
        val nanos = ((this % 1_000_000) * 1_000).toInt()
        Instant.ofEpochSecond(seconds, nanos.toLong()).atOffset(ZoneOffset.of(offset))
    }
    this >= 1_000_000_000L -> {
        Instant.ofEpochMilli(this).atOffset(ZoneOffset.of(offset))
    }
    else -> {
        Instant.ofEpochSecond(this).atOffset(ZoneOffset.of(offset))
    }
}

fun OffsetDateTime.plusBusinessDays(days: Int): OffsetDateTime {
    var result = this
    var added = 0

    while (added < days) {
        result = result.plusDays(1)
        if (result.dayOfWeek != DayOfWeek.SATURDAY && result.dayOfWeek != DayOfWeek.SUNDAY) {
            added++
        }
    }

    return result
}

fun OffsetDateTime.minusBusinessDays(days: Int): OffsetDateTime {
    var result = this
    var subtracted = 0

    while (subtracted < days) {
        result = result.minusDays(1)
        if (result.dayOfWeek != DayOfWeek.SATURDAY && result.dayOfWeek != DayOfWeek.SUNDAY) {
            subtracted++
        }
    }

    return result
}
