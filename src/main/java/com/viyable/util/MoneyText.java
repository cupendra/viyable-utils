package com.viyable.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * How an amount of money is written for a person to read. <b>One definition, shared by every
 * service that prints money.</b>
 *
 * <p><b>Why it lives here and not in an application.</b> firmland had its own copy and OMS had a
 * private {@code withSeparators} of its own — so a screen and the tax document describing the same
 * sale were formatted by two different pieces of code that nobody was keeping in step. That is the
 * same mistake the original had, one level up: before firmland's copy existed, the same three
 * lines had been pasted into five places and had already drifted apart in grouping and in rounding.
 * A shared library is the only arrangement where "the invoice and the screen agree" is structural
 * rather than a thing somebody has to remember.
 *
 * <p><b>⚠ GROUPING FOLLOWS THE CURRENCY, NOT THE READER'S CLOCK.</b> A rupee amount groups by lakh
 * whether it is read in Mumbai, Dubai or London — the convention belongs to the money, not to
 * where the reader happens to be standing. Keying this on an org's TIMEZONE was considered and
 * rejected: {@code Asia/Kolkata} and {@code Asia/Colombo} keep the same clock and different
 * conventions, and an Indian firm that set {@code Asia/Dubai} for a branch's scheduling would have
 * silently reformatted every rupee figure it printed. The clock changed; the money did not.
 *
 * <p><b>The locale is pinned.</b> {@code String.format("%,.2f", v)} and a bare
 * {@code new DecimalFormat("#,##0.00")} both group according to the JVM's default locale, so the
 * same figure prints differently on a differently-configured box — a property nobody notices until
 * an invoice from one machine disagrees with an invoice from another. OMS's formatter had exactly
 * that bug.
 *
 * <p><b>⚠ THE OUTPUT IS FOR READING, NEVER FOR PARSING.</b> It contains separators, so anything
 * reading it back with {@code Double.valueOf} or {@code new BigDecimal(..)} will throw or — far
 * worse — be swallowed by some handler and silently become a different number. Keep the numeric
 * value if a caller needs to compute; use this only on the way to a template or a document.
 */
public final class MoneyText {

    private MoneyText() {}

    /** ISO 4217 code for the Indian rupee — the one currency that does not group by three. */
    public static final String INR = "INR";

    /**
     * ⚠⚠ <b>NO JDK LOCALE PRODUCES LAKH GROUPING, so it is written out here.</b>
     *
     * <p>This was checked rather than assumed, on the JDK these services actually run (17):
     * {@code en-IN}, {@code hi-IN} and {@code ta-IN} all report the pattern {@code #,##0.00} and
     * format 352551.21 as {@code 352,551.21}. Nor can a DecimalFormat pattern express it —
     * {@code #,##,##0.00} looks like it should, and does not, because DecimalFormat carries a
     * SINGLE {@code groupingSize} and simply takes the last group (three). It silently produces
     * Western grouping from an Indian-looking pattern, which is the worst possible failure for
     * something nobody re-reads after it compiles.
     *
     * <p>So the grouping is done by hand — and that is the strongest argument for this class
     * existing at all. Hand-rolled digit grouping copied into two services would not merely
     * duplicate; it would diverge on the first edge case somebody fixed in one of them.
     *
     * @param firstGroup digits in the rightmost group
     * @param thenGroup  digits in every group to its left
     */
    /** Whether a currency groups by lakh. Public so a test can assert the table, not just outputs. */
    public static boolean groupsByLakh(String currencyCode) {
        return INR.equalsIgnoreCase(nullSafe(currencyCode));
    }

    /**
     * {@code 352551.21} in INR → {@code "3,52,551.21"}; in anything else → {@code "352,551.21"}.
     *
     * <p>A null or unknown currency falls back to grouping by three rather than guessing. That is
     * the safe direction: an unfamiliar currency printed the common way is merely conventional,
     * while defaulting to lakh grouping would put an Indian convention on somebody else's money.
     */
    public static String of(Double amount, String currencyCode) {
        return format(amount == null ? 0d : amount, groupsByLakh(currencyCode));
    }

    public static String of(double amount, String currencyCode) {
        return format(amount, groupsByLakh(currencyCode));
    }

    /** {@code "₹3,52,551.21"} — symbol from the org's own setting, never hard-coded by a caller. */
    public static String withSymbol(Double amount, String currencyCode, String symbol) {
        return (symbol == null || symbol.isBlank() ? "" : symbol) + of(amount, currencyCode);
    }

    public static String withSymbol(double amount, String currencyCode, String symbol) {
        return (symbol == null || symbol.isBlank() ? "" : symbol) + of(amount, currencyCode);
    }

    private static String group(double amount, int firstGroup, int thenGroup) {
        // BigDecimal, not String.format: the rounding has to be stated. HALF_UP matches what the
        // money paths already ask for explicitly and what the String.format this replaces did —
        // DecimalFormat's own default is HALF_EVEN, and adopting it would quietly change the last
        // paise of every figure in the application, which is not a formatting change at all.
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        boolean negative = value.signum() < 0;      // -0.00 is signum 0, so it reads as zero
        String plain = value.abs().toPlainString();
        int dot = plain.indexOf('.');
        String digits = plain.substring(0, dot);
        String fraction = plain.substring(dot);

        String grouped;
        if (digits.length() <= firstGroup) {
            grouped = digits;
        } else {
            String tail = digits.substring(digits.length() - firstGroup);
            String head = digits.substring(0, digits.length() - firstGroup);
            // Written plainly on purpose. The first attempt used the four-argument
            // StringBuilder.insert to avoid a substring, and its indices were off by one against
            // the comma it was prepending — producing "3352,551.21", which is the sort of wrong
            // that still looks like a number.
            StringBuilder sb = new StringBuilder(tail);
            int i = head.length();
            while (i > thenGroup) {
                sb.insert(0, head.substring(i - thenGroup, i) + ",");
                i -= thenGroup;
            }
            sb.insert(0, head.substring(0, i) + ",");
            grouped = sb.toString();
        }
        return (negative ? "-" : "") + grouped + fraction;
    }

    private static String format(double amount, boolean indian) {
        return indian ? group(amount, 3, 2) : group(amount, 3, 3);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }
}
