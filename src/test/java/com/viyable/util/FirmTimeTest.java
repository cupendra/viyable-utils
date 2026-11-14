package com.viyable.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FirmTime} at 00:30 IST on 1 April 2026 — 19:00 UTC on 31 March, when the server's day, month and financial
 * year are all still the old ones. Every rule here is one a real bug broke (TIMEZONE_PLAN.md).
 */
class FirmTimeTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Clock HALF_PAST_MIDNIGHT_1_APRIL =
            Clock.fixed(ZonedDateTime.of(2026, 4, 1, 0, 30, 0, 0, INDIA).toInstant(), ZoneOffset.UTC);

    @Test
    void todayAndThisMonthAreTheFirmsNotTheServers() {
        assertEquals(LocalDate.of(2026, 3, 31), LocalDate.now(HALF_PAST_MIDNIGHT_1_APRIL), "the server, on UTC");
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.today(INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
        assertEquals(YearMonth.of(2026, 4), FirmTime.thisMonth(INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
        assertEquals(LocalDate.of(2026, 3, 31), FirmTime.today(ZoneId.of("Asia/Dubai"), HALF_PAST_MIDNIGHT_1_APRIL));
    }

    @Test
    void aMissingOrBadZoneIsAsiaKolkata_neverUtc() {
        assertEquals(INDIA, FirmTime.zoneOrFallback(null));
        assertEquals(INDIA, FirmTime.zoneOrFallback(" "));
        assertEquals(INDIA, FirmTime.zoneOrFallback("IST"), "an abbreviation is not a zone id");
        assertEquals(ZoneId.of("Asia/Dubai"), FirmTime.zoneOrFallback(" Asia/Dubai "));
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.today(null, HALF_PAST_MIDNIGHT_1_APRIL));
    }

    @Test
    void todayIsNotInTheFuture_tomorrowIs() {
        assertFalse(FirmTime.isFuture(LocalDate.of(2026, 4, 1), INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
        assertTrue(FirmTime.isFuture(LocalDate.of(2026, 4, 2), INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
        assertFalse(FirmTime.isFuture(null, INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
    }

    @Test
    void aDocumentIssuedNowIsNoonOnTheFirmsDay_theSameDayReadInUtcOrIndia_inTheNewFinancialYear() {
        Date issued = FirmTime.issueInstant(INDIA, HALF_PAST_MIDNIGHT_1_APRIL);
        assertEquals(LocalDate.of(2026, 4, 1), issued.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.dayOf(issued, INDIA));
        assertEquals("2026-27", FirmTime.financialYear(FirmTime.dayOf(issued, INDIA)));
        assertEquals("2025-26", FirmTime.financialYear(LocalDate.of(2026, 3, 31)));
    }

    @Test
    void theStoredDayFormRoundTrips() {
        Date stored = FirmTime.todayAsStoredDay(INDIA, HALF_PAST_MIDNIGHT_1_APRIL);
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.storedDayOf(stored));
        assertNull(FirmTime.storedDay(null));
    }

    @Test
    void anInstantFallsOnTheFirmsDay_andADaysRangeIsTheFirmsMidnightToMidnight() {
        Date halfPastMidnight = Date.from(ZonedDateTime.of(2026, 4, 1, 0, 30, 0, 0, INDIA).toInstant());
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.dayOf(halfPastMidnight, INDIA));
        LocalDate day = LocalDate.of(2026, 4, 1);
        assertEquals(ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, INDIA).toInstant(), FirmTime.startOf(day, INDIA).toInstant());
        assertEquals(ZonedDateTime.of(2026, 4, 2, 0, 0, 0, 0, INDIA).toInstant(), FirmTime.endOf(day, INDIA).toInstant());
        assertEquals(FirmTime.endOf(day, INDIA).getTime() - 1, FirmTime.lastInstantOf(day, INDIA).getTime());
        assertTrue(!halfPastMidnight.before(FirmTime.startOf(day, INDIA)) && halfPastMidnight.before(FirmTime.endOf(day, INDIA)));
    }

    @Test
    void lastMonthsDocumentIsOutOfThisMonthOnTheFirst() {
        Date the31stOfMarch = Date.from(ZonedDateTime.of(2026, 3, 31, 12, 0, 0, 0, INDIA).toInstant());
        assertFalse(FirmTime.inThisMonth(the31stOfMarch, INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
        assertTrue(FirmTime.inThisMonth(FirmTime.issueInstant(INDIA, HALF_PAST_MIDNIGHT_1_APRIL), INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
        assertFalse(FirmTime.inThisMonth(null, INDIA, HALF_PAST_MIDNIGHT_1_APRIL));
    }

    @Test
    void aStoredDayShowsAsWritten_anInstantShowsAsTheFirmsDay() {
        Date stored = FirmTime.storedDay(LocalDate.of(2026, 4, 1));
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.dayToShow(stored, INDIA));
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.dayToShow(stored, ZoneId.of("America/New_York")), "a day is not shifted west");
        Date halfPastMidnight = Date.from(ZonedDateTime.of(2026, 4, 1, 0, 30, 0, 0, INDIA).toInstant());
        assertEquals(LocalDate.of(2026, 4, 1), FirmTime.dayToShow(halfPastMidnight, INDIA), "an instant is the firm's day");
        assertNull(FirmTime.dayToShow(null, INDIA));
    }

    @Test
    void anInstantCrossesTheWireAsIsoUtc() {
        Date placed = Date.from(ZonedDateTime.of(2026, 11, 13, 22, 16, 5, 0, INDIA).toInstant());
        assertEquals("2026-11-13T16:46:05Z", FirmTime.isoInstant(placed));
        assertNull(FirmTime.isoInstant((Date) null));
    }
}
