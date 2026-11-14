package com.viyable.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * <b>⚠⚠ THE ONE PLACE A FIRM'S DAY IS WORKED OUT</b> (TIMEZONE_PLAN.md, owner rule 2026-11-14).
 *
 * <p>Every service runs on UTC. Between 00:00 and 05:30 IST the server's "today" is YESTERDAY: invoices were dated a
 * day early (and on 1 April numbered in the closing financial year), the 00:00 IST price job applied every change a
 * day late, night jobs credited and lapsed leave on the wrong day, a pause or payment for "today" was refused as
 * "in the future". So a day that is STORED, PRINTED, or decides MONEY or ELIGIBILITY is the FIRM's day, in the firm's
 * own zone (firmland Org Settings → Regional) — never {@code LocalDate.now()} with no zone.
 *
 * <p>This class is the arithmetic only. Finding a firm's zone differs per service and stays there, each in one helper
 * that delegates here: firmland {@code OrgTimeFormatter}, hr-service {@code OrgTimeFormatter}, OMS {@code FirmDay},
 * ordering {@code FirmDays}, dispatch {@code OrgZoneResolver}. Every method takes a {@link Clock} so a test can stand
 * at 00:30 IST — the window every one of these bugs lives in.
 *
 * <p>⚠ Two different kinds of date, never mixed:
 * <ul>
 *   <li><b>An instant</b> ({@code createdDate}, "placed at") — a moment; shown in a zone, never turned into a day by the
 *       server's zone.</li>
 *   <li><b>A day</b> (invoice date, leave day, due date) — a {@link LocalDate} in the firm's zone. Where an old column
 *       holds a day as a {@code java.util.Date}, it is midnight in the SERVER's zone ({@link #storedDay}) and is read
 *       back the same way ({@link #storedDayOf}); do not "fix" that round trip — it would shift every stored day.</li>
 * </ul>
 */
public final class FirmTime {

    /** The fallback when a firm's zone is unset, unknown or unreadable — never the server's UTC. */
    public static final ZoneId FALLBACK = ZoneId.of(TimeZoneConstants.INDIAN_STANDARD_TIME);

    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private FirmTime() {}

    // ── zones ─────────────────────────────────────────────────────────────────────────────────────────────

    /** A zone from an IANA id ("Asia/Kolkata"), or {@link #FALLBACK} when blank or not a zone. Never throws. */
    public static ZoneId zoneOrFallback(String ianaId) {
        if (ianaId == null || ianaId.isBlank()) return FALLBACK;
        try {
            return ZoneId.of(ianaId.trim());
        } catch (RuntimeException e) {
            return FALLBACK;
        }
    }

    // ── today ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Today as the firm counts it. */
    public static LocalDate today(ZoneId firmZone, Clock clock) {
        return LocalDate.now(clock.withZone(orFallback(firmZone)));
    }

    /** This month as the firm counts it. */
    public static YearMonth thisMonth(ZoneId firmZone, Clock clock) {
        return YearMonth.from(today(firmZone, clock));
    }

    /** Whether {@code day} is after the firm's today ("cannot be in the future" checks). Null is not future. */
    public static boolean isFuture(LocalDate day, ZoneId firmZone, Clock clock) {
        return day != null && day.isAfter(today(firmZone, clock));
    }

    // ── a document dated "now" ────────────────────────────────────────────────────────────────────────────

    /**
     * The date to put on a document issued now: NOON on the firm's today, as an instant. Noon is deliberate — it is the
     * same calendar day read in UTC, in the firm's zone, or in any zone within twelve hours, so the printed date, the
     * financial year in the number and the books' day all agree whichever way a reader converts it.
     */
    public static Date issueInstant(ZoneId firmZone, Clock clock) {
        ZoneId zone = orFallback(firmZone);
        return Date.from(today(zone, clock).atTime(LocalTime.NOON).atZone(zone).toInstant());
    }

    // ── the old Date-column day form ──────────────────────────────────────────────────────────────────────

    /** A day as an old {@code Date} column holds one: midnight in the SERVER's zone (how {@code DateUtils.asDate} writes it). */
    public static Date storedDay(LocalDate day) {
        return day == null ? null : Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** The day an old {@code Date} column holds — read back through the same server-zone conversion it was written with. */
    public static LocalDate storedDayOf(Date storedDay) {
        return storedDay == null ? null : storedDay.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** The firm's today in the old {@code Date}-column form. */
    public static Date todayAsStoredDay(ZoneId firmZone, Clock clock) {
        return storedDay(today(firmZone, clock));
    }

    // ── instants → days, days → ranges ────────────────────────────────────────────────────────────────────

    /** The firm's day an instant fell on (an order placed at 00:30 IST is that IST day, not the UTC day before). */
    public static LocalDate dayOf(Date instant, ZoneId firmZone) {
        return instant == null ? null : instant.toInstant().atZone(orFallback(firmZone)).toLocalDate();
    }

    /** The first instant of {@code day} in the firm's zone — the inclusive lower bound of a day range. */
    public static Date startOf(LocalDate day, ZoneId firmZone) {
        return day == null ? null : Date.from(day.atStartOfDay(orFallback(firmZone)).toInstant());
    }

    /** The first instant AFTER {@code day} in the firm's zone — the exclusive upper bound ({@code < endOf}). */
    public static Date endOf(LocalDate day, ZoneId firmZone) {
        return day == null ? null : Date.from(day.plusDays(1).atStartOfDay(orFallback(firmZone)).toInstant());
    }

    /** The last instant of {@code day} (end exclusive minus 1 ms), for an inclusive {@code BETWEEN}. */
    public static Date lastInstantOf(LocalDate day, ZoneId firmZone) {
        return day == null ? null : new Date(endOf(day, firmZone).getTime() - 1);
    }

    /**
     * The day to SHOW for a {@code java.util.Date} whose kind the screen does not know — an instant ("created at") or an
     * old stored day (midnight in the server zone). A stored day is recognised by being exactly midnight in the server
     * zone and is read back as written ({@link #storedDayOf}), so it never shifts; anything else is an instant and shows
     * as the firm's day ({@link #dayOf}). Only an instant at exactly 00:00:00.000 server time is read the other way, and
     * for a firm east of the server (all of India) both readings give the same day.
     */
    public static LocalDate dayToShow(Date value, ZoneId firmZone) {
        if (value == null) return null;
        java.time.ZonedDateTime server = value.toInstant().atZone(ZoneId.systemDefault());
        return server.toLocalTime().equals(LocalTime.MIDNIGHT) ? server.toLocalDate() : dayOf(value, firmZone);
    }

    /** Whether a document dated {@code documentDate} sits in the firm's current month (the open tax period). */
    public static boolean inThisMonth(Date documentDate, ZoneId firmZone, Clock clock) {
        if (documentDate == null) return false;
        ZoneId zone = orFallback(firmZone);
        return YearMonth.from(documentDate.toInstant().atZone(zone)).equals(thisMonth(zone, clock));
    }

    /** India's financial year of a day, as "2026-27" (April to March). */
    public static String financialYear(LocalDate day) {
        int start = day.getMonthValue() >= 4 ? day.getYear() : day.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }

    // ── instants on the wire (release 2) ──────────────────────────────────────────────────────────────────

    /**
     * An instant as ISO-8601 in UTC ({@code 2026-11-13T16:46:05Z}) — how a time crosses a service boundary or reaches an
     * app. A zone-less "2026-11-13 16:46" is UTC wall time that every reader takes for local time (the shop showed
     * "04:46 pm" for an order placed at 10:16 pm IST).
     */
    public static String isoInstant(Date instant) {
        return instant == null ? null : ISO_INSTANT.format(instant.toInstant());
    }

    public static String isoInstant(Instant instant) {
        return instant == null ? null : ISO_INSTANT.format(instant);
    }

    private static ZoneId orFallback(ZoneId zone) {
        return zone != null ? zone : FALLBACK;
    }
}
