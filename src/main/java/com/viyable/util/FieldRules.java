package com.viyable.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <b>What a typed field may contain — one definition, for the server and the screens.</b>
 *
 * <p><b>⚠⚠ EVERY SCREEN AND EVERY ENDPOINT USES THIS, WITHOUT BEING ASKED</b> (owner, 2026-09-18). A new form validates
 * through it; a new endpoint refuses through it. Relax a rule only when the input genuinely needs it — a field that
 * really does take a 13-digit phone number, say — and then say so where the exception is made, rather than writing a
 * looser copy of the rule beside it.
 *
 * <p>⚠⚠ ITS TWIN IS {@code field-rules.js} — in firmland at {@code static/assets/js/field-rules.js}, and wherever else
 * a browser form applies these rules. THE TWO MUST SAY THE SAME THING, wording included. A form that refuses what the
 * endpoint accepts is a second opinion the person cannot appeal to; a form that accepts what the endpoint refuses turns
 * a typo into a failed save carrying a message from three layers down. When a rule changes here, change it there, and
 * the other way round. firmland's {@code FieldRulesJsTest} RUNS that file and compares its answers with these, case by
 * case — the check that makes the pairing real rather than a promise in a comment.
 *
 * <p><b>⚠ A RULE SPEAKS ONLY ABOUT A VALUE SOMEBODY SUPPLIED.</b> Every method treats null or blank as "nothing to
 * judge" and returns no problem. Whether a field is REQUIRED is the caller's question, asked separately — most of
 * these fields are optional, and a rule that also meant "required" would make them all mandatory by accident.
 *
 * <p>⚠ Each method returns the PROBLEM as a sentence, or null when there is none, so a caller collects them in the
 * order the person reads the form rather than throwing on the first.
 */
public final class FieldRules {

    private FieldRules() {
    }

    /**
     * ⚠ EXACTLY TEN DIGITS, AND NOTHING ELSE (owner, 2026-09-18). It was "7 to 15 digits, brackets and + allowed",
     * which let "+91 99029 00119" and a 15-digit number through and stored the punctuation as typed — so the same
     * number could be held two ways and match neither a search nor another record. All five people in production
     * already have ten bare digits.
     */
    public static final int PHONE_DIGITS = 10;

    /**
     * ⚠ Still not a grammar — an address is proved by SENDING to it — but the characters are pinned: a local part, an
     * at, a domain with a dot and a two-letter-or-longer ending. "Ra@ra.c!@#!@#" used to pass.
     */
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");

    /**
     * <b>⚠⚠ A NAME IS LETTERS, and the punctuation names really carry</b> (owner, 2026-09-18: the form accepted
     * "Upendr a@!@3123123^&%&%&#$%"). Letters in ANY script, spaces, and {@code . ' -} for Dr. Rao, O'Brien and
     * Jean-Luc. Digits and symbols are refused: this is somebody's name, and it is printed on their offer.
     */
    private static final Pattern NAME = Pattern.compile("\\p{L}[\\p{L}\\p{M}\\s.'\\-]*");

    /**
     * Ordinary typed text — a job title, a place. Letters, digits, spaces and the punctuation those carry:
     * {@code Sr. Engineer (Backend)}, {@code Hyderabad - Unit 2}, {@code Sales & Marketing}.
     *
     * <p>⚠ Wider than a name on purpose, and still not "anything": @, #, $, ^, % and the rest belong in no job title
     * and are how a form becomes a place to paste junk.
     */
    private static final Pattern TEXT = Pattern.compile("[\\p{L}\\p{M}\\p{N}\\s.,'&()/\\-]+");

    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");

    public static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** ⚠ {@code what} names the field as the person sees it, so a refusal says which box to look at. */
    public static String nameProblem(String value, String what) {
        if (blank(value)) {
            return null;
        }
        return NAME.matcher(value.trim()).matches() ? null : "That does not look like " + what + ".";
    }

    /**
     * Ordinary text: a designation, a work location, a label.
     *
     * <p>⚠ Every free-text field gets this unless it is genuinely prose (a note). A box that accepts anything is one
     * somebody eventually pastes a stack trace into, and it prints on a document afterwards.
     */
    public static String textProblem(String value, String what) {
        if (blank(value)) {
            return null;
        }
        return TEXT.matcher(value.trim()).matches()
                ? null
                : what + " can use letters, numbers and . , ' & ( ) / - only.";
    }

    public static String emailProblem(String value) {
        if (blank(value)) {
            return null;
        }
        return EMAIL.matcher(value.trim()).matches() ? null : "That email address does not look right.";
    }

    /**
     * ⚠ No stripping of spaces or brackets first: they are not allowed, so accepting them here and cleaning them up
     * silently would store one number in several shapes.
     */
    public static String phoneProblem(String value) {
        if (blank(value)) {
            return null;
        }
        String typed = value.trim();
        if (!DIGITS_ONLY.matcher(typed).matches()) {
            return "A phone number is digits only, with no spaces or symbols.";
        }
        return typed.length() == PHONE_DIGITS ? null : "A phone number is " + PHONE_DIGITS + " digits.";
    }

    /**
     * ⚠ Says the limit AND the length, because "too long" about a 1,000-character note is not actionable.
     *
     * <p>⚠ The sentence after the field's name is word-for-word the one field-rules.js shows under the box — the screen
     * cannot name the field it is already sitting under, and the server must, since its message arrives alone.
     */
    public static String lengthProblem(String value, int max, String what) {
        if (value == null || value.length() <= max) {
            return null;
        }
        return what + " — the limit is " + max + " characters; this is " + value.length() + ".";
    }

    /**
     * A money figure the column can actually hold.
     *
     * <p>⚠⚠ THE PRECISION IS PART OF THE RULE. These land in {@code DECIMAL(15,2)}: more than thirteen digits before
     * the point, or more than two after, is a database error at save time — a 500 that names no field — rather than
     * the sentence below.
     */
    public static String amountProblem(BigDecimal value, String what, int wholeDigits, int decimals) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            return what + " cannot be negative.";
        }
        if (value.scale() > decimals) {
            return what + " has more than " + decimals + " decimal places.";
        }
        if (value.precision() - value.scale() > wholeDigits) {
            return what + " is too large.";
        }
        return null;
    }

    /** The shape every {@code DECIMAL(15,2)} money column in this product takes. */
    public static String moneyProblem(BigDecimal value, String what) {
        return amountProblem(value, what, 13, 2);
    }

    /** ⚠ Adds only the problems that exist, so a caller can pass everything it has and read the list afterwards. */
    public static void collect(List<String> problems, String... found) {
        for (String problem : found) {
            if (problem != null) {
                problems.add(problem);
            }
        }
    }
}
