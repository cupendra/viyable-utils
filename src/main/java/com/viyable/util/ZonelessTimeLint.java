package com.viyable.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <b>⚠⚠ THE RATCHET THAT KEEPS "TODAY" THE FIRM'S</b> (TIMEZONE_PLAN.md, owner rule 2026-11-14) — used by a one-line
 * test in every service: {@code ZonelessTimeLint.assertNoNew(Path.of("src/main/java"), Files.readAllLines(Path.of("src/test/resources/zoneless-time.allowed")))}.
 *
 * <p>Every server runs on UTC, so a {@code LocalDate.now()} with no zone is YESTERDAY in India between 00:00 and
 * 05:30 — the shape of ~75 bugs found on 2026-11-14 (documents dated a day early, prices a day late, leave lapsing
 * late, "today" refused as "in the future"). New code takes the firm's day from the service's own helper, which
 * delegates to {@link FirmTime}; an instant is shown in the firm's zone; a time on the wire is
 * {@link FirmTime#isoInstant}.
 *
 * <p>What this counts, per file of main code: {@code LocalDate.now()}, {@code LocalDateTime.now()},
 * {@code YearMonth.now()}, {@code LocalTime.now()}, {@code ZonedDateTime.now()} — each with NO argument — and
 * {@code ZoneId.systemDefault()}, {@code TimeZone.getDefault()}, {@code Calendar.getInstance()} with no argument.
 * Comment lines are skipped.
 *
 * <p>Occurrences that predate the rule are listed per file with their COUNT (the allowed-list resource). <b>A count
 * may only go down.</b> A file not listed may have none; a listed file may not gain one; and a count that has gone
 * down must be lowered in the list too, so the space it freed cannot be quietly reused. By COUNT, not by file name:
 * a name-only exemption hid six shipped debug dialogs in one file for as long as they had been there.
 *
 * <p>⚠ A use that is genuinely right (a stored-day round trip in an old {@code Date} column) belongs in
 * {@link FirmTime#storedDay}/{@link FirmTime#storedDayOf}, not in a new {@code ZoneId.systemDefault()} beside it.
 */
public final class ZonelessTimeLint {

    static final Pattern ZONELESS = Pattern.compile(
            "\\b(?:LocalDate|LocalDateTime|YearMonth|LocalTime|ZonedDateTime)\\.now\\(\\s*\\)"
                    + "|\\bZoneId\\.systemDefault\\(\\s*\\)"
                    + "|\\bTimeZone\\.getDefault\\(\\s*\\)"
                    + "|\\bCalendar\\.getInstance\\(\\s*\\)");

    private ZonelessTimeLint() {}

    /** Per file (path relative to {@code root}, forward slashes): how many zone-less "now"/server-zone uses it has. */
    public static Map<String, Integer> count(Path root) {
        Map<String, Integer> counts = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                int n = 0;
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
                    Matcher m = ZONELESS.matcher(line);
                    while (m.find()) n++;
                }
                if (n > 0) counts.put(root.relativize(f).toString().replace('\\', '/'), n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return counts;
    }

    /** The allowed list: one {@code relative/Path.java=N} per line; blank lines and {@code #} comments ignored. */
    public static Map<String, Integer> parseAllowed(List<String> lines) {
        Map<String, Integer> allowed = new TreeMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.lastIndexOf('=');
            allowed.put(line.substring(0, eq).trim(), Integer.parseInt(line.substring(eq + 1).trim()));
        }
        return allowed;
    }

    /** Every way {@code found} breaks the ratchet against {@code allowed}, as sentences. Empty = clean. */
    public static List<String> problems(Map<String, Integer> found, Map<String, Integer> allowed) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> e : found.entrySet()) {
            Integer max = allowed.get(e.getKey());
            if (max == null) {
                problems.add(e.getKey() + " uses the server's clock or zone " + e.getValue() + " time(s). Take the firm's"
                        + " day from this service's helper (it delegates to com.viyable.util.FirmTime) - see TIMEZONE_PLAN.md.");
            } else if (e.getValue() > max) {
                problems.add(e.getKey() + " now has " + e.getValue() + " zone-less uses; it was allowed " + max
                        + ". The count may only go down - use the firm's day for the new one.");
            } else if (e.getValue() < max) {
                problems.add(e.getKey() + " is down to " + e.getValue() + " from " + max
                        + " - lower it in the allowed list too, so the freed space cannot be reused.");
            }
        }
        for (Map.Entry<String, Integer> e : allowed.entrySet()) {
            if (!found.containsKey(e.getKey())) {
                problems.add(e.getKey() + " has none left (or is gone) - remove it from the allowed list.");
            }
        }
        return problems;
    }

    /** The one-line test body: fails with every problem, each on its own line. */
    public static void assertNoNew(Path mainJavaRoot, List<String> allowedLines) {
        List<String> problems = problems(count(mainJavaRoot), parseAllowed(allowedLines));
        if (!problems.isEmpty()) {
            throw new AssertionError("Zone-less time (TIMEZONE_PLAN.md):\n  " + String.join("\n  ", problems));
        }
    }

    /** Writes the current counts as an allowed list — for creating one, never for silencing a failure. */
    public static String render(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }
}
