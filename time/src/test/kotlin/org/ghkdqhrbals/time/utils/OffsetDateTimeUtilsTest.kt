package org.ghkdqhrbals.time.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.LocalDateTime

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

    @Nested
    @DisplayName("thisMonthStartTime 확장 케이스")
    inner class ThisMonthStartTimeExpanded {

        private fun expect(todayUtc: OffsetDateTime, off: String): OffsetDateTime {
            val zone = ZoneOffset.of(off)
            val local = todayUtc.withOffsetSameInstant(zone)
            val startLocal = LocalDateTime.of(local.year, local.month, 1, 0, 0).atOffset(zone)
            return startLocal.withOffsetSameInstant(ZoneOffset.UTC)
        }

        @DisplayName("[2024-03-15T12:34:56Z] KST 월 시작(UTC 변환) 검증")
        @Test
        fun kst_mid_march_2024() {
            val today = OffsetDateTime.of(2024, 3, 15, 12, 34, 56, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
            assertEquals(ZoneOffset.UTC, result.offset)
        }

        @DisplayName("[2024-01-10T00:00:00Z] KST 월 시작(UTC 변환) 검증")
        @Test
        fun kst_january_2024() {
            val today = OffsetDateTime.of(2024, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2023-08-10T05:00:00Z] -03:00 월 시작(UTC 변환) 검증")
        @Test
        fun minus03_august_2023() {
            val today = OffsetDateTime.of(2023, 8, 10, 5, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-03:00")
            assertEquals(expect(today, "-03:00"), result)
        }

        @DisplayName("[2023-12-20T23:59:59Z] UTC(+00:00) 동일 월 시작 시각 검증")
        @Test
        fun utc_zone_same_month_start() {
            val today = OffsetDateTime.of(2023, 12, 20, 23, 59, 59, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+00:00")
            assertEquals(expect(today, "+00:00"), result)
        }

        @DisplayName("[2024-06-18T10:20:30Z] +05:30(인도) 월 시작(UTC 변환) 검증")
        @Test
        fun india_plus_0530_case() {
            val today = OffsetDateTime.of(2024, 6, 18, 10, 20, 30, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+05:30")
            assertEquals(expect(today, "+05:30"), result)
        }

        @DisplayName("[2024-03-02T03:00:00Z] +14:00 최대 오프셋 월 시작 검증")
        @Test
        fun max_offset_plus_14() {
            val today = OffsetDateTime.of(2024, 3, 2, 3, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+14:00")
            assertEquals(expect(today, "+14:00"), result)
        }

        @DisplayName("[2024-11-30T23:00:00Z] -12:00 최소 오프셋 월 시작 검증")
        @Test
        fun min_offset_minus_12() {
            val today = OffsetDateTime.of(2024, 11, 30, 23, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-12:00")
            assertEquals(expect(today, "-12:00"), result)
        }

        @DisplayName("[2024-03-01T00:30:00Z] UTC 자정 직후(-03:00 기준) 월 경계 처리")
        @Test
        fun boundary_utc_just_after_month_change_minus03() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 30, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-03:00")
            assertEquals(expect(today, "-03:00"), result)
        }

        @DisplayName("[2024-02-29T23:00:00Z] 윤년 2월 29일 +02:00 로컬 월 경계 처리")
        @Test
        fun boundary_utc_to_next_local_month_plus02() {
            val today = OffsetDateTime.of(2024, 2, 29, 23, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+02:00")
            assertEquals(expect(today, "+02:00"), result)
        }

        @DisplayName("[2023-02-15T12:00:00Z] 비윤년 2023-02 KST 월 시작 검증")
        @Test
        fun non_leap_feb_2023_plus09() {
            val today = OffsetDateTime.of(2023, 2, 15, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2024-02-29T12:00:00Z] 윤년 2024-02 KST 월 시작 검증")
        @Test
        fun leap_feb_2024_plus09() {
            val today = OffsetDateTime.of(2024, 2, 29, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2023-12-31T23:59:59Z] 연말 12월 KST 월 시작 검증")
        @Test
        fun end_of_year_december_plus09() {
            val today = OffsetDateTime.of(2023, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2024-01-01T00:00:00Z] 연초 1월 UTC 월 시작 검증")
        @Test
        fun start_of_year_jan_plus00() {
            val today = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+00:00")
            assertEquals(expect(today, "+00:00"), result)
        }

        @DisplayName("[2024-04-30T12:00:00Z] 30일 월(4월) -12:00 월 시작 검증")
        @Test
        fun last_day_30_month_april_minus12() {
            val today = OffsetDateTime.of(2024, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-12:00")
            assertEquals(expect(today, "-12:00"), result)
        }

        @DisplayName("[2024-05-31T23:00:00Z] 31일 월(5월) +05:30 월 시작 검증")
        @Test
        fun last_day_31_month_may_plus0530() {
            val today = OffsetDateTime.of(2024, 5, 31, 23, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+05:30")
            assertEquals(expect(today, "+05:30"), result)
        }

        @DisplayName("[2024-07-01T00:00:00Z] 이미 1일인 경우(+09:00) 0시로 리셋")
        @Test
        fun first_day_already_plus09() {
            val today = OffsetDateTime.of(2024, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2024-03-31T15:05:00Z] UTC 3/31 15:05 → KST 월 경계 처리")
        @Test
        fun near_midnight_shift_back_plus09() {
            val today = OffsetDateTime.of(2024, 3, 31, 15, 5, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2024-03-01T02:59:59Z] -03:00 월 경계 처리")
        @Test
        fun near_midnight_shift_forward_minus03() {
            val today = OffsetDateTime.of(2024, 3, 1, 2, 59, 59, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-03:00")
            assertEquals(expect(today, "-03:00"), result)
        }

        @DisplayName("[2024-03-10T08:00:00Z] 분기 Q1(3월) UTC 월 시작 검증")
        @Test
        fun quarter_q1_case_plus00() {
            val today = OffsetDateTime.of(2024, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+00:00")
            assertEquals(expect(today, "+00:00"), result)
        }

        @DisplayName("[2024-04-10T08:00:00Z] 분기 Q2(4월) UTC 월 시작 검증")
        @Test
        fun quarter_q2_case_plus00() {
            val today = OffsetDateTime.of(2024, 4, 10, 8, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+00:00")
            assertEquals(expect(today, "+00:00"), result)
        }

        @DisplayName("[2024-07-10T08:00:00Z] 분기 Q3(7월) UTC 월 시작 검증")
        @Test
        fun quarter_q3_case_plus00() {
            val today = OffsetDateTime.of(2024, 7, 10, 8, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+00:00")
            assertEquals(expect(today, "+00:00"), result)
        }

        @DisplayName("[2024-10-10T08:00:00Z] 분기 Q4(10월) UTC 월 시작 검증")
        @Test
        fun quarter_q4_case_plus00() {
            val today = OffsetDateTime.of(2024, 10, 10, 8, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+00:00")
            assertEquals(expect(today, "+00:00"), result)
        }

        @DisplayName("[2024-08-20T12:34:56Z] 서로 다른 오프셋 결과가 동일하지 않음 검증")
        @Test
        fun different_offsets_same_instant_compare() {
            val today = OffsetDateTime.of(2024, 8, 20, 12, 34, 56, 0, ZoneOffset.UTC)
            val a = thisMonthStartTime(today, "+09:00")
            val b = thisMonthStartTime(today, "-03:00")
            // 서로 다른 로컬 기준이라 값이 다름을 보장 (월 시작 instant가 다를 수 있음)
            assertEquals(a == b, false)
        }

        @DisplayName("[2024-09-09T09:09:09Z] +08:00 월 시작 검증")
        @Test
        fun plus08_case() {
            val today = OffsetDateTime.of(2024, 9, 9, 9, 9, 9, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+08:00")
            assertEquals(expect(today, "+08:00"), result)
        }

        @DisplayName("[2024-09-09T09:09:09Z] -08:00 월 시작 검증")
        @Test
        fun minus08_case() {
            val today = OffsetDateTime.of(2024, 9, 9, 9, 9, 9, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-08:00")
            assertEquals(expect(today, "-08:00"), result)
        }

        @DisplayName("[2024-09-30T23:00:00Z] +01:30 월 시작 검증")
        @Test
        fun plus0130_case() {
            val today = OffsetDateTime.of(2024, 9, 30, 23, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+01:30")
            assertEquals(expect(today, "+01:30"), result)
        }

        @DisplayName("[2024-05-01T00:15:00Z] -03:30 월 시작 검증")
        @Test
        fun minus0330_case() {
            val today = OffsetDateTime.of(2024, 5, 1, 0, 15, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-03:30")
            assertEquals(expect(today, "-03:30"), result)
        }

        @DisplayName("[2024-02-01T00:00:00Z] 윤년 2월 1일 +14:00 월 시작 검증")
        @Test
        fun feb_1st_leap_edge_plus14() {
            val today = OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+14:00")
            assertEquals(expect(today, "+14:00"), result)
        }

        @DisplayName("[2024-01-31T23:59:59Z] 1/31 23:59:59 UTC → -12:00 월 시작 검증")
        @Test
        fun jan_31_to_feb_start_minus12() {
            val today = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-12:00")
            assertEquals(expect(today, "-12:00"), result)
        }

        @DisplayName("[2023-02-28T23:59:59Z] 비윤년 2월 말 UTC 월 시작 검증")
        @Test
        fun end_of_feb_non_leap_plus00() {
            val today = OffsetDateTime.of(2023, 2, 28, 23, 59, 59, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+00:00")
            assertEquals(expect(today, "+00:00"), result)
        }

        @DisplayName("[2025-10-12T13:14:15Z] 임의 날짜1(+09:00) 월 시작 검증")
        @Test
        fun middle_of_month_random1() {
            val today = OffsetDateTime.of(2025, 10, 12, 13, 14, 15, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2025-06-06T06:06:06Z] 임의 날짜2(-05:00) 월 시작 검증")
        @Test
        fun middle_of_month_random2() {
            val today = OffsetDateTime.of(2025, 6, 6, 6, 6, 6, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-05:00")
            assertEquals(expect(today, "-05:00"), result)
        }

        @DisplayName("[2022-01-15T00:00:01Z] 임의 날짜3(+03:00) 월 시작 검증")
        @Test
        fun middle_of_month_random3() {
            val today = OffsetDateTime.of(2022, 1, 15, 0, 0, 1, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+03:00")
            assertEquals(expect(today, "+03:00"), result)
        }

        @DisplayName("[2024-09-01T12:00:00Z] 결과 오프셋이 항상 UTC인지 검증")
        @Test
        fun verify_result_is_utc_offset_for_various() {
            val offsets = listOf("+09:00", "-03:00", "+00:00", "+05:30", "+14:00", "-12:00")
            offsets.forEach { off ->
                val today = OffsetDateTime.of(2024, 9, 1, 12, 0, 0, 0, ZoneOffset.UTC)
                val res = thisMonthStartTime(today, off)
                assertEquals(ZoneOffset.UTC, res.offset)
            }
        }

        @DisplayName("[2024-04-01T18:00:00Z] 로컬 1일이더라도 0시로 리셋(오프셋 유지) 검증")
        @Test
        fun when_today_already_first_day_keep_month_but_reset_time() {
            val today = OffsetDateTime.of(2024, 4, 1, 18, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-08:00")
            assertEquals(expect(today, "-08:00"), result)
        }

        @DisplayName("[2024-05-31T15:00:00Z] UTC 5/31 15:00 → KST 6/1 00:00 경계 처리")
        @Test
        fun cross_month_backwards_plus09() {
            val today = OffsetDateTime.of(2024, 5, 31, 15, 0, 0, 0, ZoneOffset.UTC) // KST로 6/1 00:00
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2024-06-01T02:59:59Z] UTC 6/1 02:59:59 → -03:00 5/31 경계 처리")
        @Test
        fun cross_month_forwards_minus03() {
            val today = OffsetDateTime.of(2024, 6, 1, 2, 59, 59, 0, ZoneOffset.UTC) // -03로 5/31 23:59:59
            val result = thisMonthStartTime(today, "-03:00")
            assertEquals(expect(today, "-03:00"), result)
        }

        @DisplayName("[2024-03-15T00:00:00Z] 고정 예시: 2024-03 KST 월 시작 예상값 검증")
        @Test
        fun verify_constant_expected_example_kst_march_2024() {
            val today = OffsetDateTime.of(2024, 3, 15, 0, 0, 0, 0, ZoneOffset.UTC)
            val expectedUtc = OffsetDateTime.of(2024, 2, 29, 15, 0, 0, 0, ZoneOffset.UTC) // 3/1 00:00+09:00
            assertEquals(expectedUtc, thisMonthStartTime(today, "+09:00"))
        }

        @DisplayName("[2023-08-10T00:00:00Z] 고정 예시: 2023-08 -03:00 월 시작 예상값 검증")
        @Test
        fun verify_constant_expected_example_minus03_august_2023() {
            val today = OffsetDateTime.of(2023, 8, 10, 0, 0, 0, 0, ZoneOffset.UTC)
            val expectedUtc = OffsetDateTime.of(2023, 8, 1, 3, 0, 0, 0, ZoneOffset.UTC) // 8/1 00:00-03:00
            assertEquals(expectedUtc, thisMonthStartTime(today, "-03:00"))
        }

        @DisplayName("[2024-05-20T12:00:00Z] 고정 예시: 2024-05 UTC 월 시작 예상값 검증")
        @Test
        fun verify_constant_expected_example_plus00_may_2024() {
            val today = OffsetDateTime.of(2024, 5, 20, 12, 0, 0, 0, ZoneOffset.UTC)
            val expectedUtc = OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            assertEquals(expectedUtc, thisMonthStartTime(today, "+00:00"))
        }

        @DisplayName("[2024-07-21T12:00:00Z] +05:45(네팔) 월 시작 검증")
        @Test
        fun plus0545_nepal_case() {
            val today = OffsetDateTime.of(2024, 7, 21, 12, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+05:45")
            assertEquals(expect(today, "+05:45"), result)
        }

        @DisplayName("[2024-02-29T10:00:00Z] +12:45(채텀) 월 시작 검증")
        @Test
        fun plus1245_chatham_case() {
            val today = OffsetDateTime.of(2024, 2, 29, 10, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+12:45")
            assertEquals(expect(today, "+12:45"), result)
        }

        @DisplayName("[2024-05-15T09:29:59Z] -09:30(마르케사스) 월 시작 검증")
        @Test
        fun minus0930_marquesas_case() {
            val today = OffsetDateTime.of(2024, 5, 15, 9, 29, 59, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-09:30")
            assertEquals(expect(today, "-09:30"), result)
        }

        @DisplayName("[2024-09-30T16:30:00Z] +08:45(오스트레일리아 유클라) 월 시작 검증")
        @Test
        fun plus0845_eucla_case() {
            val today = OffsetDateTime.of(2024, 9, 30, 16, 30, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+08:45")
            assertEquals(expect(today, "+08:45"), result)
        }

        @DisplayName("[2024-11-01T00:00:00Z] +10:30(로드하우) 월 시작 검증")
        @Test
        fun plus1030_lord_howe_case() {
            val today = OffsetDateTime.of(2024, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+10:30")
            assertEquals(expect(today, "+10:30"), result)
        }

        @DisplayName("[2024-03-10T03:30:00Z] -04:30(베네수엘라) 월 시작 검증")
        @Test
        fun minus0430_venezuela_case() {
            val today = OffsetDateTime.of(2024, 3, 10, 3, 30, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "-04:30")
            assertEquals(expect(today, "-04:30"), result)
        }

        @DisplayName("[2024-12-31T11:00:00Z] +13:00(라인 제도) 월 시작 검증")
        @Test
        fun plus1300_line_islands_case() {
            val today = OffsetDateTime.of(2024, 12, 31, 11, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+13:00")
            assertEquals(expect(today, "+13:00"), result)
        }

        @DisplayName("[2024-04-10T10:00:00Z] +04:30(카불) 월 시작 검증")
        @Test
        fun plus0430_kabul_case() {
            val today = OffsetDateTime.of(2024, 4, 10, 10, 0, 0, 0, ZoneOffset.UTC)
            val result = thisMonthStartTime(today, "+04:30")
            assertEquals(expect(today, "+04:30"), result)
        }

        @DisplayName("[2024-03-01T00:00:00+09:00] today가 KST이고 KST 기준 월 시작(UTC 변환) 경계값")
        @Test
        fun non_utc_today_kst_exact_month_start_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00"))
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2024-02-29T23:59:59+09:00] today가 KST이고 직전 달 말 경계에서 KST 기준 월 시작")
        @Test
        fun non_utc_today_kst_prev_month_end_boundary() {
            val today = OffsetDateTime.of(2024, 2, 29, 23, 59, 59, 0, ZoneOffset.of("+09:00"))
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }

        @DisplayName("[2024-03-01T00:00:00-03:00] today가 -03:00이고 -03:00 기준 월 시작 경계값")
        @Test
        fun non_utc_today_minus03_exact_month_start_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("-03:00"))
            val result = thisMonthStartTime(today, "-03:00")
            assertEquals(expect(today, "-03:00"), result)
        }

        @DisplayName("[2024-02-29T23:59:59-03:00] today가 -03:00이고 직전 달 말 경계에서 -03:00 기준 월 시작")
        @Test
        fun non_utc_today_minus03_prev_month_end_boundary() {
            val today = OffsetDateTime.of(2024, 2, 29, 23, 59, 59, 0, ZoneOffset.of("-03:00"))
            val result = thisMonthStartTime(today, "-03:00")
            assertEquals(expect(today, "-03:00"), result)
        }

        @DisplayName("[2024-03-01T00:00:00+12:45] today가 +12:45(채텀)이고 해당 오프셋 기준 월 시작 경계값")
        @Test
        fun non_utc_today_plus1245_exact_month_start_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("+12:45"))
            val result = thisMonthStartTime(today, "+12:45")
            assertEquals(expect(today, "+12:45"), result)
        }

        @DisplayName("[2024-03-01T00:00:00-09:30] today가 -09:30(마르케사스)이고 해당 오프셋 기준 월 시작 경계값")
        @Test
        fun non_utc_today_minus0930_exact_month_start_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("-09:30"))
            val result = thisMonthStartTime(today, "-09:30")
            assertEquals(expect(today, "-09:30"), result)
        }

        @DisplayName("[2024-03-01T00:00:00+14:00] today가 +14:00이고 해당 오프셋 기준 월 시작 경계값")
        @Test
        fun non_utc_today_plus14_exact_month_start_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("+14:00"))
            val result = thisMonthStartTime(today, "+14:00")
            assertEquals(expect(today, "+14:00"), result)
        }

        @DisplayName("[2024-03-01T00:00:00-12:00] today가 -12:00이고 해당 오프셋 기준 월 시작 경계값")
        @Test
        fun non_utc_today_minus12_exact_month_start_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("-12:00"))
            val result = thisMonthStartTime(today, "-12:00")
            assertEquals(expect(today, "-12:00"), result)
        }

        @DisplayName("[2024-03-01T00:00:00+09:00] today는 KST, 계산 오프셋은 -03:00 (상이한 today 오프셋 경계) ")
        @Test
        fun non_utc_today_kst_with_minus03_calc_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00"))
            val result = thisMonthStartTime(today, "-03:00")
            assertEquals(expect(today, "-03:00"), result)
        }

        @DisplayName("[2024-03-01T00:00:00-03:00] today는 -03:00, 계산 오프셋은 +09:00 (상이한 today 오프셋 경계)")
        @Test
        fun non_utc_today_minus03_with_plus09_calc_boundary() {
            val today = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("-03:00"))
            val result = thisMonthStartTime(today, "+09:00")
            assertEquals(expect(today, "+09:00"), result)
        }
    }
}
