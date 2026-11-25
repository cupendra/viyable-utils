package com.viyable.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>The grammar of a GST document number — and nothing else.</b> (ACCOUNTING_PLAN.md §17.13.9.)
 *
 * <p>A pattern such as {@code {doc}/{fy4}/{serial6}} says how one document type's numbers are spelled. The patterns
 * themselves, the document codes and the type names are <b>data</b>: firmland holds them (reference tables
 * {@code number_format}, {@code number_format_pattern}, {@code document_type}) and every service reads them from its
 * API. ⚠ <b>This class holds none of them</b> (owner rule 2026-11-25: viyable-utils is never a data store); it only
 * works on what it is handed.
 *
 * <p>Tokens:
 * <ul>
 *   <li>{@code {firm}} the firm's own code (1 to N characters, N decided by the patterns: {@link #roomForFirmCode})</li>
 *   <li>{@code {doc}} the document type's code (CN, DC …)</li>
 *   <li>{@code {fy4}} the financial year written short ({@code 2627}); {@code {fy7}} written long ({@code 2026-27})</li>
 *   <li>{@code {serialN}} the serial, zero-padded to N digits (1–9) — exactly one, and <b>last</b></li>
 * </ul>
 * Between tokens only {@code /} and {@code -} may appear, the two characters GST allows besides letters and digits.
 *
 * <p><b>⚠ THE SERIAL IS ALWAYS LAST.</b> Everything before it is the series' prefix — the identity of a series, stored
 * on the series row ({@code document_series.prefix}) so a document's series is read, never parsed back out of its
 * number. A serial in the middle would make the prefix a lie.
 */
public final class DocumentNumberPattern {

    /** Rule 46(b) and GSTN: a document number is at most 16 characters. Law, not a setting. */
    public static final int GST_MAX_LENGTH = 16;

    /**
     * ⚠ GSTN offline tool's own rule, copied verbatim from the installed tool (2026-11-25,
     * {@code validations.formats.invoiceregex}): 1–16 characters, letters (either case), digits, {@code /} and
     * {@code -}, and at least one letter or non-zero digit. A number this accepts is one the tool imports.
     */
    private static final Pattern GSTN_TOOL =
            Pattern.compile("^(?=.{1,16}$)([/\\-0]*[a-zA-Z0-9/\\-]*[a-zA-Z1-9]+[a-zA-Z0-9/\\-]*)$");

    private static final Pattern TOKEN = Pattern.compile("\\{(firm|doc|fy4|fy7|serial([1-9]))\\}|([/-])");

    /** What one part of a pattern is. */
    public enum Kind { FIRM, DOC, FY4, FY7, SERIAL, SEPARATOR }

    /** One part of a pattern: a token, or a separator ({@code text} holds the character). */
    public record Part(Kind kind, String text) {}

    /** One part of a rendered sample number and what it means, for the legend shown beside a format. */
    public record LegendPart(String text, String meaning) {}

    private final String source;
    private final List<Part> parts;
    private final int serialDigits;

    private DocumentNumberPattern(String source, List<Part> parts, int serialDigits) {
        this.source = source;
        this.parts = parts;
        this.serialDigits = serialDigits;
    }

