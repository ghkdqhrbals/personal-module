package com.ghkdqhrbals.mod.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OffsetDateTimeUtilsTest {

    private val offset = ZoneOffset.of(DEFAULT_OFFSET)
    private val offsetString = DEFAULT_OFFSET

    @Nested
    @DisplayName("minusMonthsFirstDay - Zone 기반 월 계산 후 UTC 반환")
    inner class MinusMonthsFirstDayZoneToUtc {

        @Test
        @DisplayName("KST(+09:00) 기준 2024-03-15 → 1개월 전 첫날(2월 1일 00:00+09:00) → UTC 변환")
        fun kst_to_utc_minus_1_month() {
            val zone = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(1, zone)

            // KST 기준 2024-02-01T00:00+09:00 == 2024-01-31T15:00Z
            val expectedUtc = OffsetDateTime.of(2024, 1, 31, 15, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }

        @Test
        @DisplayName("브라질(-03:00) 기준 2024-03-15 → 1개월 전 첫날(2월 1일 00:00-03:00) → UTC 변환")
        fun brazil_to_utc_minus_1_month() {
            val zone = "-03:00"
            val dateTime = OffsetDateTime.of(2024, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(1, zone)

            // -03:00 기준 2024-02-01T00:00-03:00 == 2024-02-01T03:00Z
            val expectedUtc = OffsetDateTime.of(2024, 2, 1, 3, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }

        @Test
        @DisplayName("윤년(2024) 3월 15일 KST → 1개월 전(2월 1일 KST) → UTC 반환")
        fun leap_year_kst_to_utc() {
            val zone = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 3, 15, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(1, zone)

            // 2024-02-01T00:00+09:00 == 2024-01-31T15:00Z
            val expectedUtc = OffsetDateTime.of(2024, 1, 31, 15, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }

        @Test
        @DisplayName("연도 경계: KST 기준 2024-01-15 → 1개월 전 첫날(2023-12-01T00:00+09:00) → UTC 변환")
        fun year_boundary_kst_to_utc() {
            val zone = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(1, zone)

            // 2023-12-01T00:00+09:00 == 2023-11-30T15:00Z
            val expectedUtc = OffsetDateTime.of(2023, 11, 30, 15, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }

        @Test
        @DisplayName("연도 경계: UTC 기준 2024-01-15 → -03:00 기준 1개월 전 → 2023-12-01T03:00Z")
        fun year_boundary_brazil_to_utc() {
            val zone = "-03:00"
            val dateTime = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(1, zone)

            // 2023-12-01T00:00-03:00 == 2023-12-01T03:00Z
            val expectedUtc = OffsetDateTime.of(2023, 12, 1, 3, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }

        @Test
        @DisplayName("0개월: 브라질(-03:00) 기준 이번 달 1일(00:00-03:00) → UTC 03:00Z")
        fun zero_month_brazil() {
            val zone = "-03:00"
            val dateTime = OffsetDateTime.of(2024, 3, 10, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(0, zone)

            // 2024-03-01T00:00-03:00 == 2024-03-01T03:00Z
            val expectedUtc = OffsetDateTime.of(2024, 3, 1, 3, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }

        @Test
        @DisplayName("25개월 차이: KST 기준 2025-04-15 → 2023-03-01T00:00+09:00 → UTC 변환")
        fun kst_minus_25_months_to_utc() {
            val zone = "+09:00"
            val dateTime = OffsetDateTime.of(2025, 4, 15, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(25, zone)

            // 2023-03-01T00:00+09:00 == 2023-02-28T15:00Z
            val expectedUtc = OffsetDateTime.of(2023, 2, 28, 15, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }

        @Test
        @DisplayName("윤년(2024) 2월 29일 KST 기준 0개월 → 2월 1일(00:00+09:00) → UTC 변환")
        fun leap_year_same_month_kst_to_utc() {
            val zone = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 2, 29, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = dateTime.minusMonthsFirstDay(0, zone)

            // 2024-02-01T00:00+09:00 == 2024-01-31T15:00Z
            val expectedUtc = OffsetDateTime.of(2024, 1, 31, 15, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, result)
        }
    }

    @Nested
    @DisplayName("minusMonthsFirstDay")
    inner class MinusMonthsFirstDay {
        @Test
        @DisplayName("minusMonthsFirstDay more than 12 month should be ok")
        fun first_day_minus_over_year() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 3, 15, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023, 3, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(12, offset)), true)
        }

        @Test
        @DisplayName("윤년(2024) 3월 15일에서 1개월 전으로 → 2024-02-01 (윤년 2월)")
        fun leap_year_minus_1_month() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 3, 15, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(1, offset)), true)
        }

        @Test
        @DisplayName("윤년(2024) 2월 29일에서 1개월 전으로 → 2024-01-01")
        fun leap_year_february_to_january() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 2, 29, 10, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(1, offset)), true)
        }

        @Test
        @DisplayName("비윤년(2023) 3월 15일에서 1개월 전 → 2023-02-01")
        fun non_leap_year_minus_1_month() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2023, 3, 15, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023, 2, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(1, offset)), true)
        }

        @Test
        @DisplayName("비윤년(2023) 3월 31일에서 1개월 전 → 2023-02-01")
        fun non_leap_year_end_of_month() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2023, 3, 31, 23, 59, 59, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023, 2, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(1, offset)), true)
        }

        @Test
        @DisplayName("연도 경계: 2024-01-15 에서 1개월 전 → 2023-12-01")
        fun year_boundary_minus_1_month() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023, 12, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(1, offset)), true)
        }

        @Test
        @DisplayName("연도 경계: 2024-01-01 에서 0개월 전 → 2024-01-01 동일하게")
        fun year_boundary_minus_0_month() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(dateTime.isEqual(dateTime.minusMonthsFirstDay(0, offset)), true)
        }

        @Test
        @DisplayName("offset 변경하면서 테스트")
        fun year_boundary_minus_0_m2onth() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(dateTime.isEqual(dateTime.minusMonthsFirstDay(0, offset)), true)
        }

        @Test
        @DisplayName("연도 경계: 2024-01-31 에서 13개월 전 → 2022-12-01")
        fun year_boundary_minus_13_months() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 1, 31, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2022, 12, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(13, offset)), true)
        }

        @Test
        @DisplayName("offset 변경: +09:00 → +00:00 로 변경 시 UTC 기준 동일 instant 유지")
        fun offset_change_should_keep_instant() {
            val dateTime = OffsetDateTime.of(2023, 10, 5, 9, 0, 0, 0, ZoneOffset.of("+09:00"))
            val result = dateTime.minusMonthsFirstDay(0, "+00:00")
            // +09:00 9시 == UTC 0시
            assertEquals(result.toInstant(), dateTime.withOffsetSameInstant(ZoneOffset.UTC).withDayOfMonth(1).toInstant())
        }

        @Test
        @DisplayName("음수 월 입력 시 IllegalArgumentException 발생")
        fun negative_months_should_throw() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2023, 10, 5, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertThrows(IllegalArgumentException::class.java) {
                dateTime.minusMonthsFirstDay(-1, offset)
            }
        }

        @Test
        @DisplayName("25개월 차이 계산")
        fun first_day_minus_25_months() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2025, 4, 15, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023, 3, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(25, offset)), true)
        }
    }

    @Nested
    @DisplayName("thisMonthStartTime")
    inner class ThisMonthStartTime {
        @Test
        @DisplayName("negative offset (-09:00) should be parsed ok")
        fun parse_negative_offset() {
            val dt = "2023-10-05T14:48:00-09:00".toOffsetDateTime()
            assertEquals(OffsetDateTime.of(2023,10,5,14,48,0,0, ZoneOffset.of("-09:00")).isEqual(dt), true)
        }

        @Test
        @DisplayName("fraction 끝이 0으로 채워진 경우 정상 처리")
        fun parse_fraction_trailing_zero() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,100_000_000, offset)
            assertEquals(expected, "2023-10-05T14:48:00.100000000Z".toOffsetDateTime())
        }

        @Test
        @DisplayName("nano 999,999,999 parsing ok")
        fun parse_fraction_max_nano() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,999_999_999, ZoneOffset.of(offsetString))
            assertEquals(expected, "2023-10-05T14:48:00.999999999Z".toOffsetDateTime(offsetString))
        }
    }

    @Nested
    @DisplayName("success")
    inner class SuccessCases {

        @Test
        fun first_day_of_month() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2023,10,5,14,48,0,0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023,10,1,0,0,0,0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(0, offset)), true)
        }

        @Test
        @DisplayName("if the date is already the first day of the month, it should return the same date")
        fun first_day_of_month_2() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2023,10,1,0,0,0,0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023,10,1,0,0,0,0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(0, offset)), true)
        }

        @Test
        @DisplayName("윤년의 2월 29일 → 해당 달의 첫 날(2월 1일)")
        fun leap_year_february() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 2, 29, 12, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(0, offset)), true)
        }

        @Test
        @DisplayName("윤년 다음 달(3월 1일)은 그대로 유지")
        fun leap_year_next_month() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(0, offset)), true)
        }

        @Test
        fun first_day_of_month_minus_3() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2023,10,5,14,48,0,0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023,7,1,0,0,0,0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(3, offset)), true)
        }

        @Test
        fun first_day_of_month_minus_1() {
            val offset = "+09:00"
            val dateTime = OffsetDateTime.of(2023,10,5,14,48,0,0, ZoneOffset.of(offset))
            val expected = OffsetDateTime.of(2023,9,1,0,0,0,0, ZoneOffset.of(offset))
            assertEquals(expected.isEqual(dateTime.minusMonthsFirstDay(1, offset)), true)
        }

        @Test
        fun parse_z_utc() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,0, offset)
            assertEquals(expected, "2023-10-05T14:48:00Z".toOffsetDateTime())
        }

        @Test
        fun parse_plus_00_offset() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,0, offset)
            assertEquals(expected, "2023-10-05T14:48:00+00:00".toOffsetDateTime())
        }

        @Test
        fun parse_wrapped_in_quotes() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,0, offset)
            assertEquals(expected, "\"2023-10-05T14:48:00Z\"".toOffsetDateTime())
        }

        @Test
        fun parse_local_space_separator() {
            val expected = OffsetDateTime.of(2023,10,5,14,48,0,0, offset)
            assertEquals(expected, "2023-10-05 14:48:00".toOffsetDateTime())
        }

        @Test
        fun parse_local_t_separator() {
            val expected = OffsetDateTime.of(2023,10,5,14,48,0,0, offset)
            assertEquals(expected, "2023-10-05T14:48:00".toOffsetDateTime())
        }

        @Test
        fun parse_fraction_1_z() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,100_000_000, offset)
            assertEquals(expected, "2023-10-05T14:48:00.1Z".toOffsetDateTime())
        }

        @Test
        fun parse_fraction_2_z() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,120_000_000, offset)
            assertEquals(expected, "2023-10-05T14:48:00.12Z".toOffsetDateTime())
        }

        @Test
        fun parse_fraction_3_z() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,123_000_000, offset)
            assertEquals(expected, "2023-10-05T14:48:00.123Z".toOffsetDateTime())
        }

        @Test
        fun parse_fraction_6_z() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,123_456_000, offset)
            assertEquals(expected, "2023-10-05T14:48:00.123456Z".toOffsetDateTime())
        }

        @Test
        fun parse_fraction_9_z() {
            val expected = OffsetDateTime.of(2023,10,5,23,48,0,123_456_789, offset)
            assertEquals(expected, "2023-10-05T14:48:00.123456789Z".toOffsetDateTime())
        }

        @Test
        fun parse_fraction_6_no_offset_t() {
            val expected = OffsetDateTime.of(2023,10,5,14,48,0,123_456_000, offset)
            assertEquals(expected, "2023-10-05T14:48:00.123456".toOffsetDateTime())
        }

        @Test
        fun parse_fraction_6_no_offset_space() {
            val expected = OffsetDateTime.of(2023,10,5,14,48,0,123_456_000, offset)
            assertEquals(expected, "2023-10-05 14:48:00.123456".toOffsetDateTime())
        }

        @Test
        fun parse_date_only() {
            val expected = OffsetDateTime.of(2023,10,5,0,0,0,0, offset)
            assertEquals(expected, "20231005".toOffsetDateTime())
        }
    }

    @Nested
    @DisplayName("fail")
    inner class FailureCases {
        @Test
        fun fail_empty() { assertThrows(IllegalArgumentException::class.java) { "".toOffsetDateTime() } }

        @Test
        fun fail_blank() { assertThrows(IllegalArgumentException::class.java) { " ".toOffsetDateTime() } }

        @Test
        fun fail_alpha() { assertThrows(IllegalArgumentException::class.java) { "abcd".toOffsetDateTime() } }

        @Test
        fun fail_invalid_month() { assertThrows(IllegalArgumentException::class.java) { "2023-13-01T00:00:00Z".toOffsetDateTime() } }

        @Test
        fun fail_invalid_minute_60() { assertThrows(IllegalArgumentException::class.java) { "2023-10-05T14:60:00Z".toOffsetDateTime() } }

        @Test
        fun fail_invalid_second_61() { assertThrows(IllegalArgumentException::class.java) { "2023-10-05T14:48:61Z".toOffsetDateTime() } }

        @Test
        fun fail_short_yyyyMMdd() { assertThrows(IllegalArgumentException::class.java) { "202310".toOffsetDateTime() } }

        @Test
        fun `fail_short_yyyy-MM-dd`(){
            assertThrows(IllegalArgumentException::class.java) { "2023-10-01".toOffsetDateTime() }
        }

        @Test
        fun fail_wrong_separator() { assertThrows(IllegalArgumentException::class.java) { "2023/10/05".toOffsetDateTime() } }

        @Test
        fun fail_too_long_nano() { assertThrows(IllegalArgumentException::class.java) { "2023-10-05T14:48:00.1234567890123Z".toOffsetDateTime() } }

        @Test
        fun fail_zero_month() { assertThrows(IllegalArgumentException::class.java) { "2023-00-10".toOffsetDateTime() } }

        @Test
        fun fail_invalid_day() { assertThrows(IllegalArgumentException::class.java) { "2023-10-32".toOffsetDateTime() } }

        @Test
        fun fail_trailing_junk() { assertThrows(IllegalArgumentException::class.java) { "2023-10-05T14:48:00Zjunk".toOffsetDateTime() } }
    }
}
