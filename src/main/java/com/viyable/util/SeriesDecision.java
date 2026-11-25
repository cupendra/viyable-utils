package com.viyable.util;

import java.util.List;

/**
 * <b>What to do with a document series when the next number is drawn</b> — the one definition, used by every service
 * that issues GST documents (OMS, accounting). ACCOUNTING_PLAN.md §17.13.7–9.
 *
 * <p>⚠ Pure: it reads and writes nothing. The issuing service locks its own series rows ({@code document_series},
 * {@code acc_document_series}), hands their state in, and carries out the answer inside the same transaction as the
 * document, so a document that rolls back returns its number. The counters stay with the service that issues the
 * document; only this logic is shared (owner, 2026-11-25).
 *
 * <p><b>A series is its prefix.</b> Format and firm code both feed the prefix, so comparing prefixes covers either
 * change: same prefix → the same series continues; a prefix already used this year → that series reopens and continues
 * (never a second series with the same numbers); a new prefix → a new series from 1. The live series is closed whenever
 * another one takes over.
 */
public final class SeriesDecision {

    private SeriesDecision() {
    }

    /** A series row as the issuing service holds it, for one firm, one document type, one financial year. */
    public record Series(long id, String prefix, long lastSerial, long maxSerial) {
        public long left() { return maxSerial - lastSerial; }
    }

    /**
     * The series the firm's current settings call for.
     *
     * @param prefix    rendered by {@link DocumentNumberPattern#prefix}
     * @param maxSerial {@link DocumentNumberPattern#maxSerial}
     */
    public record Wanted(String prefix, long maxSerial) {}

    /** The answer. Exactly one of the permitted subtypes. */
    public sealed interface Outcome permits Continue, Reopen, Open, Refused {}

    /** Draw {@code serial} from the live series {@code seriesId}. */
    public record Continue(long seriesId, long serial) implements Outcome {}

    /** Close {@code closeSeriesId} (when not null) and draw {@code serial} from the closed series {@code seriesId}. */
    public record Reopen(long seriesId, long serial, Long closeSeriesId) implements Outcome {}

    /** Close {@code closeSeriesId} (when not null) and open a series with this prefix, drawing serial 1. */
    public record Open(String prefix, long maxSerial, Long closeSeriesId) implements Outcome {
        public long serial() { return 1; }
    }

    /** Nothing may be issued; {@code sentence} says why and what to do. */
    public record Refused(String sentence) implements Outcome {}

    /**
     * @param live       the live series of this firm, type and year, or null when none is open
     * @param closedThisYear the same firm, type and year's closed series (any order; may be empty)
     * @param wanted     what the firm's settings call for, or <b>null when the settings could not be read</b> — then the
     *                   live series simply continues, and with none live nothing is issued. ⚠ Never a guessed format:
     *                   the caller may pass the format of the firm's most recent series as {@code wanted}, which is its
     *                   own data, but this method invents nothing.
     */
    public static Outcome decide(Series live, List<Series> closedThisYear, Wanted wanted) {
        if (wanted == null) {
            return live == null
                    ? new Refused("The firm's document-number settings could not be read, and this document type has no"
                            + " series open this year to continue. Nothing was issued; it will be tried again.")
                    : next(live, live.id(), null, true);
        }
        if (live != null && live.prefix().equals(wanted.prefix())) {
            return next(live, live.id(), null, true);
        }
        Long close = live == null ? null : live.id();
        for (Series s : closedThisYear == null ? List.<Series>of() : closedThisYear) {
            if (s.prefix().equals(wanted.prefix())) {
                return next(s, s.id(), close, false);
            }
        }
        if (wanted.maxSerial() < 1) {
            return new Refused("Series " + wanted.prefix() + " can hold no numbers; its pattern needs a serial of at"
                    + " least one digit.");
        }
        return new Open(wanted.prefix(), wanted.maxSerial(), close);
    }

    private static Outcome next(Series s, long id, Long close, boolean live) {
        if (s.lastSerial() >= s.maxSerial()) {
            return new Refused(exhausted(s));
        }
        long serial = s.lastSerial() + 1;
        return live ? new Continue(id, serial) : new Reopen(id, serial, close);
    }

    /**
     * ⚠ A series never runs past its width: a number is never cut short or widened (§17.13.6). The fix is a new
     * prefix, which the firm makes by changing its code.
     */
    static String exhausted(Series s) {
        return "Series " + s.prefix() + " has used all " + grouped(s.maxSerial()) + " of its numbers"
                + " for this year. Nothing was issued. Change your code in the document-number settings to start a new"
                + " series.";
    }

    /**
     * <b>Said in words before a series runs out</b>, or null while plenty are left: once only a tenth of its numbers
     * remain (for six digits, from the 900,000th document on).
     */
    public static String runningLow(Series s) {
        if (s == null || s.left() > s.maxSerial() / 10) {
            return null;
        }
        return "Series " + s.prefix() + " has " + grouped(s.left()) + " numbers left this year, of "
                + grouped(s.maxSerial()) + ". When they are used up nothing more can be issued in it;"
                + " changing your code in the document-number settings starts a new series.";
    }

    /** A count with thousands separators ("999,999") for a sentence. A count, not money: MoneyText's rules do not apply. */
    private static String grouped(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }
}