    /**
     * What is wrong with a pattern, as a sentence, or null when it is usable. Speaks only about the pattern's shape;
     * whether it fits 16 characters depends on the codes, see {@link #roomForFirmCode}.
     */
    public static String problem(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "A number pattern cannot be empty.";
        }
        Matcher m = TOKEN.matcher(pattern);
        int at = 0, serials = 0;
        Kind last = null;
        boolean firm = false, doc = false, fy = false;
        while (m.find()) {
            if (m.start() != at) {
                return "A number pattern holds only {firm}, {doc}, {fy4}, {fy7}, {serialN} and the separators / and -;"
                        + " \"" + pattern.substring(at, m.start()) + "\" in " + pattern + " is none of them.";
            }
            Kind k = kindOf(m);
            if (k == Kind.SERIAL) serials++;
            if ((k == Kind.FIRM && firm) || (k == Kind.DOC && doc) || ((k == Kind.FY4 || k == Kind.FY7) && fy)) {
                return "A number pattern uses each of {firm}, {doc} and the year once; " + pattern + " repeats one.";
            }
            firm |= k == Kind.FIRM;
            doc |= k == Kind.DOC;
            fy |= k == Kind.FY4 || k == Kind.FY7;
            if (k == Kind.SEPARATOR && (last == null || last == Kind.SEPARATOR)) {
                return "A separator in a number pattern stands between two parts; " + pattern + " has one at the start"
                        + " or two together.";
            }
            last = k;
            at = m.end();
        }
        if (at != pattern.length()) {
            return "A number pattern holds only {firm}, {doc}, {fy4}, {fy7}, {serialN} and the separators / and -;"
                    + " \"" + pattern.substring(at) + "\" in " + pattern + " is none of them.";
        }
        if (serials != 1) {
            return "A number pattern has exactly one serial, {serial1} to {serial9}; " + pattern + " has " + serials + ".";
        }
        if (last != Kind.SERIAL) {
            return "The serial comes last in a number pattern, so everything before it names the series; " + pattern
                    + " puts it elsewhere.";
        }
        return null;
    }

    /** Reads a pattern. ⚠ Throws {@link IllegalArgumentException} carrying {@link #problem}'s sentence. */
    public static DocumentNumberPattern parse(String pattern) {
        String problem = problem(pattern);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        List<Part> parts = new ArrayList<>();
        int digits = 0;
        Matcher m = TOKEN.matcher(pattern);
        while (m.find()) {
            Kind k = kindOf(m);
            if (k == Kind.SERIAL) digits = Integer.parseInt(m.group(2));
            parts.add(new Part(k, k == Kind.SEPARATOR ? m.group(3) : m.group()));
        }
        return new DocumentNumberPattern(pattern, Collections.unmodifiableList(parts), digits);
    }

    private static Kind kindOf(Matcher m) {
        if (m.group(3) != null) return Kind.SEPARATOR;
        String t = m.group(1);
        return switch (t) {
            case "firm" -> Kind.FIRM;
            case "doc" -> Kind.DOC;
            case "fy4" -> Kind.FY4;
            case "fy7" -> Kind.FY7;
            default -> Kind.SERIAL;
        };
    }

    public String source() { return source; }

    public List<Part> parts() { return parts; }

    public int serialDigits() { return serialDigits; }

    /** The largest serial a series of this pattern can hold in a year: 999,999 for {@code {serial6}}. */
    public long maxSerial() {
        long max = 1;
        for (int i = 0; i < serialDigits; i++) max *= 10;
        return max - 1;
    }

    public boolean carriesFirmCode() { return has(Kind.FIRM); }

    public boolean carriesDocCode() { return has(Kind.DOC); }

    /**
     * Whether the number names its year. ⚠ When it does not, a series restarts each 1 April under the same prefix, so
     * the same number comes back every year (lawful: Rule 46(b) asks for uniqueness within a financial year).
     */
    public boolean carriesYear() { return has(Kind.FY4) || has(Kind.FY7); }

    private boolean has(Kind kind) {
        return parts.stream().anyMatch(p -> p.kind() == kind);
    }

    /**
     * <b>The most characters a firm's code may have under this pattern</b> so that no number it produces passes
     * {@value #GST_MAX_LENGTH}: 16 less everything else at its longest. {@link Integer#MAX_VALUE} when the pattern
     * carries no firm code. ⚠ The firm-code limit on the settings screen is the SMALLEST of these over every offered
     * pattern — derived from the data, so a firm can never pick a code that some format would overflow.
     * Below 1 when no code fits — for a pattern with no firm code, when the pattern is too long by itself.
     *
     * @param docCode this type's code (data); ignored when the pattern has no {@code {doc}}
     */
    public int roomForFirmCode(String docCode) {
        int room = GST_MAX_LENGTH - lengthWithout(docCode);
        return carriesFirmCode() || room < 0 ? room : Integer.MAX_VALUE;
    }

    /** Whether every number this pattern produces with a code of {@code firmCodeLength} characters fits 16. */
    public boolean fits(int firmCodeLength, String docCode) {
        return longest(firmCodeLength, docCode) <= GST_MAX_LENGTH;
    }

    /** The longest number this pattern produces with a firm code of {@code firmCodeLength} characters. */
    public int longest(int firmCodeLength, String docCode) {
        return lengthWithout(docCode) + (carriesFirmCode() ? firmCodeLength : 0);
    }

    private int lengthWithout(String docCode) {
        int n = 0;
        for (Part p : parts) {
            n += switch (p.kind()) {
                case FIRM -> 0;
                case DOC -> docCode == null ? 0 : docCode.length();
                case FY4 -> 4;
                case FY7 -> 7;
                case SERIAL -> serialDigits;
                case SEPARATOR -> 1;
            };
        }
        return n;
    }

    /**
     * The series' prefix: everything before the serial, e.g. {@code CN/2627/}. This is the identity of a series.
     *
     * @param fyLabel the financial year as {@code 2026-27} ({@link FirmTime#financialYear})
     */
    public String prefix(String firmCode, String docCode, String fyLabel) {
        StringBuilder sb = new StringBuilder();
        for (Part p : parts) {
            switch (p.kind()) {
                case FIRM -> sb.append(required(firmCode, "the firm's code"));
                case DOC -> sb.append(required(docCode, "the document code"));
                case FY4 -> sb.append(FirmTime.shortFinancialYear(fyLabel));
                case FY7 -> {
                    FirmTime.shortFinancialYear(fyLabel); // refuses a label that is not a real yyyy-yy year
                    sb.append(fyLabel);
                }
                case SEPARATOR -> sb.append(p.text());
                case SERIAL -> { }
            }
        }
        return sb.toString();
    }

    /**
     * A document's number. ⚠ Refuses a serial below 1 or above {@link #maxSerial()} rather than writing it wider — a
     * number that does not fit is never cut short or widened (§17.13.6); running out is said in words beforehand
     * ({@link SeriesDecision}).
     */
    public String number(String firmCode, String docCode, String fyLabel, long serial) {
        return prefix(firmCode, docCode, fyLabel) + serialText(serial);
    }

    /** The serial as printed: zero-padded to the pattern's width. */
    public String serialText(long serial) {
        if (serial < 1 || serial > maxSerial()) {
            throw new IllegalArgumentException("Serial " + serial + " is outside 1 to " + maxSerial() + " for "
                    + source + ".");
        }
        return String.format("%0" + serialDigits + "d", serial);
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A number needs " + what + ", and none was given.");
        }
        return value;
    }

    /** Whether GSTN's offline tool accepts this as a document number (its own rule, see {@link #GSTN_TOOL}). */
    public static boolean gstnAccepts(String number) {
        return number != null && GSTN_TOOL.matcher(number).matches();
    }

    /**
     * <b>The legend shown beside a format</b>: a sample number split into its parts, each with what it means. The
     * meaning of each token is grammar and lives here; the type's name is data and is handed in.
     *
     * @param typeName the document type's name as the catalogue holds it ("Credit note")
     * @param sample   the serial to show, e.g. 5
     */
    public List<LegendPart> legend(String firmCode, String docCode, String typeName, String fyLabel, long sample) {
        List<LegendPart> out = new ArrayList<>();
        String serial = serialText(sample);
        for (Part p : parts) {
            switch (p.kind()) {
                case FIRM -> out.add(new LegendPart(firmCode, "your firm's code"));
                case DOC -> out.add(new LegendPart(docCode, "the code for a " + lower(typeName)));
                case FY4 -> out.add(new LegendPart(FirmTime.shortFinancialYear(fyLabel),
                        "the financial year " + fyLabel));
                case FY7 -> out.add(new LegendPart(fyLabel, "the financial year " + fyLabel));
                case SERIAL -> out.add(new LegendPart(serial, serialMeaning(typeName, sample)));
                case SEPARATOR -> { }
            }
        }
        return out;
    }

    private String serialMeaning(String typeName, long sample) {
        String restart = serialText(1);
        String what = "the " + ordinal(sample) + " " + lower(typeName) + " of the year";
        return carriesYear()
                ? what + "; numbering starts again at " + restart + " each 1 April"
                : what + "; numbering starts again at " + restart + " each 1 April, so the same number comes back"
                        + " every year and the document's date tells them apart";
    }

    private static String lower(String name) {
        return name == null || name.isEmpty() ? "document" : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    static String ordinal(long n) {
        long h = n % 100;
        String suffix = h >= 11 && h <= 13 ? "th" : switch ((int) (n % 10)) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    @Override
    public String toString() {
        return source;
    }
}
